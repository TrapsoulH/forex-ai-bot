package com.forexbot.service;

import com.forexbot.dto.AcceptInviteForm;
import com.forexbot.dto.ForgotPasswordForm;
import com.forexbot.dto.RegisterForm;
import com.forexbot.dto.ResetPasswordForm;
import com.forexbot.model.PasswordResetToken;
import com.forexbot.model.User;
import com.forexbot.repository.PasswordResetTokenRepository;
import com.forexbot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public UserService(UserRepository userRepository,
                       PasswordResetTokenRepository tokenRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService) {
        this.userRepository  = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService    = emailService;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Transactional
    public User register(RegisterForm form) {
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (userRepository.existsByUsername(form.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        User user = User.builder()
                .username(form.getUsername())
                .email(form.getEmail())
                .fullName(form.getFullName())
                .passwordHash(passwordEncoder.encode(form.getPassword()))
                .role(User.Role.USER)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered: {} ({})", saved.getUsername(), saved.getEmail());
        return saved;
    }

    // ── Forgot password ───────────────────────────────────────────────────────

    @Transactional
    public void initiatePasswordReset(ForgotPasswordForm form) {
        Optional<User> userOpt = userRepository.findByEmail(form.getEmail());

        // Always return without error — do not reveal whether the email exists
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for unknown email: {}", form.getEmail());
            return;
        }

        User user = userOpt.get();

        // Invalidate any existing tokens for this user
        tokenRepository.deleteAllByUserId(user.getId());

        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken token = PasswordResetToken.builder()
                .token(rawToken)
                .user(user)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        tokenRepository.save(token);

        emailService.sendPasswordReset(user.getEmail(), rawToken);
        log.info("Password reset token issued for user: {}", user.getUsername());
    }

    // ── Reset password ────────────────────────────────────────────────────────

    @Transactional
    public void resetPassword(ResetPasswordForm form) {
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        PasswordResetToken token = tokenRepository.findByToken(form.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset link"));

        if (token.isUsed()) {
            throw new IllegalArgumentException("This reset link has already been used");
        }
        if (token.isExpired()) {
            throw new IllegalArgumentException("This reset link has expired — please request a new one");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        log.info("Password successfully reset for user: {}", user.getUsername());
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    public List<User> findAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Invite flow — admin provides email + role only.
     * Creates the account with a random placeholder password (unusable),
     * issues a 72-hour setup token, and fires the invite email.
     * The invitee sets their full name + password on the /invite/accept page.
     */
    @Transactional
    public User inviteUser(String email, User.Role role, String invitedByUsername) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        // Auto-generate a unique username from the email local-part
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_");
        String username = base;
        int i = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + i++;
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .fullName("")   // set by the invitee on the accept page
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString())) // unusable placeholder
                .role(role != null ? role : User.Role.USER)
                .enabled(true)
                .build();
        User saved = userRepository.save(user);

        // Reuse password_reset_tokens table — 72h expiry (longer than a regular reset)
        tokenRepository.deleteAllByUserId(saved.getId());
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken token = PasswordResetToken.builder()
                .token(rawToken)
                .user(saved)
                .expiresAt(Instant.now().plus(72, ChronoUnit.HOURS))
                .build();
        tokenRepository.save(token);

        emailService.sendInvite(saved.getEmail(), rawToken);
        log.info("User invited: {} ({}) role={} by {}", saved.getEmail(), saved.getUsername(), role, invitedByUsername);
        return saved;
    }

    /**
     * Accept invite — sets the user's full name and password, marks token used.
     */
    @Transactional
    public void acceptInvite(AcceptInviteForm form) {
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        PasswordResetToken token = tokenRepository.findByToken(form.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invite link"));
        if (token.isUsed()) {
            throw new IllegalArgumentException("This invite link has already been used");
        }
        if (token.isExpired()) {
            throw new IllegalArgumentException("This invite link has expired — ask your admin to resend");
        }

        User user = token.getUser();
        user.setFullName(form.getFullName().trim());
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);
        log.info("Invite accepted by user: {} ({})", user.getUsername(), user.getEmail());
    }

    @Transactional
    public void toggleUserEnabled(Long userId, String requestingUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getUsername().equals(requestingUsername)) {
            throw new IllegalArgumentException("You cannot disable your own account");
        }
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        log.info("Admin toggled user {} → enabled={}", user.getUsername(), user.isEnabled());
    }

    @Transactional
    public void changeUserRole(Long userId, User.Role newRole, String requestingUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getUsername().equals(requestingUsername)) {
            throw new IllegalArgumentException("You cannot change your own role");
        }
        user.setRole(newRole);
        userRepository.save(user);
        log.info("Admin changed role of {} → {}", user.getUsername(), newRole);
    }

    // ── OAuth2 helpers ────────────────────────────────────────────────────────

    @Transactional
    public User findOrCreateOAuthUser(String email, String fullName) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_");
            String username = baseUsername;
            int i = 1;
            while (userRepository.existsByUsername(username)) {
                username = baseUsername + i++;
            }

            User user = User.builder()
                    .username(username)
                    .email(email)
                    .fullName(fullName)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString())) // unusable password
                    .role(User.Role.USER)
                    .enabled(true)
                    .build();

            User saved = userRepository.save(user);
            log.info("New OAuth2 user created: {} ({})", saved.getUsername(), saved.getEmail());
            return saved;
        });
    }
}
