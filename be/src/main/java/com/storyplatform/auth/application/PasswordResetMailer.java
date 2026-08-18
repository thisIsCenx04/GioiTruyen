package com.storyplatform.auth.application;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Delivers the password reset link.
 *
 * <p>Whether SMTP is usable is decided by {@code spring.mail.host} being
 * non-blank, not by the sender bean existing. Spring Boot builds a
 * {@link JavaMailSender} whenever that property is *defined*, and it is always
 * defined here - as an empty string when MAIL_HOST is unset - so a bean check
 * alone would send every deployment down a doomed connection attempt instead of
 * the intended fallback. When there is no host the link goes to the log for an
 * operator to pass on by hand, and the application still starts.
 *
 * <p>The token never travels back in the HTTP response. Whoever asked for the
 * reset must prove they can read the mailbox, which is the entire point of the
 * flow; returning it would let anyone reset any account they can name.
 */
@Component
public class PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailer.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String linkBase;
    private final String fromAddress;
    private final boolean smtpConfigured;

    public PasswordResetMailer(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${app.identity.password-reset.link-base}") String linkBase,
            @Value("${app.identity.password-reset.from-address}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.smtpConfigured = mailHost != null && !mailHost.isBlank();
        this.linkBase = linkBase.replaceAll("/+$", "");
        this.fromAddress = fromAddress;
    }

    /**
     * Sends the link, or logs it when no mail sender is configured.
     *
     * <p>A delivery failure is swallowed on purpose. The caller answers 204
     * whether or not the address has an account - reporting a bounce here would
     * tell a stranger which emails are registered, which is exactly what that
     * uniform answer exists to hide.
     */
    public void send(String email, String token) {
        String link = linkBase + "/auth/reset-password?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);

        JavaMailSender sender = smtpConfigured ? mailSender.getIfAvailable() : null;
        if (sender == null) {
            log.warn("Chua cau hinh SMTP (spring.mail.host). Link dat lai mat khau cho {}: {}",
                    email, link);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Đặt lại mật khẩu Giới Truyện");
        message.setText("""
                Xin chào,

                Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản Giới Truyện.
                Nhấn vào liên kết dưới đây để đặt mật khẩu mới:

                %s

                Liên kết có hiệu lực trong 1 giờ và chỉ dùng được một lần.
                Nếu bạn không yêu cầu điều này, hãy bỏ qua email - mật khẩu
                hiện tại của bạn vẫn giữ nguyên.

                Giới Truyện
                """.formatted(link));

        try {
            sender.send(message);
        } catch (MailException exception) {
            log.error("Khong gui duoc email dat lai mat khau toi {}", email, exception);
        }
    }
}
