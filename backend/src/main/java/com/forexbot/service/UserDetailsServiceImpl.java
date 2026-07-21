package com.forexbot.service;

import com.forexbot.model.User;
import com.forexbot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Users log in with their email address — look up by email, fall back to username for admin seed account
        User user = userRepository.findByEmail(email)
                .or(() -> userRepository.findByUsername(email))
                .orElseThrow(() -> {
                    log.warn("Login attempt for unknown email: {}", email);
                    return new UsernameNotFoundException("User not found: " + email);
                });

        // Admin-disabled accounts
        if (!user.isEnabled()) {
            log.warn("Login attempt for disabled account: {}", email);
            throw new DisabledException("Account is disabled.");
        }

        // Email not yet verified
        if (!user.isEmailVerified()) {
            log.warn("Login attempt for unverified account: {}", email);
            throw new DisabledException("EMAIL_NOT_VERIFIED");
        }

        // Auto-unlock if the lock window has expired
        boolean isLocked = false;
        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(Instant.now())) {
                isLocked = true;
                log.warn("Login attempt for locked account: {} (locked until {})", email, user.getLockedUntil());
            } else {
                // Window passed — clear lock so next login attempt starts fresh
                user.setLockedUntil(null);
                user.setFailedLoginAttempts(0);
                userRepository.save(user);
            }
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .disabled(false)
                .accountExpired(false)
                .credentialsExpired(false)
                .accountLocked(isLocked)
                .build();
    }
}
