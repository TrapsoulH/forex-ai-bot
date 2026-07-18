package com.forexbot.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Transactional email service — HTML emails via Brevo SMTP relay in UAT/prod.
 *
 * Dev fallback: if MAIL_USERNAME is blank, all links are logged to the console
 * so the invite/reset flow can be tested without a real SMTP account.
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

    private final JavaMailSender   mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${app.mail.from:${spring.mail.username:}}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
        this.mailSender     = mailSender;
        this.templateEngine = templateEngine;
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

        Context ctx = new Context();
        ctx.setVariable("resetLink", link);

        sendHtml(toEmail,
                 "Reset your Harvest Technologies password",
                 "email/password-reset",
                 ctx);
    }

    // ── Invite new user ───────────────────────────────────────────────────────

    public void sendInvite(String toEmail, String token) {
        String link = baseUrl + "/invite/accept?token=" + token;

        if (!isMailConfigured()) {
            logToConsole("ACCOUNT INVITE", toEmail, link, "72 hours");
            return;
        }

        Context ctx = new Context();
        ctx.setVariable("inviteLink", link);

        sendHtml(toEmail,
                 "You've been invited to Harvest Technologies",
                 "email/invite",
                 ctx);
    }

    // ── Trade notification ────────────────────────────────────────────────────

    public void sendTradeOpened(String toEmail, com.forexbot.model.Trade trade) {
        if (!isMailConfigured()) {
            log.warn("╔══ TRADE OPENED (dev mode — no SMTP) ═══════════════════════════╗");
            log.warn("  To: {} | {} {} vol={} price={}",
                    toEmail, trade.getDirection(), trade.getSymbol(),
                    trade.getVolume(), trade.getOpenPrice());
            log.warn("╚═════════════════════════════════════════════════════════════════╝");
            return;
        }

        Context ctx = new Context();
        ctx.setVariable("trade", trade);
        ctx.setVariable("dashboardUrl", baseUrl + "/dashboard");

        String direction = trade.getDirection().name();
        String subject   = direction + " " + trade.getSymbol()
                         + " — Trade opened | Harvest Technologies";

        sendHtml(toEmail, subject, "email/trade-notification", ctx);
    }

    // ── Weekly review ─────────────────────────────────────────────────────────

    public void sendWeeklyReview(String toEmail, com.forexbot.dto.WeeklyStatsDto stats) {
        if (!isMailConfigured()) {
            log.warn("╔══ WEEKLY REVIEW (dev mode — no SMTP) ═════════════════════════╗");
            log.warn("  To: {} | Signals: {} | Trades: {} | P&L: {}",
                    toEmail, stats.getTotalSignals(), stats.getTotalTrades(), stats.getTotalPnl());
            log.warn("╚══════════════════════════════════════════════════════════════╝");
            return;
        }

        Context ctx = new Context();
        ctx.setVariable("stats", stats);
        ctx.setVariable("dashboardUrl", baseUrl + "/dashboard");

        sendHtml(toEmail,
                 "Your weekly trading review — Harvest Technologies",
                 "email/weekly-review",
                 ctx);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void sendHtml(String to, String subject, String templateName, Context ctx) {
        try {
            String html = templateEngine.process(templateName, ctx);

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom("Harvest Technologies <" + fromAddress + ">");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true); // true = HTML

            mailSender.send(msg);
            log.info("Email sent → {} | subject: {}", to, subject);
        } catch (MailException | jakarta.mail.MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            // Don't propagate — caller shows a neutral UI message regardless
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
