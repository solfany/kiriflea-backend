package com.nplohs.market.auth.service;

import com.nplohs.market.auth.entity.EmailVerificationCode;
import com.nplohs.market.auth.repository.EmailVerificationCodeRepository;
import com.nplohs.market.common.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailVerificationCodeRepository codeRepository;
    private final com.nplohs.market.user.repository.UserRepository userRepository;
    private final RedisTemplate<String, Object> redis;
    private final RateLimiter rateLimiter;

    // 코드 유효시간(기본 10분) 동안 검증 시도를 제한해, 6자리 코드를 전수조사(최대 100만 가지)로
    // 뚫는 걸 막는다. 초과하면 코드를 다시 요청해야 한다.
    private static final int VERIFY_MAX_ATTEMPTS = 5;
    // 같은 이메일로 코드 발송을 반복 요청해 메일함을 스팸으로 채우는 것도 제한한다.
    private static final int SEND_MAX_PER_WINDOW = 3;
    private static final Duration SEND_WINDOW = Duration.ofMinutes(5);

    @Value("${nplohs.email.code-expiry-minutes:10}")
    private int expiryMinutes;

    @Value("${spring.mail.username:no-reply@nplohs.com}")
    private String fromAddress;

    private static final SecureRandom RANDOM = new SecureRandom();
    static final String EMAIL_VERIFIED_PREFIX = "email_verified:";

    @Transactional
    public void sendVerificationCode(String email) {
        String lowerEmail = email.toLowerCase();

        java.util.Optional<com.nplohs.market.user.entity.User> existing = userRepository.findByEmail(lowerEmail);
        if (existing.isPresent()) {
            if (!existing.get().isActive()) {
                throw new IllegalStateException("탈퇴한 회원입니다. 24시간 뒤에 회원가입 가능합니다.");
            }
            throw new IllegalStateException("이미 가입된 이메일입니다.");
        }
        if (!rateLimiter.tryAcquire("email-send:" + lowerEmail, SEND_MAX_PER_WINDOW, SEND_WINDOW)) {
            throw new IllegalStateException("인증 코드 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }

        codeRepository.deleteByEmail(lowerEmail);
        rateLimiter.reset("email-verify:" + lowerEmail); // 새 코드를 받았으니 시도 횟수도 초기화

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codeRepository.save(new EmailVerificationCode(lowerEmail, code, expiryMinutes));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("[너굴상점] 이메일 인증 코드");
        message.setText(String.format("인증 코드: %s%n%n유효 시간: %d분", code, expiryMinutes));
        mailSender.send(message);
        log.info("Verification code sent to {}: {}", email, code);
    }

    @Transactional
    public void sendPasswordResetCode(String email) {
        String lowerEmail = email.toLowerCase();

        if (!userRepository.existsByEmail(lowerEmail)) {
            throw new IllegalStateException("가입되지 않은 이메일입니다.");
        }
        if (!rateLimiter.tryAcquire("email-send:" + lowerEmail, SEND_MAX_PER_WINDOW, SEND_WINDOW)) {
            throw new IllegalStateException("인증 코드 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }

        codeRepository.deleteByEmail(lowerEmail);
        rateLimiter.reset("email-verify:" + lowerEmail); // 새 코드를 받았으니 시도 횟수도 초기화

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codeRepository.save(new EmailVerificationCode(lowerEmail, code, expiryMinutes));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("[너굴상점] 비밀번호 재설정 인증 코드");
        message.setText(String.format("비밀번호 재설정 인증 코드: %s%n%n유효 시간: %d분", code, expiryMinutes));
        mailSender.send(message);
        log.info("Password reset code sent to {}: {}", email, code);
    }

    @Transactional
    public void verifyCode(String email, String code) {
        String lowerEmail = email.toLowerCase();

        if (!rateLimiter.tryAcquire("email-verify:" + lowerEmail, VERIFY_MAX_ATTEMPTS, Duration.ofMinutes(expiryMinutes))) {
            throw new IllegalArgumentException("인증 시도 횟수를 초과했습니다. 코드를 다시 요청해주세요.");
        }

        EmailVerificationCode record = codeRepository
                .findTopByEmailAndUsedFalseOrderByIdDesc(lowerEmail)
                .orElseThrow(() -> new IllegalArgumentException("인증 코드가 없습니다."));

        if (record.isExpired()) throw new IllegalArgumentException("인증 코드가 만료되었습니다.");
        if (!record.getCode().equals(code)) throw new IllegalArgumentException("인증 코드가 올바르지 않습니다.");

        record.markUsed();
        // 인증 완료 플래그를 Redis에 저장 (30분 유효)
        redis.opsForValue().set(EMAIL_VERIFIED_PREFIX + lowerEmail, "1", 30, TimeUnit.MINUTES);
    }

    public boolean isEmailVerified(String email) {
        return Boolean.TRUE.equals(redis.hasKey(EMAIL_VERIFIED_PREFIX + email.toLowerCase()));
    }

    public void clearEmailVerified(String email) {
        redis.delete(EMAIL_VERIFIED_PREFIX + email.toLowerCase());
    }

    public void sendDeveloperContactEmail(String senderEmail, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo("solfany@krtranslink.com");
        message.setSubject("[너굴상점] 사용자 개발자 문의 접수");
        
        String mailBody = String.format("문의 작성자 이메일: %s\n\n[문의 내용]\n%s", senderEmail, content);
        message.setText(mailBody);
        
        mailSender.send(message);
        log.info("Developer contact email sent from {}", senderEmail);
    }
}
