package com.nplohs.market.auth.service;

import com.nplohs.market.auth.entity.EmailVerificationCode;
import com.nplohs.market.auth.repository.EmailVerificationCodeRepository;
import com.nplohs.market.common.ratelimit.RateLimiter;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
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

    private static final int VERIFY_MAX_ATTEMPTS = 5;
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
        rateLimiter.reset("email-verify:" + lowerEmail);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codeRepository.save(new EmailVerificationCode(lowerEmail, code, expiryMinutes));

        sendHtmlEmailWithBanner(
                email,
                "[너굴상점] 이메일 인증 코드",
                "이메일 인증 안내",
                code,
                "너굴상점 가입을 환영합니다!<br/>아래 6자리 인증 코드를 입력하여 회원가입을 완료해주세요.");
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
        rateLimiter.reset("email-verify:" + lowerEmail);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codeRepository.save(new EmailVerificationCode(lowerEmail, code, expiryMinutes));

        sendHtmlEmailWithBanner(
                email,
                "[너굴상점] 비밀번호 재설정 인증 코드",
                "비밀번호 재설정 안내 🔒",
                code,
                "비밀번호 재설정을 위한 인증 코드입니다.<br/>본인이 요청하지 않은 경우 이 메일을 무시하셔도 됩니다.");
        log.info("Password reset code sent to {}: {}", email, code);
    }

    private void sendHtmlEmailWithBanner(String toEmail, String subject, String title, String codeText,
            String descText) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);

            String htmlContent = String.format(
                    """
                            <!DOCTYPE html>
                            <html>
                            <head>
                              <meta charset="utf-8">
                            </head>
                            <body style="margin:0; padding:20px; background-color:#f4f6f8; font-family:'Apple SD Gothic Neo', -apple-system, sans-serif;">
                              <div style="max-width:560px; margin:0 auto; background:#ffffff; border-radius:20px; overflow:hidden; box-shadow:0 4px 20px rgba(0,0,0,0.08); border:1px solid #e5e7eb;">
                                <div style="width:100%%; text-align:center; background:#f9fafb;">
                                  <img src="cid:bannerImage" style="width:100%%; max-width:560px; height:auto; display:block;" alt="너굴상점 배너" />
                                </div>
                                <div style="padding:32px 28px; text-align:center;">
                                  <h2 style="font-size:22px; font-weight:800; color:#111827; margin:0 0 12px 0;">%s</h2>
                                  <p style="font-size:14px; color:#4b5563; margin:0 0 24px 0; line-height:1.6;">%s</p>

                                  <div style="background:#E8F5E9; border:1.5px solid #C8E6C9; border-radius:14px; padding:20px; margin-bottom:24px;">
                                    <span style="font-size:12px; font-weight:700; color:#4CAF50; letter-spacing:0.05em; text-transform:uppercase; display:block; margin-bottom:6px;">인증 코드</span>
                                    <span style="font-size:32px; font-weight:900; color:#2E7D32; letter-spacing:0.2em; font-family:monospace;">%s</span>
                                  </div>

                                  <p style="font-size:12px; color:#9ca3af; margin:0;">본 인증 코드는 %d분간 유효합니다.</p>
                                </div>
                                <div style="background:#f9fafb; padding:16px 28px; border-top:1px solid #f3f4f6; text-align:center;">
                                  <span style="font-size:11px; color:#9ca3af;">© 너굴상점 Nook Market. All rights reserved.</span>
                                </div>
                              </div>
                            </body>
                            </html>
                            """,
                    title, descText, codeText, expiryMinutes);

            helper.setText(htmlContent, true);

            File bannerFile = new File(
                    "/Users/solfany/Project/kiriflea-market/kiriflea-front/public/images/mail/verifyCode1.jpeg");
            if (!bannerFile.exists()) {
                bannerFile = new File(
                        "/Users/solfany/Project/kiriflea-market/kiriflea-backend/src/main/resources/static/images/mail/verifyCode1.jpeg");
            }

            if (bannerFile.exists()) {
                helper.addInline("bannerImage", new FileSystemResource(bannerFile));
            } else {
                ClassPathResource classPathRes = new ClassPathResource("static/images/mail/verifyCode1.jpeg");
                if (classPathRes.exists()) {
                    helper.addInline("bannerImage", classPathRes);
                }
            }

            mailSender.send(mimeMessage);
        } catch (Exception e) {
            log.error("Failed to send HTML email with banner, falling back to simple text mail", e);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(String.format("%s: %s\n\n유효 시간: %d분", title, codeText, expiryMinutes));
            mailSender.send(message);
        }
    }

    @Transactional
    public void verifyCode(String email, String code) {
        String lowerEmail = email.toLowerCase();

        if (!rateLimiter.tryAcquire("email-verify:" + lowerEmail, VERIFY_MAX_ATTEMPTS,
                Duration.ofMinutes(expiryMinutes))) {
            throw new IllegalArgumentException("인증 시도 횟수를 초과했습니다. 코드를 다시 요청해주세요.");
        }

        EmailVerificationCode record = codeRepository
                .findTopByEmailAndUsedFalseOrderByIdDesc(lowerEmail)
                .orElseThrow(() -> new IllegalArgumentException("인증 코드가 없습니다."));

        if (record.isExpired())
            throw new IllegalArgumentException("인증 코드가 만료되었습니다.");
        if (!record.getCode().equals(code))
            throw new IllegalArgumentException("인증 코드가 올바르지 않습니다.");

        record.markUsed();
        redis.opsForValue().set(EMAIL_VERIFIED_PREFIX + lowerEmail, "1", 30, TimeUnit.MINUTES);
    }

    public boolean isEmailVerified(String email) {
        return Boolean.TRUE.equals(redis.hasKey(EMAIL_VERIFIED_PREFIX + email.toLowerCase()));
    }

    public void clearEmailVerified(String email) {
        redis.delete(EMAIL_VERIFIED_PREFIX + email.toLowerCase());
    }

    public void sendDeveloperContactEmail(String senderEmail, String content) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo("solfany@krtranslink.com");
            helper.setSubject("[너굴상점] 사용자 개발자 문의 접수");

            String htmlContent = String.format(
                    """
                            <!DOCTYPE html>
                            <html>
                            <head><meta charset="utf-8"></head>
                            <body style="margin:0; padding:20px; background-color:#f4f6f8; font-family:'Apple SD Gothic Neo', sans-serif;">
                              <div style="max-width:560px; margin:0 auto; background:#ffffff; border-radius:20px; overflow:hidden; box-shadow:0 4px 20px rgba(0,0,0,0.08); border:1px solid #e5e7eb;">
                                <div style="width:100%%; text-align:center; background:#f9fafb;">
                                  <img src="cid:bannerImage" style="width:100%%; max-width:560px; height:auto; display:block;" alt="너굴상점 배너" />
                                </div>
                                <div style="padding:28px;">
                                  <h3 style="font-size:18px; font-weight:800; color:#111827; margin:0 0 16px 0;">개발자 문의 접수</h3>
                                  <p style="font-size:13px; color:#6b7280; margin-bottom:12px;"><strong>작성자 이메일:</strong> %s</p>
                                  <div style="background:#f9fafb; border:1px solid #e5e7eb; border-radius:12px; padding:16px; font-size:14px; color:#374151; white-space:pre-wrap; line-height:1.6;">
                                    %s
                                  </div>
                                </div>
                              </div>
                            </body>
                            </html>
                            """,
                    senderEmail, content);

            helper.setText(htmlContent, true);

            File bannerFile = new File(
                    "/Users/solfany/Project/kiriflea-market/kiriflea-front/public/images/mail/verifyCode1.jpeg");
            if (bannerFile.exists()) {
                helper.addInline("bannerImage", new FileSystemResource(bannerFile));
            }
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo("solfany@krtranslink.com");
            message.setSubject("[너굴상점] 사용자 개발자 문의 접수");
            message.setText(String.format("문의 작성자 이메일: %s\n\n[문의 내용]\n%s", senderEmail, content));
            mailSender.send(message);
        }
        log.info("Developer contact email sent from {}", senderEmail);
    }
}
