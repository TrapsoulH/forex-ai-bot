-- ============================================================
-- Forex AI Bot — MySQL 8 schema
-- ============================================================

CREATE DATABASE IF NOT EXISTS forexbot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE forexbot;

-- ── Trades ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS trades (
    id                BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    symbol            VARCHAR(10)    NOT NULL,
    direction         ENUM('BUY','SELL') NOT NULL,
    volume            DECIMAL(10,5)  NOT NULL,
    open_price        DECIMAL(10,5),
    close_price       DECIMAL(10,5),
    sl_price          DECIMAL(10,5),
    tp_price          DECIMAL(10,5),
    profit            DECIMAL(10,2),
    status            ENUM('OPEN','CLOSED','CANCELLED') NOT NULL DEFAULT 'OPEN',
    mt5_ticket        BIGINT,
    signal_confidence DECIMAL(5,4),
    opened_at         DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    closed_at         DATETIME(3),
    paper_trade       TINYINT(1)     NOT NULL DEFAULT 1,
    INDEX idx_trades_symbol (symbol),
    INDEX idx_trades_status (status),
    INDEX idx_trades_opened_at (opened_at)
) ENGINE=InnoDB;

-- ── Signals ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS signals (
    id               BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    symbol           VARCHAR(10)    NOT NULL,
    direction        VARCHAR(4)     NOT NULL,   -- BUY | SELL | HOLD
    confidence       DECIMAL(5,4),
    technical_signal VARCHAR(4),
    ml_signal        VARCHAR(4),
    ml_confidence    DECIMAL(5,4),
    reason           TEXT,
    acted_on         TINYINT(1)     NOT NULL DEFAULT 0,
    trade_id         BIGINT,
    created_at       DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_signals_symbol (symbol),
    INDEX idx_signals_created_at (created_at)
) ENGINE=InnoDB;

-- ── OHLCV cache (optional, for backtesting) ───────────────────
CREATE TABLE IF NOT EXISTS ohlcv (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    symbol     VARCHAR(10)   NOT NULL,
    timeframe  VARCHAR(4)    NOT NULL,
    bar_time   DATETIME      NOT NULL,
    open       DECIMAL(10,5) NOT NULL,
    high       DECIMAL(10,5) NOT NULL,
    low        DECIMAL(10,5) NOT NULL,
    close      DECIMAL(10,5) NOT NULL,
    volume     BIGINT        NOT NULL DEFAULT 0,
    UNIQUE KEY uk_ohlcv (symbol, timeframe, bar_time),
    INDEX idx_ohlcv_symbol_tf (symbol, timeframe),
    INDEX idx_ohlcv_time (bar_time)
) ENGINE=InnoDB;

-- ── Bot configuration ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS bot_config (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    config_key   VARCHAR(100)  NOT NULL UNIQUE,
    config_value TEXT          NOT NULL,
    description  TEXT,
    updated_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;

-- Default configuration values
INSERT INTO bot_config (config_key, config_value, description) VALUES
  ('paper_trading',       'true',          'Safety lock — set to false only after thorough testing'),
  ('scan_interval_sec',   '60',            'How often the signal engine is polled (seconds)'),
  ('default_volume',      '0.01',          'Default lot size per trade'),
  ('sl_pips',             '30.0',          'Stop loss in pips'),
  ('tp_pips',             '60.0',          'Take profit in pips (2:1 RR by default)'),
  ('max_open_trades',     '3',             'Maximum simultaneous open positions'),
  ('min_ml_confidence',   '0.55',          'Minimum ML model confidence to act on a signal'),
  ('symbols',             'EURUSD,GBPUSD,USDJPY,AUDUSD', 'Comma-separated list of symbols to trade')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
