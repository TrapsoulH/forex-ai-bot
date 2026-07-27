package com.forexbot.service;

import com.forexbot.config.BotProperties;
import com.forexbot.dto.SignalDto;
import com.forexbot.model.Signal;
import com.forexbot.repository.SignalRepository;
import com.forexbot.repository.TradeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class SignalPollerService {

    private final BotProperties         botProperties;
    private final SymbolSettingsService symbolSettingsService;
    private final SignalRepository      signalRepository;
    private final TradeRepository       tradeRepository;
    private final TradeService          tradeService;
    private final SseService            sseService;
    private final WebClient             signalClient;

    private volatile boolean botEnabled = false;

    public SignalPollerService(
            BotProperties botProperties,
            SymbolSettingsService symbolSettingsService,
            SignalRepository signalRepository,
            TradeRepository tradeRepository,
            TradeService tradeService,
            SseService sseService,
            @Qualifier("signalWebClient") WebClient signalClient
    ) {
        this.botProperties        = botProperties;
        this.symbolSettingsService = symbolSettingsService;
        this.signalRepository     = signalRepository;
        this.tradeRepository      = tradeRepository;
        this.tradeService         = tradeService;
        this.sseService           = sseService;
        this.signalClient         = signalClient;
    }

    // US index CFDs — only trade during US session (13:30–20:00 UTC, Mon–Fri)
    private static final Set<String> US_INDICES = Set.of("US100", "US500", "US30");
    private static final ZoneId      UTC        = ZoneId.of("UTC");

    /**
     * Returns true when a US index symbol is currently within its trading window.
     * US equity CFDs trade Mon–Fri 13:30–20:00 UTC (15:30–22:00 SAST).
     */
    private boolean isUsSessionOpen() {
        ZonedDateTime now = ZonedDateTime.now(UTC);
        int dow  = now.getDayOfWeek().getValue(); // 1=Mon … 7=Sun
        int hour = now.getHour();
        int min  = now.getMinute();
        int time = hour * 60 + min;               // minutes since midnight UTC

        boolean weekday  = dow >= 1 && dow <= 5;
        boolean inWindow = time >= (13 * 60 + 30) && time < (20 * 60);
        return weekday && inWindow;
    }

    public void enable()  { botEnabled = true;  log.info("Bot ENABLED"); }
    public void disable() { botEnabled = false; log.info("Bot DISABLED"); }
    public boolean isEnabled() { return botEnabled; }

    @Scheduled(fixedDelayString = "#{${bot.scan-interval-seconds:60} * 1000}")
    public void scan() {
        if (!botEnabled) {
            log.debug("Bot is disabled — skipping scan");
            return;
        }

        long openTrades = tradeRepository.countOpen();
        if (openTrades >= botProperties.getMaxOpenTrades()) {
            log.info("Max open trades reached ({}) — skipping scan", openTrades);
            return;
        }

        for (String symbol : botProperties.getSymbols()) {
            // Respect the per-symbol enabled flag — skip if disabled in bot settings
            if (!symbolSettingsService.getOrCreate(symbol).isEnabled()) {
                log.debug("Symbol {} is disabled in settings — skipping this scan cycle", symbol);
                continue;
            }

            // US index CFDs only trade during the US session (13:30–20:00 UTC Mon–Fri)
            if (US_INDICES.contains(symbol) && !isUsSessionOpen()) {
                log.debug("Symbol {} skipped — US market is closed (outside 13:30–20:00 UTC)", symbol);
                continue;
            }
            try {
                pollSymbol(symbol);
            } catch (WebClientRequestException e) {
                log.warn("Signal engine unreachable for {} — skipping cycle: {}", symbol, e.getMessage());
            } catch (WebClientResponseException e) {
                log.warn("Signal engine returned {} for {} — skipping cycle", e.getStatusCode().value(), symbol);
            } catch (Exception e) {
                log.error("Unexpected error polling signal for {}: {}", symbol, e.getMessage());
            }
        }
    }

    private void pollSymbol(String symbol) {
        SignalDto dto = signalClient.get()
                .uri("/signal/{symbol}", symbol)
                .retrieve()
                .bodyToMono(SignalDto.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        if (dto == null) return;

        // ── Deduplication: skip storing if nothing changed ────────────────────
        // BUY/SELL always saves (could act on it). HOLD only saves when the
        // reason changes — avoids hundreds of identical HOLD rows per day.
        if ("HOLD".equals(dto.getSignal())) {
            Signal last = signalRepository.findTopBySymbolOrderByCreatedAtDesc(symbol);
            if (last != null
                    && "HOLD".equals(last.getDirection())
                    && Objects.equals(last.getReason(), dto.getReason())) {
                log.debug("[{}] Duplicate HOLD — skipping save (reason unchanged)", symbol);
                return; // nothing changed, don't write or broadcast
            }
        }

        Signal saved = signalRepository.save(Signal.builder()
                .symbol(dto.getSymbol())
                .direction(dto.getSignal())
                .confidence(dto.getConfidence())
                .technicalSignal(dto.getTechnical())
                .mlSignal(dto.getMl())
                .mlConfidence(dto.getMlConfidence())
                .reason(dto.getReason())
                .actedOn(false)
                .build());

        log.info("[{}] Signal: {} (conf={}, reason={})",
                symbol, dto.getSignal(), dto.getConfidence(), dto.getReason());

        if (!"HOLD".equals(dto.getSignal())) {
            tradeService.openTrade(symbol, dto.getSignal(), dto.getConfidence(),
                    dto.getSlPrice(), dto.getTpPrice(), dto.getAtr(), saved.getId());
            saved.setActedOn(true);
            signalRepository.save(saved);
            sseService.broadcastTrade();
        }

        sseService.broadcastSignal();
    }

    // ── Retention: delete HOLD signals older than 7 days ─────────────────────
    // Runs every Sunday at 02:00 SAST. BUY/SELL signals are kept indefinitely.
    @Scheduled(cron = "0 0 2 * * SUN", zone = "Africa/Johannesburg")
    public void cleanOldHoldSignals() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));
        int deleted = signalRepository.deleteOldHoldSignals(cutoff);
        log.info("Signal retention: deleted {} HOLD signals older than 7 days", deleted);
    }

    // ── Auto-retrain ML models weekly ────────────────────────────────────────
    // Runs every Sunday at 03:00 SAST (after signal cleanup at 02:00).
    // Retrains XGBoost models for all enabled symbols so accuracy improves
    // as more H1 candle history accumulates on the broker over time.
    @Scheduled(cron = "0 0 3 * * SUN", zone = "Africa/Johannesburg")
    public void retrainAllModels() {
        log.info("Weekly auto-retrain starting for {} symbols", botProperties.getSymbols().size());
        int success = 0;
        int failed  = 0;

        for (String symbol : botProperties.getSymbols()) {
            // Skip disabled symbols — no point training a model that won't be used
            if (!symbolSettingsService.getOrCreate(symbol).isEnabled()) {
                log.debug("[{}] Skipping retrain — symbol is disabled", symbol);
                continue;
            }
            try {
                Map<?, ?> result = signalClient.post()
                        .uri("/train/{symbol}", symbol)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofMinutes(5))   // training fetches 5000 candles — give it time
                        .block();

                Object acc = result != null ? result.get("accuracy") : null;
                Object samples = result != null ? result.get("samples") : null;
                log.info("[{}] Retrain complete — accuracy={} samples={}", symbol, acc, samples);
                success++;
            } catch (WebClientResponseException e) {
                log.warn("[{}] Retrain failed — signal engine returned {}: {}", symbol, e.getStatusCode().value(), e.getResponseBodyAsString());
                failed++;
            } catch (Exception e) {
                log.warn("[{}] Retrain failed — {}", symbol, e.getMessage());
                failed++;
            }
        }

        log.info("Weekly auto-retrain complete — {}/{} symbols succeeded", success, success + failed);
    }
}
