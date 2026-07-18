package com.forexbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails (password reset, welcome, etc.).
 * In dev — if MAIL_USERNAME is not configured, the reset link is logged
 * to the console instead of being emailed so you can still test the flow.
 */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPasswordReset(String toEmail, String token) {
        String resetLink = baseUrl + "/reset-password?token=" + token;

        if (fromAddress == null || fromAddress.isBlank()) {
            // No mail configured — log for local dev testing
            log.warn("═══════════════════════════════════════════════════════════");
            log.warn("  PASSWORD RESET LINK (no SMTP configured — dev mode)");
            log.warn("  To      : {}", toEmail);
            log.warn("  Link    : {}", resetLink);
            log.warn("  Expires : 24 hours");
            log.warn("═══════════════════════════════════════════════════════════");
            return;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom("Harvest Technologies <" + fromAddress + ">");
            msg.setTo(toEmail);
            msg.setSubject("Reset your Harvest Technologies password");
            msg.setText("""
                    Hi,

                    You requested a password reset for your Harvest Technologies account.

                    Click the link below to set a new password (valid for 24 hours):

                    %s

                    If you didn't request this, you can safely ignore this email.

                    — The Harvest Technologies Team
                    """.formatted(resetLink));
            mailSender.send(msg);
            log.info("Password reset email sent to {}", toEmail);
        } catch (MailException e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }
}
