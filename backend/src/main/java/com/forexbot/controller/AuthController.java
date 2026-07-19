package com.forexbot.controller;

import com.forexbot.dto.AcceptInviteForm;
import com.forexbot.dto.ForgotPasswordForm;
import com.forexbot.dto.RegisterForm;
import com.forexbot.dto.ResetPasswordForm;
import com.forexbot.model.PasswordResetToken;
import com.forexbot.repository.PasswordResetTokenRepository;
import com.forexbot.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Slf4j
@Controller
public class AuthController {

    private final UserService userService;
    private final PasswordResetTokenRepository tokenRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id:disabled}")
    private String googleClientId;

    public AuthController(UserService userService,
                          PasswordResetTokenRepository tokenRepository) {
        this.userService     = userService;
        this.tokenRepository = tokenRepository;
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    private boolean isOAuthEnabled() {
        return googleClientId != null && !googleClientId.isBlank() && !googleClientId.equals("disabled");
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("oauthEnabled", isOAuthEnabled());
        return "auth/login";
    }

    // ── Register ──────────────────────────────────────────────────────────────

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("form", new RegisterForm());
        model.addAttribute("oauthEnabled", isOAuthEnabled());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.register(form);
            redirectAttrs.addFlashAttribute("registered", true);
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("registrationError", e.getMessage());
            return "auth/register";
        }
    }

    // ── Forgot password ───────────────────────────────────────────────────────

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        model.addAttribute("form", new ForgotPasswordForm());
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @ModelAttribute("form") ForgotPasswordForm form,
                                 BindingResult result,
                                 Model model) {
        if (result.hasErrors()) {
            return "auth/forgot-password";
        }
        userService.initiatePasswordReset(form);
        // Always show success to avoid email enumeration
        model.addAttribute("success",
                "If that email is registered, a reset link has been sent. Check your inbox (or console in dev mode).");
        model.addAttribute("form", new ForgotPasswordForm());
        return "auth/forgot-password";
    }

    // ── Reset password ────────────────────────────────────────────────────────

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam String token, Model model) {
        Optional<PasswordResetToken> opt = tokenRepository.findByToken(token);

        if (opt.isEmpty() || opt.get().isUsed() || opt.get().isExpired()) {
            model.addAttribute("error", "This reset link is invalid or has expired. Please request a new one.");
            return "auth/reset-password";
        }

        ResetPasswordForm form = new ResetPasswordForm();
        form.setToken(token);
        model.addAttribute("form", form);
        model.addAttribute("token", token);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute("form") ResetPasswordForm form,
                                BindingResult result,
                                Model model,
                                RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            model.addAttribute("token", form.getToken());
            model.addAttribute("error", "Please fix the errors below.");
            return "auth/reset-password";
        }
        try {
            userService.resetPassword(form);
            redirectAttrs.addFlashAttribute("passwordReset", true);
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("token", form.getToken());
            return "auth/reset-password";
        }
    }

    // ── Accept invite ─────────────────────────────────────────────────────────

    @GetMapping("/invite/accept")
    public String acceptInvitePage(@RequestParam String token, Model model) {
        Optional<PasswordResetToken> opt = tokenRepository.findByToken(token);

        if (opt.isEmpty() || opt.get().isUsed() || opt.get().isExpired()) {
            model.addAttribute("error",
                "This invite link is invalid or has expired. Please ask your admin to resend the invitation.");
            return "auth/invite-accept";
        }

        AcceptInviteForm form = new AcceptInviteForm();
        form.setToken(token);
        model.addAttribute("form", form);
        model.addAttribute("email", opt.get().getUser().getEmail());
        return "auth/invite-accept";
    }

    @PostMapping("/invite/accept")
    public String acceptInvite(@Valid @ModelAttribute("form") AcceptInviteForm form,
                               BindingResult result,
                               Model model,
                               RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            // Re-fetch email for the form header
            tokenRepository.findByToken(form.getToken())
                    .ifPresent(t -> model.addAttribute("email", t.getUser().getEmail()));
            return "auth/invite-accept";
        }
        try {
            userService.acceptInvite(form);
            redirectAttrs.addFlashAttribute("accountReady", true);
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            tokenRepository.findByToken(form.getToken())
                    .ifPresent(t -> model.addAttribute("email", t.getUser().getEmail()));
            return "auth/invite-accept";
        }
    }
}
