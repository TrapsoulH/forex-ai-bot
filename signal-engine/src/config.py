from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import List

_ENV_FILE = Path(__file__).parent.parent.parent / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE),
        env_prefix="SIGNAL_",
        extra="ignore",          # ignore MT5_BRIDGE_*, DB_* etc. from the shared .env
    )

    host: str = "0.0.0.0"
    port: int = 8002

    mt5_bridge_url: str = "http://localhost:8001"
    backend_url: str = "http://localhost:8080"

    symbols: List[str] = [
        "EURUSD", "GBPUSD", "AUDUSD", "XAUUSD",
        "EURJPY", "AUDJPY", "US100", "US500", "US30",
    ]
    timeframe: str = "H1"
    candle_count: int = 500
    training_candle_count: int = 5000   # ~7 months of H1 data for XGBoost

    # SMA periods (fixed — not configurable per run)
    sma_periods: List[int] = [5, 30, 62, 100, 200]

    # ADX
    adx_period: int = 14
    adx_min_strength: float = 20.0   # below this = ranging market, no trade

    # RSI(9) — faster than RSI(14), wider zone needed
    rsi_period: int = 9
    rsi_overbought: float = 72.0
    rsi_oversold: float = 28.0

    # ATR — used for SL/TP calculation (1.5 × ATR = SL, 4.5 × ATR = TP → 1:3 R:R)
    atr_period: int = 14
    sl_atr_mult: float = 1.5
    tp_atr_mult: float = 4.5

    model_dir: str = "models"


settings = Settings()
