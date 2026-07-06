package com.nplohs.market.auth.service;

import com.nplohs.market.auth.entity.EmailVerificationCode;
import com.nplohs.market.auth.repository.EmailVerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailVerificationCodeRepository codeRepository;
    private final com.nplohs.market.auth.repository.UserRepository userRepository;
    private final RedisTemplate<String, Object> redis;

    @Value("${nplohs.email.code-expiry-minutes:10}")
    private int expiryMinutes;

    @Value("${spring.mail.username:no-reply@nplohs.com}")
    private String fromAddress;

    private static final SecureRandom RANDOM = new SecureRandom();
    static final String EMAIL_VERIFIED_PREFIX = "email_verified:";

    @Transactional
    public void sendVerificationCode(String email) {
        String lowerEmail = email.toLowerCase();
        
        if (userRepository.existsByEmail(lowerEmail)) {
            throw new IllegalStateException("이미 가입된 이메일입니다.");
        }

        codeRepository.deleteByEmail(lowerEmail);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codeRepository.save(new EmailVerificationCode(lowerEmail, code, expiryMinutes));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("[nplohs 마켓] 이메일 인증 코드");
        message.setText(String.format("인증 코드: %s%n%n유효 시간: %d분", code, expiryMinutes));
        mailSender.send(message);
        log.info("Verification code sent to {}: {}", email, code);
    }

    @Transactional
    public void verifyCode(String email, String code) {
        String lowerEmail = email.toLowerCase();
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
        message.setSubject("[끼리플리] 사용자 개발자 문의 접수");
        
        String mailBody = String.format("문의 작성자 이메일: %s\n\n[문의 내용]\n%s", senderEmail, content);
        message.setText(mailBody);
        
        mailSender.send(message);
        log.info("Developer contact email sent from {}", senderEmail);
    }
}
