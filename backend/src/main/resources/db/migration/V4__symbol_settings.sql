-- ── V4: Per-symbol risk settings ─────────────────────────────────────────────
-- Overrides the global SL/TP/volume from BotProperties on a per-symbol basis.
-- The Java service layer falls back to global config when no row exists.

CREATE TABLE symbol_settings (
    id          BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    symbol      VARCHAR(10)     NOT NULL,
    sl_pips     DECIMAL(8,2)    NOT NULL DEFAULT 30.00,
    tp_pips     DECIMAL(8,2)    NOT NULL DEFAULT 60.00,
    volume      DECIMAL(10,4)   NOT NULL DEFAULT 0.0100,
    enabled     BOOLEAN         NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_symbol_settings_symbol UNIQUE (symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed the four default pairs with sensible conservative defaults.
-- Pip values differ by pair; EURUSD/GBPUSD/AUDUSD use decimal pips, USDJPY uses larger numbers.
INSERT INTO symbol_settings (symbol, sl_pips, tp_pips, volume, enabled) VALUES
    ('EURUSD', 20.00, 40.00, 0.0100, TRUE),
    ('GBPUSD', 25.00, 50.00, 0.0100, TRUE),
    ('USDJPY', 20.00, 40.00, 0.0100, TRUE),
    ('AUDUSD', 20.00, 40.00, 0.0100, TRUE);
