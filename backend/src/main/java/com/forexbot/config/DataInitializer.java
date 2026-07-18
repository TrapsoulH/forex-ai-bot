package com.forexbot.config;

import com.forexbot.model.User;
import com.forexbot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a default admin account on first startup if no users exist.
 * Change the password immediately after first login.
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "Harvest2025!";
    private static final String DEFAULT_ADMIN_EMAIL    = "admin@harvesttechnologies.com";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = User.builder()
                    .username(DEFAULT_ADMIN_USERNAME)
                    .email(DEFAULT_ADMIN_EMAIL)
                    .passwordHash(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD))
                    .role(User.Role.ADMIN)
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            log.warn("═══════════════════════════════════════════════════════════");
            log.warn("  Default admin created — CHANGE PASSWORD AFTER FIRST LOGIN");
            log.warn("  Username : {}", DEFAULT_ADMIN_USERNAME);
            log.warn("  Password : {}", DEFAULT_ADMIN_PASSWORD);
            log.warn("═══════════════════════════════════════════════════════════");
        } else {
            log.info("Users already exist — skipping default admin seed");
        }
    }
}
