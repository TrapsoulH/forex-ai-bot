package com.forexbot.controller;

import com.forexbot.model.Trade;
import com.forexbot.repository.SignalRepository;
import com.forexbot.repository.TradeRepository;
import com.forexbot.service.MarketHoursService;
import com.forexbot.service.SignalPollerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JSON API endpoints consumed by the SSE-driven dashboard frontend.
 *
 * These return lightweight maps rather than full JPA entities to avoid
 * lazy-loading issues and keep payloads small.
 *
 * All endpoints are authenticated (standard session cookie) and CSRF-exempt
 * (GET requests + /api/** already excluded in SecurityConfig).
 */
@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveDataController {

    // Use the JVM default zone (= Windows system timezone, SAST on this machine).
    // When deployed to GCP (Phase 5) this will be UTC — cloud timestamps are always UTC.
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SignalRepository   signalRepository;
    private final TradeRepository    tradeRepository;
    private final SignalPollerService pollerService;
    private final MarketHoursService  marketHours;

    // ── /api/live/signals ──────────────────────────────────────────────────

    @GetMapping("/signals")
    public List<Map<String, Object>> recentSignals() {
        return signalRepository.findTop100ByOrderByCreatedAtDesc()
                .stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",              s.getId());
                    m.put("createdAt",       fmt(s.getCreatedAt()));
                    m.put("symbol",          s.getSymbol());
                    m.put("direction",       s.getDirection());
                    m.put("confidence",      pct(s.getConfidence()));
                    m.put("mlConfidence",    pct(s.getMlConfidence()));
                    m.put("technicalSignal", s.getTechnicalSignal());
                    m.put("mlSignal",        s.getMlSignal());
                    m.put("actedOn",         s.isActedOn());
                    m.put("reason",          s.getReason());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ── /api/live/positions ────────────────────────────────────────────────

    @GetMapping("/positions")
    public List<Map<String, Object>> openPositions() {
        return tradeRepository.findByStatusOrderByOpenedAtDesc(Trade.TradeStatus.OPEN)
                .stream()
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",         t.getId());
                    m.put("symbol",     t.getSymbol());
                    m.put("direction",  t.getDirection().name());
                    m.put("volume",     t.getVolume());
                    m.put("openPrice",  t.getOpenPrice());
                    m.put("confidence", pct(t.getSignalConfidence()));
                    m.put("openedAt",   fmt(t.getOpenedAt()));
                    m.put("paperTrade", t.isPaperTrade());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ── /api/live/stats ────────────────────────────────────────────────────

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        BigDecimal profit = tradeRepository.totalProfit();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("openTradeCount", tradeRepository.countOpen());
        m.put("totalProfit",    profit != null ? profit.setScale(2, RoundingMode.HALF_UP) : null);
        m.put("botEnabled",     pollerService.isEnabled());
        m.put("marketOpen",     marketHours.isOpen());
        return m;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String fmt(Instant instant) {
        return instant != null ? FMT.format(instant) : null;
    }

    /** Confidence as "85.3" (ready for + "%" in JS), or null. */
    private String pct(BigDecimal value) {
        if (value == null) return null;
        return value.multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP)
                    .toPlainString();
    }
}
