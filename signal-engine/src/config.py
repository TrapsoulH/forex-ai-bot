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

    symbols: List[str] = ["EURUSD", "GBPUSD", "USDJPY", "AUDUSD"]
    timeframe: str = "H1"
    candle_count: int = 500
    training_candle_count: int = 5000   # ~7 months of H1 data for XGBoost

    ema_fast: int = 9
    ema_slow: int = 21
    rsi_period: int = 14
    rsi_overbought: float = 70.0
    rsi_oversold: float = 30.0
    atr_period: int = 14

    model_dir: str = "models"


settings = Settings()
