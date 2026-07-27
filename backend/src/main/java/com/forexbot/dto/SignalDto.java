package com.forexbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SignalDto {
    private String symbol;
    private String signal;
    private BigDecimal confidence;
    private String technical;
    private String ml;
    @JsonProperty("ml_confidence")
    private BigDecimal mlConfidence;
    private String reason;
    /** ATR-based stop-loss price (1.5 × ATR from entry). Null for HOLD signals. */
    @JsonProperty("sl_price")
    private BigDecimal slPrice;
    /** ATR-based take-profit price (4.5 × ATR from entry → 1:3 R:R). Null for HOLD signals. */
    @JsonProperty("tp_price")
    private BigDecimal tpPrice;
    /**
     * Current ATR(14) value for this symbol.
     * Used by TradeService to recalculate SL/TP using per-symbol ATR multipliers
     * from SymbolSettings, overriding the signal engine's pre-calculated levels.
     */
    private BigDecimal atr;
}
