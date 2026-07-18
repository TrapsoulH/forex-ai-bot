package com.forexbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Automatically disables the signal poller when the forex market closes
 * (Friday 17:00 ET) and re-enables it when the market reopens
 * (Sunday 17:00 ET).
 *
 * Runs every 15 minutes so transitions are caught within a 15-minute window.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSchedulerService {

    private final MarketHoursService    marketHours;
    private final SignalPollerService   pollerService;

    @Scheduled(fixedDelay = 900_000)   // every 15 minutes
    public void syncWithMarketHours() {
        boolean open    = marketHours.isOpen();
        boolean running = pollerService.isEnabled();

        if (!open && running) {
            log.info("Market closed — auto-disabling signal poller");
            pollerService.disable();
        } else if (open && !running) {
            log.info("Market open — auto-enabling signal poller");
            pollerService.enable();
        }
    }
}
