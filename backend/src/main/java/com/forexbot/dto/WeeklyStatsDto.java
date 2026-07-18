package com.forexbot.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;

/**
 * Aggregated stats for one trading week.
 * Populated by WeeklyReviewService and used by both the dashboard
 * section and the Friday email.
 */
@Data
@Builder
public class WeeklyStatsDto {

    // ── Period ────────────────────────────────────────────────────
    private Instant weekStart;        // Sunday 17:00 ET → UTC

    // ── Signals ───────────────────────────────────────────────────
    private long totalSignals;
    private long buySignals;
    private long sellSignals;
    private long holdSignals;
    private long actedOnSignals;      // signals that became a trade

    // ── Trades ────────────────────────────────────────────────────
    private long totalTrades;
    private long winTrades;           // closed with profit > 0
    private long lossTrades;          // closed with profit < 0
    private long openTrades;          // still open at time of report

    // ── P&L ───────────────────────────────────────────────────────
    private BigDecimal totalPnl;
    private Map<String, BigDecimal> pnlBySymbol;   // e.g. EURUSD → +12.40
    private String bestPair;
    private String worstPair;

    // ── ML quality ────────────────────────────────────────────────
    private BigDecimal avgConfidenceWins;    // avg confidence on winning trades
    private BigDecimal avgConfidenceLosses;  // avg confidence on losing trades

    // ── Meta ──────────────────────────────────────────────────────
    private boolean hasData;          // false → no trades fired this week (paper/no signal)

    /** Win rate as a percentage string like "67%", or "—" if no closed trades. */
    public String winRateDisplay() {
        long closed = winTrades + lossTrades;
        if (closed == 0) return "—";
        long pct = Math.round(100.0 * winTrades / closed);
        return pct + "%";
    }

    /** True when the week's total P&L is zero or positive. */
    public boolean isPnlPositive() {
        return totalPnl != null && totalPnl.compareTo(BigDecimal.ZERO) >= 0;
    }

    /** "+12.34" or "-12.34" */
    public String formattedPnl() {
        if (totalPnl == null) return "0.00";
        String val = totalPnl.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return isPnlPositive() ? "+" + val : val;
    }

    /** True when the given per-pair P&L is zero or positive. */
    public boolean isPairPnlPositive(BigDecimal pnl) {
        return pnl != null && pnl.compareTo(BigDecimal.ZERO) >= 0;
    }

    /** "+12.34" or "-12.34" for a per-pair P&L value. */
    public String formattedPairPnl(BigDecimal pnl) {
        if (pnl == null) return "0.00";
        String val = pnl.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return isPairPnlPositive(pnl) ? "+" + val : val;
    }

    /** True when total closed trades >= 50 % win. */
    public boolean isWinning() {
        long closed = winTrades + lossTrades;
        return closed > 0 && winTrades * 2 >= closed;
    }
}
