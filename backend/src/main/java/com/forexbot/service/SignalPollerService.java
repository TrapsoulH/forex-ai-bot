package com.forexbot.service;

import com.forexbot.config.BotProperties;
import com.forexbot.dto.SignalDto;
import com.forexbot.model.Signal;
import com.forexbot.repository.SignalRepository;
import com.forexbot.repository.TradeRepository;
import com.forexbot.repository.UserRepository;
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
    private final EmailService          emailService;
    private final UserRepository        userRepository;
    private final WebClient             signalClient;

    private volatile boolean botEnabled = false;

    // ── Health check state ────────────────────────────────────────────────────
    /** Consecutive failed health-check cycles (resets to 0 on first success). */
    private volatile int     healthFailures = 0;
    /** True after an alert has been sent — prevents repeated alert emails. */
    private volatile boolean alertSent      = false;

    public SignalPollerService(
            BotProperties botProperties,
            SymbolSettingsService symbolSettingsService,
            SignalRepository signalRepository,
            TradeRepository tradeRepository,
            TradeService tradeService,
            SseService sseService,
            EmailService emailService,
            UserRepository userRepository,
            @Qualifier("signalWebClient") WebClient signalClient
    ) {
        this.botProperties        = botProperties;
        this.symbolSettingsService = symbolSettingsService;
        this.signalRepository     = signalRepository;
        this.tradeRepository      = tradeRepository;
        this.tradeService         = tradeService;
        this.sseService           = sseService;
        this.emailService         = emailService;
        this.userRepository       = userRepository;
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
                .timeout(Duration.ofSeconds(30))  // first call fetches candles — give it time
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
            // Per-symbol guard: never open a second trade on the same symbol
            // while one is already open. The global max-trades check above only
            // caps the total count — without this a sustained BUY signal would
            // open a new trade on every scan cycle until the cap is reached.
            if (tradeRepository.existsBySymbolAndStatus(symbol, com.forexbot.model.Trade.TradeStatus.OPEN)) {
                log.debug("[{}] Trade already open — skipping duplicate {} signal", symbol, dto.getSignal());
            } else {
                tradeService.openTrade(symbol, dto.getSignal(), dto.getConfidence(),
                        dto.getSlPrice(), dto.getTpPrice(), dto.getAtr(), saved.getId());
                saved.setActedOn(true);
                signalRepository.save(saved);
                sseService.broadcastTrade();
            }
        }

        sseService.broadcastSignal();
    }

    // ── Signal engine health check ────────────────────────────────────────────
    // Runs every 2 minutes. Sends an alert after 2 consecutive failures
    // (~4 minutes of downtime). Sends a recovery email on the first success
    // after an outage so admins know the engine is back without checking manually.
    @Scheduled(fixedDelay = 120_000)
    public void checkSignalEngineHealth() {
        try {
            signalClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (alertSent) {
                // Recovery — engine is back online
                alertSent      = false;
                healthFailures = 0;
                log.info("Signal engine RECOVERED — sending recovery email to admins");
                notifyAdmins(
                        "Signal engine recovered | Blue Ocean Hub",
                        "The signal engine is back online and responding normally. " +
                        "All scan cycles will resume on the next tick."
                );
            } else {
                healthFailures = 0;
                log.debug("Signal engine health: OK");
            }

        } catch (Exception e) {
            healthFailures++;
            log.warn("Signal engine health check failed ({} consecutive) — {}",
                    healthFailures, e.getMessage());

            if (healthFailures >= 2 && !alertSent) {
                alertSent = true;
                log.error("Signal engine unreachable for {} health checks — alerting admins", healthFailures);
                notifyAdmins(
                        "⚠ Signal engine down | Blue Ocean Hub",
                        "The signal engine has been unreachable for <strong>" + healthFailures
                        + " consecutive health checks</strong> (~" + (healthFailures * 2)
                        + " minutes).<br><br>"
                        + "No new signals are being generated. Please SSH into your server "
                        + "and check the signal-engine process (e.g. "
                        + "<code>systemctl status signal-engine</code> or "
                        + "<code>docker logs signal-engine</code>).<br><br>"
                        + "Error: <code>" + e.getMessage() + "</code>"
                );
            }
        }
    }

    /** Send an alert email to all admin users. Fire-and-forget — never throws. */
    private void notifyAdmins(String subject, String body) {
        try {
            userRepository.findAll().stream()
                    .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                    .filter(u -> u.getRole() == com.forexbot.model.User.Role.ADMIN)
                    .forEach(u -> emailService.sendSystemAlert(u.getEmail(), subject, body));
        } catch (Exception ex) {
            log.error("Failed to send admin alert: {}", ex.getMessage());
        }
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
    //
    // Enabled symbols  → retrain as normal so accuracy improves over time.
    // Disabled symbols → probe: attempt training to see if the broker has
    //   accumulated enough H1 candle history yet. If training succeeds with
    //   ≥ 400 usable samples (roughly 4–5 months of US session hours for
    //   indices), the symbol is auto-enabled so it starts being scanned.
    //   This means you never have to manually remember to re-enable US100 —
    //   it turns itself on once there's enough data.
    @Scheduled(cron = "0 0 3 * * SUN", zone = "Africa/Johannesburg")
    public void retrainAllModels() {
        log.info("Weekly auto-retrain starting for {} symbols", botProperties.getSymbols().size());
        int success   = 0;
        int failed    = 0;
        int autoEnabled = 0;

        // Minimum training samples required before we consider a model usable.
        // Below this the model is likely overfit or nearly random — keep disabled.
        final int MIN_SAMPLES_TO_ENABLE = 400;

        for (String symbol : botProperties.getSymbols()) {
            var settings   = symbolSettingsService.getOrCreate(symbol);
            boolean enabled = settings.isEnabled();

            try {
                Map<?, ?> result = signalClient.post()
                        .uri("/train/{symbol}", symbol)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofMinutes(5))
                        .block();

                Object acc     = result != null ? result.get("accuracy") : null;
                Object samplesObj = result != null ? result.get("samples") : null;
                int    samples = samplesObj instanceof Number n ? n.intValue() : 0;

                if (enabled) {
                    log.info("[{}] Retrain complete — accuracy={} samples={}", symbol, acc, samples);
                    success++;
                } else {
                    // Disabled symbol successfully trained — check if we have enough data
                    if (samples >= MIN_SAMPLES_TO_ENABLE) {
                        symbolSettingsService.save(symbol,
                                settings.getSlAtrMult(), settings.getTpAtrMult(),
                                settings.getVolume(), true);
                        log.info("[{}] AUTO-ENABLED — broker now has enough history " +
                                 "(samples={}, accuracy={}). Symbol will be scanned from next cycle.",
                                 symbol, samples, acc);
                        autoEnabled++;
                        success++;
                    } else {
                        log.info("[{}] Probe train: {} samples so far — need {} to auto-enable. " +
                                 "Check back next Sunday.", symbol, samples, MIN_SAMPLES_TO_ENABLE);
                    }
                }

            } catch (WebClientResponseException e) {
                if (enabled) {
                    log.warn("[{}] Retrain failed — signal engine returned {}: {}",
                             symbol, e.getStatusCode().value(), e.getResponseBodyAsString());
                    failed++;
                } else {
                    // Expected for symbols with no broker history — not a real error
                    log.debug("[{}] Probe train: no broker data yet ({})", symbol, e.getStatusCode().value());
                }
            } catch (Exception e) {
                if (enabled) {
                    log.warn("[{}] Retrain failed — {}", symbol, e.getMessage());
                    failed++;
                } else {
                    log.debug("[{}] Probe train: not ready yet — {}", symbol, e.getMessage());
                }
            }
        }

        log.info("Weekly auto-retrain complete — {}/{} enabled symbols succeeded{}",
                 success, success + failed,
                 autoEnabled > 0 ? " · " + autoEnabled + " symbol(s) auto-enabled" : "");
    }
}
