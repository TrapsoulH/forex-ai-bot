package com.forexbot.controller;

import com.forexbot.service.MarketHoursService;
import com.forexbot.service.SignalPollerService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds shared model attributes to every Thymeleaf template,
 * so nav widgets (bot status pill, market state, etc.) work without
 * repeating the same logic in every controller.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final SignalPollerService pollerService;
    private final MarketHoursService  marketHours;

    public GlobalModelAdvice(SignalPollerService pollerService,
                             MarketHoursService  marketHours) {
        this.pollerService = pollerService;
        this.marketHours   = marketHours;
    }

    /** True when the signal poller is running. */
    @ModelAttribute("botEnabled")
    public boolean botEnabled() {
        return pollerService.isEnabled();
    }

    /** True when the forex spot market is currently open. */
    @ModelAttribute("marketOpen")
    public boolean marketOpen() {
        return marketHours.isOpen();
    }
}
