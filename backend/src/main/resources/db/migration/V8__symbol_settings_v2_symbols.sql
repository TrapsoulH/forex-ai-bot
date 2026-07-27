-- V8: Align symbol_settings rows with Strategy V2 symbol list.
-- Removes USDJPY (no longer traded) and seeds the 6 new symbols.
-- Existing EURUSD / GBPUSD / AUDUSD rows are kept as-is (user may have changed their settings).
-- ON DUPLICATE KEY UPDATE is a no-op guard — new symbols won't already exist.

DELETE FROM symbol_settings WHERE symbol = 'USDJPY';

INSERT INTO symbol_settings (symbol, sl_pips, tp_pips, volume, enabled, sl_atr_mult, tp_atr_mult)
VALUES
    -- Gold: wider pip values, ATR-based SL/TP is the primary control
    ('XAUUSD',  200.00, 600.00, 0.0100, TRUE,  1.50, 4.50),
    -- JPY crosses
    ('EURJPY',   25.00,  75.00, 0.0100, TRUE,  1.50, 4.50),
    ('AUDJPY',   25.00,  75.00, 0.0100, TRUE,  1.50, 4.50),
    -- US indices (CFD points, not pips — pip fields are fallback only)
    -- US100 disabled by default: broker has no H1 history so ML model cannot be trained.
    -- Enable manually in bot settings once candle history is available and model is trained.
    ('US100',   150.00, 450.00, 0.0100, FALSE, 1.50, 4.50),
    ('US500',    20.00,  60.00, 0.0100, TRUE,  1.50, 4.50),
    ('US30',    150.00, 450.00, 0.0100, TRUE,  1.50, 4.50)
ON DUPLICATE KEY UPDATE symbol = symbol;  -- no-op if row already exists
