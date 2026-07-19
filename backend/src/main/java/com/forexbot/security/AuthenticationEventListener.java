package com.forexbot.security;

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
    private final HttpServletRequest  request;

    public AuthenticationEventListener(LoginAttemptService loginAttemptService,
                                       HttpServletRequest request) {
        this.loginAttemptService = loginAttemptService;
        this.request             = request;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String ip = getClientIp();
        loginAttemptService.loginSucceeded(ip);
        log.debug("Login succeeded from {}", ip);
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String ip = getClientIp();
        loginAttemptService.loginFailed(ip);
        log.warn("Login failure from {} — cause: {}", ip, event.getException().getMessage());
    }

    private String getClientIp() {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
