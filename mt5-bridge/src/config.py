from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import List

_ENV_FILE = Path(__file__).parent.parent.parent / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE),
        env_prefix="MT5_BRIDGE_",
        extra="ignore",          # ignore DB_*, SIGNAL_* etc. from the shared .env
    )

    # MT5 credentials — use the numeric account ID shown in MT5, not your email
    mt5_login: str
    mt5_password: str
    mt5_server: str
    mt5_path: str = r"C:\Program Files\MetaTrader 5\terminal64.exe"

    host: str = "0.0.0.0"
    port: int = 8001
    signal_engine_url: str = "http://localhost:8002"
    symbols: List[str] = ["EURUSD", "GBPUSD", "USDJPY", "AUDUSD"]
    paper_trading: bool = True


settings = Settings()
