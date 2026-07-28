package com.forexbot.service;

import com.forexbot.config.BotProperties;
import com.forexbot.model.SymbolSettings;
import com.forexbot.model.Trade;
import com.forexbot.repository.TradeRepository;
import com.forexbot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
public class TradeService {

    private final BotProperties         botProperties;
    private final SymbolSettingsService symbolSettingsService;
    private final TradeRepository       tradeRepository;
    private final UserRepository        userRepository;
    private final EmailService          emailService;
    private final WebClient             mt5Client;

    public TradeService(
            BotProperties botProperties,
            SymbolSettingsService symbolSettingsService,
            TradeRepository tradeRepository,
            UserRepository userRepository,
            EmailService emailService,
            @Qualifier("mt5WebClient") WebClient mt5Client
    ) {
        this.botProperties        = botProperties;
        this.symbolSettingsService = symbolSettingsService;
        this.tradeRepository      = tradeRepository;
        this.userRepository       = userRepository;
        this.emailService         = emailService;
        this.mt5Client            = mt5Client;
    }

    /**
     * Open a trade, resolving SL/TP using this priority order:
     * <ol>
     *   <li>Per-symbol ATR multipliers (SymbolSettings.slAtrMult / tpAtrMult) when the
     *       signal engine supplied a current ATR value — allows per-symbol R:R customisation.</li>
     *   <li>Pre-calculated price levels from the signal engine (slPrice / tpPrice).</li>
     *   <li>Fixed-pip fallback from SymbolSettings (slPips / tpPips).</li>
     * </ol>
     */
    public Trade openTrade(String symbol, String direction, BigDecimal confidence,
                           BigDecimal slPrice, BigDecimal tpPrice,
                           BigDecimal atr, Long signalId) {
        SymbolSettings sym = symbolSettingsService.getOrCreate(symbol);
        BigDecimal resolvedVolume = sym.getVolume();

        // ── Resolve SL/TP ─────────────────────────────────────────────────────
        BigDecimal resolvedSl = slPrice;
        BigDecimal resolvedTp = tpPrice;

        // Priority 1: recalculate from per-symbol ATR multipliers when ATR is available
        if (atr != null && atr.compareTo(BigDecimal.ZERO) > 0
                && sym.getSlAtrMult() != null && sym.getTpAtrMult() != null) {
            // We need a reference price — use the signal engine's pre-calc to derive entry,
            // or recalculate: for BUY: entry ≈ sl_price + sl_atr_mult * atr
            // Simpler: recalculate absolute levels from the signal's pre-calculated SL/TP
            // entry price = slPrice + slAtrMult * atr (BUY) or slPrice - slAtrMult * atr (SELL)
            if (slPrice != null) {
                double atrD = atr.doubleValue();
                double origSlMult = botProperties.getSlPips(); // fallback baseline
                // Back-derive entry price from signal engine's calculation
                if ("BUY".equalsIgnoreCase(direction)) {
                    // signal: sl = close - 1.5*atr → close = sl + 1.5*atr
                    double entry = slPrice.doubleValue() + 1.5 * atrD; // use default 1.5 to back-derive entry
                    resolvedSl = BigDecimal.valueOf(entry - sym.getSlAtrMult().doubleValue() * atrD)
                                           .setScale(5, java.math.RoundingMode.HALF_UP);
                    resolvedTp = BigDecimal.valueOf(entry + sym.getTpAtrMult().doubleValue() * atrD)
                                           .setScale(5, java.math.RoundingMode.HALF_UP);
                } else {
                    // signal: sl = close + 1.5*atr → close = sl - 1.5*atr
                    double entry = slPrice.doubleValue() - 1.5 * atrD;
                    resolvedSl = BigDecimal.valueOf(entry + sym.getSlAtrMult().doubleValue() * atrD)
                                           .setScale(5, java.math.RoundingMode.HALF_UP);
                    resolvedTp = BigDecimal.valueOf(entry - sym.getTpAtrMult().doubleValue() * atrD)
                                           .setScale(5, java.math.RoundingMode.HALF_UP);
                }
                log.info("SL/TP recalculated using per-symbol ATR multipliers (sl×{} tp×{}) | sl={} tp={}",
                        sym.getSlAtrMult(), sym.getTpAtrMult(), resolvedSl, resolvedTp);
            }
        }

        log.info("Opening trade | symbol={} direction={} volume={} sl={} tp={} paper={} signalId={}",
                symbol, direction, resolvedVolume, resolvedSl, resolvedTp,
                botProperties.isPaperTrading(), signalId);

        Map<String, Object> body;
        if (resolvedSl != null && resolvedTp != null) {
            // ATR-based: pass absolute price levels — MT5 bridge uses them directly
            body = Map.of(
                    "symbol",    symbol,
                    "direction", direction,
                    "volume",    resolvedVolume,
                    "sl_price",  resolvedSl,
                    "tp_price",  resolvedTp
            );
        } else {
            // Fallback: fixed pips from symbol settings
            body = Map.of(
                    "symbol",    symbol,
                    "direction", direction,
                    "volume",    resolvedVolume,
                    "sl_pips",   sym.getSlPips(),
                    "tp_pips",   sym.getTpPips()
            );
        }

        Map<?, ?> response = null;
        try {
            response = mt5Client.post()
                    .uri("/trade/open")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            log.debug("MT5 bridge response: {}", response);
        } catch (WebClientResponseException e) {
            log.error("MT5 bridge rejected trade open | status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to send trade open request to MT5 bridge: {}", e.getMessage());
        }

        Long ticket = null;
        BigDecimal openPrice = null;
        if (response != null) {
            Object t = response.get("order_id");
            if (t instanceof Number n) ticket = n.longValue();
            Object p = response.get("price");
            if (p instanceof Number n) openPrice = BigDecimal.valueOf(n.doubleValue());
        }

        Trade trade = Trade.builder()
                .symbol(symbol)
                .direction(Trade.Direction.valueOf(direction))
                .volume(resolvedVolume)
                .openPrice(openPrice)
                .slPrice(resolvedSl)
                .tpPrice(resolvedTp)
                .atr(atr)
                .status(Trade.TradeStatus.OPEN)
                .mt5Ticket(ticket)
                .signalConfidence(confidence)
                .paperTrade(botProperties.isPaperTrading())
                .build();

        Trade saved = tradeRepository.save(trade);
        log.info("Trade saved | id={} symbol={} direction={} ticket={} price={}",
                saved.getId(), symbol, direction, ticket, openPrice);

        // Notify admins only — they operate the bot; regular users don't need per-trade alerts
        try {
            userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .filter(u -> u.getRole() == com.forexbot.model.User.Role.ADMIN)
                .forEach(u -> emailService.sendTradeOpened(u.getEmail(), saved));
        } catch (Exception e) {
            log.error("Failed to send trade notification emails: {}", e.getMessage());
        }

        return saved;
    }

