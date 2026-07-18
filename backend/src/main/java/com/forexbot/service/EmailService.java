package com.forexbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Transactional email service — powered by Brevo SMTP relay in UAT/prod.
 *
 * Dev fallback: if MAIL_USERNAME is blank, all links are logged to the console
 * so the flow can be tested without a real SMTP account.
 *
 * Brevo setup (dashboard.brevo.com):
 *   SMTP & API → SMTP → Generate SMTP key
 *   MAIL_HOST     = smtp-relay.brevo.com
 *   MAIL_PORT     = 587
 *   MAIL_USERNAME = your Brevo login email
 *   MAIL_PASSWORD = the generated SMTP key (NOT your Brevo login password)
 *   MAIL_FROM     = a verified sender address in your Brevo account
 */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private boolean isMailConfigured() {
        return smtpUsername != null && !smtpUsername.isBlank();
    }

    // ── Password reset ────────────────────────────────────────────────────────

    public void sendPasswordReset(String toEmail, String token) {
        String link = baseUrl + "/reset-password?token=" + token;

        if (!isMailConfigured()) {
            logToConsole("PASSWORD RESET", toEmail, link, "24 hours");
            return;
        }

        send(toEmail,
             "Reset your Harvest Technologies password",
             """
             Hi,

             You requested a password reset for your Harvest Technologies Forex Platform account.

             Click the link below to set a new password. This link is valid for 24 hours:

             %s

             If you didn't request this, you can safely ignore this email — your password has not been changed.

             — The Harvest Technologies Team
               AI Forex Trading Platform
             """.formatted(link));
    }

    // ── Invite new user ───────────────────────────────────────────────────────

    public void sendInvite(String toEmail, String token) {
        String link = baseUrl + "/invite/accept?token=" + token;

        if (!isMailConfigured()) {
            logToConsole("ACCOUNT INVITE", toEmail, link, "72 hours");
            return;
        }

        send(toEmail,
             "You've been invited to Harvest Technologies Forex Platform",
             """
             Hi,

             You have been invited to join the Harvest Technologies AI Forex Trading Platform.

             Click the link below to set up your account and choose your password.
             This link is valid for 72 hours:

             %s

             If you weren't expecting this invitation, you can safely ignore this email.

             — The Harvest Technologies Team
               AI Forex Trading Platform
             """.formatted(link));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom("Harvest Technologies <" + fromAddress + ">");
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Email sent → {} | subject: {}", to, subject);
        } catch (MailException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            // Don't propagate — caller already shows a neutral UI message
        }
    }

    private void logToConsole(String type, String to, String link, String expiry) {
        log.warn("╔══════════════════════════════════════════════════════════════╗");
        log.warn("  {}  (no SMTP configured — dev mode)", type);
        log.warn("  To      : {}", to);
        log.warn("  Link    : {}", link);
        log.warn("  Expires : {}", expiry);
        log.warn("╚══════════════════════════════════════════════════════════════╝");
    }
}
