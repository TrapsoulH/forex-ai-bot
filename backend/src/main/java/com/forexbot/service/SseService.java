package com.forexbot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages all active SSE connections from dashboard clients.
 *
 * Thread-safe: CopyOnWriteArrayList handles concurrent connect/disconnect.
 * Emitters time out after 5 minutes and reconnect automatically from the frontend.
 */
@Slf4j
@Service
public class SseService {

    private static final long TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** Called when a dashboard tab connects. Returns an emitter bound to that client. */
    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE client disconnected — active: {}", emitters.size());
        });
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
            log.debug("SSE client timed out — active: {}", emitters.size());
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("SSE client error ({}) — active: {}", e.getMessage(), emitters.size());
        });

        emitters.add(emitter);
        log.debug("SSE client connected — active: {}", emitters.size());

        // Send an initial heartbeat so the client knows the connection is alive
        try {
            emitter.send(SseEmitter.event().name("connected").data("{\"status\":\"ok\"}"));
        } catch (Exception e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /**
     * Broadcast a "signal" event — triggers the frontend to refresh the signals table.
     * Called after every signal is processed (BUY, SELL, or HOLD).
     */
    public void broadcastSignal() {
        broadcast("signal", "{\"type\":\"signal\"}");
    }

    /**
     * Broadcast a "trade" event — triggers the frontend to refresh positions + stat cards.
     * Called when a trade is opened or closed.
     */
    public void broadcastTrade() {
        broadcast("trade", "{\"type\":\"trade\"}");
    }

    /**
     * Broadcast a "status" event — triggers the frontend to refresh bot enabled/disabled
     * state and market-open state without a page reload.
     * Called by MarketSchedulerService when the bot is auto-enabled or auto-disabled.
     */
    public void broadcastStatus() {
        broadcast("status", "{\"type\":\"status\"}");
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void broadcast(String event, String data) {
        if (emitters.isEmpty()) return;

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }

        if (!dead.isEmpty()) {
            emitters.removeAll(dead);
        }

        log.debug("SSE broadcast '{}' → {} clients ({} stale removed)",
                event, emitters.size(), dead.size());
    }
}
