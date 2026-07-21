from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import List

_ENV_FILE = Path(__file__).parent.parent.parent / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(_ENV_FILE),
        env_prefix="MT5_BRIDGE_",
        extra="ignore",
    )

    # ── MetaAPI credentials ───────────────────────────────────────────────────
    # Set these in .env — never commit real values.
    # MT5_BRIDGE_METAAPI_TOKEN=your-token-here
    # MT5_BRIDGE_METAAPI_ACCOUNT_ID=your-account-id-here
    metaapi_token: str      = ""
    metaapi_account_id: str = ""

    # MetaAPI REST base URL for historical candles.
    # Find yours in the MetaAPI dashboard → your account → Server URL.
    # Cloud-G2 Europe (London):    https://mt-client-api-v1.london.agiliumtrade.ai
    # Cloud-G2 Europe (Frankfurt): https://mt-client-api-v1.frankfurt.agiliumtrade.ai
    metaapi_base_url: str = "https://mt-client-api-v1.london.agiliumtrade.ai"

    host: str              = "0.0.0.0"
    port: int              = 8001
    signal_engine_url: str = "http://localhost:8002"
    symbols: List[str]     = ["EURUSD", "GBPUSD", "USDJPY", "AUDUSD"]
    paper_trading: bool    = True


settings = Settings()
