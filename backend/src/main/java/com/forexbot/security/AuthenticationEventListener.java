package com.forexbot.security;

import com.forexbot.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthenticationEventListener {

    private final LoginAttemptService loginAttemptService;
    private final UserService         userService;
    private final HttpServletRequest  request;

    public AuthenticationEventListener(LoginAttemptService loginAttemptService,
                                       UserService userService,
                                       HttpServletRequest request) {
        this.loginAttemptService = loginAttemptService;
        this.userService         = userService;
        this.request             = request;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String ip       = getClientIp();
        String username = event.getAuthentication().getName();
        loginAttemptService.loginSucceeded(ip);
        userService.recordLoginSuccess(username);
        log.debug("Login succeeded: {} from {}", username, ip);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String ip       = getClientIp();
        String username = event.getAuthentication().getName();
        loginAttemptService.loginFailed(ip);
        if (username != null && !username.isBlank()) {
            userService.recordLoginFailure(username);
        }
        log.warn("Login failure: {} from {} — {}", username, ip, event.getException().getMessage());
    }

    private String getClientIp() {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
