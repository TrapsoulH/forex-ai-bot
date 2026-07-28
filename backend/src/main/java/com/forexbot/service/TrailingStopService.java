package com.forexbot.service;

import com.forexbot.model.Trade;
import com.forexbot.repository.TradeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Trailing stop loss — runs every 5 minutes against all OPEN trades.
 *
 * Logic (ATR-based, mirrors the 1:3 R:R the signal engine uses):
 *
 *   Phase 1 — Break-even (profit ≥ 2× ATR):
 *     Move SL to entry price so the trade cannot close at a loss.
 *
 *   Phase 2 — Trailing stop (profit ≥ 3× ATR):
 *     Trail SL at (current price − 1× ATR) for BUY,
 *     or (current price + 1× ATR) for SELL.
 *     Only moves in the favourable direction — never widens the stop.
 *
 * Price data comes from the signal engine's /price/{symbol} endpoint which
 * returns the last H1 candle close from its in-memory cache.  For H1 signals
 * the cache is fresh enough — ATR-based distances are large enough that
 * the difference between H1 close and live tick is insignificant.
 *
 * For paper trading this is purely informational (updates the DB record).
 * For live MT5 trading a /modify_sl call to the bridge should be added here
 * once live trading is enabled.
 */
@Slf4j
@Service
public class TrailingStopService {

    private final TradeRepository tradeRepository;
    private final WebClient       signalClient;

    // R:R constants — match signal engine defaults
    private static final double BREAKEVEN_MULT = 2.0;   // move SL to entry at 2× ATR profit
    private static final double TRAIL_MULT     = 3.0;   // start trailing at 3× ATR profit
    private static final double TRAIL_DISTANCE = 1.0;   // trail by 1× ATR

    public TrailingStopService(
            TradeRepository tradeRepository,
            @Qualifier("signalWebClient") WebClient signalClient
    ) {
        this.tradeRepository = tradeRepository;
        this.signalClient    = signalClient;
    }

    @Scheduled(fixedDelay = 300_000)   // every 5 minutes
    public void checkTrailingStops() {
        List<Trade> openTrades = tradeRepository.findByStatusOrderByOpenedAtDesc(Trade.TradeStatus.OPEN);
        if (openTrades.isEmpty()) return;

        log.debug("Trailing stop check — {} open trade(s)", openTrades.size());

        for (Trade trade : openTrades) {
            // Skip if we don't have the data needed to calculate moves
            if (trade.getAtr()       == null
                    || trade.getOpenPrice() == null
                    || trade.getSlPrice()   == null) {
                log.debug("Trade #{} missing atr/entry/sl — skipping trailing stop", trade.getId());
                continue;
            }
            try {
                updateTrailingStop(trade);
            } catch (Exception e) {
                log.warn("Trailing stop check failed for trade #{} ({}): {}",
                        trade.getId(), trade.getSymbol(), e.getMessage());
            }
        }
    }

    private void updateTrailingStop(Trade trade) {
        // Fetch current price from signal engine candle cache
        Map<?, ?> priceData;
        try {
            priceData = signalClient.get()
                    .uri("/price/{symbol}", trade.getSymbol())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(8))
                    .block();
        } catch (Exception e) {
            log.warn("Could not fetch price for {} (trade #{}): {}",
                    trade.getSymbol(), trade.getId(), e.getMessage());
            return;
        }

        if (priceData == null || !(priceData.get("close") instanceof Number)) return;

        double current = ((Number) priceData.get("close")).doubleValue();
        double entry   = trade.getOpenPrice().doubleValue();
        double atr     = trade.getAtr().doubleValue();
        double sl      = trade.getSlPrice().doubleValue();

        if (atr <= 0) return;

        double newSl;
        boolean isBuy = trade.getDirection() == Trade.Direction.BUY;

        if (isBuy) {
            double profit = current - entry;
            if (profit >= TRAIL_MULT * atr) {
                // Phase 2 — trail: SL = current − 1×ATR (only advances, never retreats)
                double trailSl = current - TRAIL_DISTANCE * atr;
                newSl = Math.max(trailSl, sl);
            } else if (profit >= BREAKEVEN_MULT * atr) {
                // Phase 1 — break-even: SL moves up to entry (only if still below)
                newSl = Math.max(entry, sl);
            } else {
                return; // profit not yet large enough to act
            }
        } else {
            // SELL — prices move in the opposite direction
            double profit = entry - current;
            if (profit >= TRAIL_MULT * atr) {
                double trailSl = current + TRAIL_DISTANCE * atr;
                newSl = Math.min(trailSl, sl);
            } else if (profit >= BREAKEVEN_MULT * atr) {
                newSl = Math.min(entry, sl);
            } else {
                return;
            }
        }

        // Only persist if the SL actually changed (avoid spurious DB writes)
        double delta = Math.abs(newSl - sl);
        if (delta < 0.00001) return;

        BigDecimal newSlBd = BigDecimal.valueOf(newSl).setScale(5, RoundingMode.HALF_UP);
        trade.setSlPrice(newSlBd);
        tradeRepository.save(trade);

        String phase = (isBuy ? current - entry : entry - current) >= TRAIL_MULT * atr
                       ? "trailing" : "breakeven";
        log.info("Trailing stop [{}] | trade=#{} {} {} | price={} entry={} atr={} | sl {} → {}",
                phase, trade.getId(), trade.getDirection(), trade.getSymbol(),
                round5(current), round5(entry), round6(atr), round5(sl), newSlBd);
    }

    private static double round5(double v) { return Math.round(v * 100_000.0) / 100_000.0; }
    private static double round6(double v) { return Math.round(v * 1_000_000.0) / 1_000_000.0; }
}
