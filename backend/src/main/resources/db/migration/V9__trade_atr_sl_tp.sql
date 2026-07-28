-- V9: Store ATR, SL, and TP on each trade row so TrailingStopService can
--     move the stop loss without re-querying the signal engine.
--
--     atr     — ATR(14) value at signal time (used as the trailing-stop unit)
--     sl_price / tp_price were already in the schema but were NOT being
--     written by TradeService. This migration is a no-op for those columns;
--     the application-layer fix in TradeService.openTrade() now populates them.

ALTER TABLE trades
    ADD COLUMN atr DECIMAL(10,6) NULL;