    public Trade closeTrade(Long tradeId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new IllegalArgumentException("Trade not found: " + tradeId));

        log.info("Closing trade | id={} symbol={} direction={} ticket={}",
                trade.getId(), trade.getSymbol(), trade.getDirection(), trade.getMt5Ticket());

        if (trade.getMt5Ticket() != null) {
            try {
                Map<?, ?> response = mt5Client.post()
                        .uri("/trade/close")
                        .bodyValue(Map.of("ticket", trade.getMt5Ticket()))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(10))
                        .block();
                log.debug("MT5 bridge close response: {}", response);
            } catch (WebClientResponseException e) {
                log.error("MT5 bridge rejected trade close | status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.error("Failed to send trade close request to MT5 bridge: {}", e.getMessage());
            }
        } else {
            log.warn("Trade #{} has no MT5 ticket — closing in DB only", tradeId);
        }

        trade.setStatus(Trade.TradeStatus.CLOSED);
        trade.setClosedAt(Instant.now());
        Trade saved = tradeRepository.save(trade);
        log.info("Trade #{} marked CLOSED in DB", saved.getId());

        // Notify admins — fire-and-forget, never blocks the close response
        try {
            userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .filter(u -> u.getRole() == com.forexbot.model.User.Role.ADMIN)
                .forEach(u -> emailService.sendTradeClosed(u.getEmail(), saved));
        } catch (Exception e) {
            log.error("Failed to send trade close notification emails: {}", e.getMessage());
        }

        return saved;
    }
}
