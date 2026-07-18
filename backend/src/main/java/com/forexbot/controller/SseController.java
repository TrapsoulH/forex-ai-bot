package com.forexbot.controller;

import com.forexbot.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events endpoint for the trading dashboard.
 *
 * The frontend connects to /api/dashboard/stream once on page load.
 * Spring holds the HTTP connection open; this service pushes events
 * whenever the signal poller processes a new signal or a trade changes state.
 *
 * Events:
 *   connected  — sent once immediately on connect (heartbeat)
 *   signal     — a new signal was processed → refresh signals table
 *   trade      — a trade was opened or closed → refresh positions + stat cards
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sseService.register();
    }
}
