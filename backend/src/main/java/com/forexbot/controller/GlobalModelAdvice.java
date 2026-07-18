package com.forexbot.controller;

import com.forexbot.service.SignalPollerService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds shared model attributes to every Thymeleaf template,
 * so nav widgets (bot status pill, etc.) work without repeating
 * the same logic in every controller.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final SignalPollerService pollerService;

    public GlobalModelAdvice(SignalPollerService pollerService) {
        this.pollerService = pollerService;
    }

    /** True when the signal poller is running — drives the nav bot-status pill. */
    @ModelAttribute("botEnabled")
    public boolean botEnabled() {
        return pollerService.isEnabled();
    }
}
