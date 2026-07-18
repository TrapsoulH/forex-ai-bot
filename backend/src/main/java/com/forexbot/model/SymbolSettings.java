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

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    // ── Computed helpers (used in Thymeleaf) ─────────────────────────────────

    private static final DateTimeFormatter UPDATED_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.of("UTC"));

    /**
     * Risk:reward ratio as a display string like "1:2.0".
     * Returns "—" if SL is zero to avoid division by zero.
     */
    public String rewardRatioLabel() {
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
