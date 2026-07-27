-- V7: Add ATR-based SL/TP multiplier columns to symbol_settings
-- These replace the fixed-pip approach when the strategy engine provides an ATR value.
-- Defaults match the Strategy V2 values: SL = 1.5 × ATR, TP = 4.5 × ATR (1:3 R:R)

ALTER TABLE symbol_settings
    ADD COLUMN sl_atr_mult DECIMAL(4, 2) NOT NULL DEFAULT 1.50,
    ADD COLUMN tp_atr_mult DECIMAL(4, 2) NOT NULL DEFAULT 4.50;
