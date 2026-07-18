package com.forexbot.service;

import com.forexbot.dto.WeeklyStatsDto;
import com.forexbot.model.Trade;
import com.forexbot.repository.SignalRepository;
import com.forexbot.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Aggregates trading-week stats from the signals and trades tables.
 * "This week" is defined as the period since the most recent Sunday 17:00 ET
 * (the standard forex week open), provided by MarketHoursService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReviewService {

    private static final List<String> SYMBOLS =
            List.of("EURUSD", "GBPUSD", "USDJPY", "AUDUSD");

    private final SignalRepository signalRepo;
    private final TradeRepository  tradeRepo;
    private final MarketHoursService marketHours;

    public WeeklyStatsDto thisWeek() {
        Instant since = marketHours.currentWeekStart();
        return build(since);
    }

    private WeeklyStatsDto build(Instant since) {
        // ── Signals ───────────────────────────────────────────────────────────
        long total  = signalRepo.findByCreatedAtAfter(since).size();
        long buys   = signalRepo.countByDirectionSince("BUY",  since);
        long sells  = signalRepo.countByDirectionSince("SELL", since);
        long holds  = signalRepo.countByDirectionSince("HOLD", since);
        long acted  = signalRepo.countActedOnSince(since);

        // ── Trades ────────────────────────────────────────────────────────────
        List<Trade> trades = tradeRepo.findSince(since);
        long totalTrades   = trades.size();
        long wins          = tradeRepo.countWinsSince(since);
        long losses        = tradeRepo.countLossesSince(since);
        long open          = trades.stream().filter(t -> t.getStatus() == Trade.TradeStatus.OPEN).count();

        // ── P&L per pair ──────────────────────────────────────────────────────
        Map<String, BigDecimal> pnlBySymbol = new LinkedHashMap<>();
        String bestPair  = null;
        String worstPair = null;
        BigDecimal bestPnl  = null;
        BigDecimal worstPnl = null;

        for (String sym : SYMBOLS) {
            BigDecimal pnl = tradeRepo.sumProfitBySymbolSince(sym, since);
            if (pnl == null) pnl = BigDecimal.ZERO;
            pnlBySymbol.put(sym, pnl);

            if (bestPnl == null || pnl.compareTo(bestPnl) > 0)  { bestPnl  = pnl; bestPair  = sym; }
            if (worstPnl == null || pnl.compareTo(worstPnl) < 0) { worstPnl = pnl; worstPair = sym; }
        }

        BigDecimal totalPnl       = tradeRepo.sumProfitSince(since);
        BigDecimal avgConfWins    = tradeRepo.avgConfidenceWinsSince(since);
        BigDecimal avgConfLosses  = tradeRepo.avgConfidenceLossesSince(since);

        return WeeklyStatsDto.builder()
                .weekStart(since)
                .totalSignals(total)
                .buySignals(buys)
                .sellSignals(sells)
                .holdSignals(holds)
                .actedOnSignals(acted)
                .totalTrades(totalTrades)
                .winTrades(wins)
                .lossTrades(losses)
                .openTrades(open)
                .totalPnl(totalPnl != null ? totalPnl : BigDecimal.ZERO)
                .pnlBySymbol(pnlBySymbol)
                .bestPair(bestPair)
                .worstPair(worstPair)
                .avgConfidenceWins(avgConfWins)
                .avgConfidenceLosses(avgConfLosses)
                .hasData(totalTrades > 0 || total > 0)
                .build();
    }
}
