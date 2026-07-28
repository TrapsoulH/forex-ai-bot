package com.forexbot.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Per-symbol risk overrides — SL, TP, volume, and whether the symbol is traded at all.
 * Falls back to {@link com.forexbot.config.BotProperties} global defaults when no row exists.
 */
@Entity
@Table(name = "symbol_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SymbolSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String symbol;

    /** Stop loss in pips. */
    @Column(name = "sl_pips", nullable = false, precision = 8, scale = 2)
    private BigDecimal slPips;

    /** Take profit in pips. */
    @Column(name = "tp_pips", nullable = false, precision = 8, scale = 2)
    private BigDecimal tpPips;

    /** Lot size (e.g. 0.01 = micro lot). */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal volume;

    /** When false, the signal engine skips this symbol entirely. */
    @Column(nullable = false)
    private boolean enabled;

    /**
     * ATR multiplier for stop-loss (e.g. 1.5 → SL placed 1.5 × ATR from entry).
     * When the signal engine provides a current ATR value, TradeService uses
     * these multipliers to recalculate SL/TP instead of the fixed-pip fallback.
     */
    @Column(name = "sl_atr_mult", nullable = false, precision = 4, scale = 2)
    private BigDecimal slAtrMult = new BigDecimal("1.50");

    /** ATR multiplier for take-profit (e.g. 4.5 → TP placed 4.5 × ATR from entry, giving 1:3 R:R). */
    @Column(name = "tp_atr_mult", nullable = false, precision = 4, scale = 2)
    private BigDecimal tpAtrMult = new BigDecimal("4.50");

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    // ── Computed helpers (used in Thymeleaf) ─────────────────────────────────

    private static final DateTimeFormatter UPDATED_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.of("UTC"));

    /**
     * Risk:reward ratio as a display string like "1:3.0".
     * Uses ATR multipliers when both are set; falls back to pip-based ratio.
     * Returns "—" on zero / null to avoid division by zero.
     */
    public String rewardRatioLabel() {
        // Prefer ATR-based ratio (primary method)
        if (slAtrMult != null && tpAtrMult != null
                && slAtrMult.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = tpAtrMult.divide(slAtrMult, 1, java.math.RoundingMode.HALF_UP);
            return "1:" + ratio.toPlainString();
        }
        // Fallback to pip-based ratio
        if (slPips == null || slPips.compareTo(BigDecimal.ZERO) == 0) return "—";
        if (tpPips == null) return "—";
        BigDecimal ratio = tpPips.divide(slPips, 1, java.math.RoundingMode.HALF_UP);
        return "1:" + ratio.toPlainString();
    }

    /** Formatted updatedAt timestamp for display, e.g. "18 Jul 2026 14:35 UTC". */
    public String formattedUpdatedAt() {
        if (updatedAt == null) return "—";
        return UPDATED_FMT.format(updatedAt) + " UTC";
    }
}
