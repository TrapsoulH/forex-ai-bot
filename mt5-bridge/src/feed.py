"""
Price feed — fetches OHLCV candles and tick data via MetaAPI.

Candles:   MetaAPI historical candles REST endpoint (via httpx).
Tick/info: MetaAPI RPC connection (via SDK).
"""
import pandas as pd
from datetime import datetime, timezone
from loguru import logger
from typing import Optional

import mt5_client


# Maps the timeframe strings used by signal-engine → MetaAPI REST format
TIMEFRAME_MAP = {
    "M1":  "1m",
    "M5":  "5m",
    "M15": "15m",
    "M30": "30m",
    "H1":  "1h",
    "H4":  "4h",
    "D1":  "1d",
}


async def get_candles(symbol: str, timeframe: str = "H1", count: int = 500) -> Optional[pd.DataFrame]:
    """
    Fetch the last `count` OHLCV candles via MetaAPI SDK (account.get_historical_candles).
    Returns a DataFrame with columns: time, open, high, low, close, volume.
    """
    tf = TIMEFRAME_MAP.get(timeframe.upper())
    if tf is None:
        logger.error(f"Unknown timeframe: {timeframe}")
        return None

    account = mt5_client.get_account()
    if account is None:
        logger.warning("No MetaAPI account — cannot fetch candles")
        return None

    try:
        logger.debug(f"Fetching candles: {symbol}/{tf} limit={count}")
        candles = await account.get_historical_candles(symbol, tf, limit=count)
        if not candles:
            logger.warning(f"No candles returned for {symbol}/{timeframe}")
            return None

        df = pd.DataFrame(candles)
        df["time"] = pd.to_datetime(df["time"], utc=True)

        # MetaAPI returns tickVolume — map to the volume column signal-engine expects
        if "tickVolume" in df.columns:
            df.rename(columns={"tickVolume": "volume"}, inplace=True)
        elif "volume" not in df.columns:
            df["volume"] = 0

        df = df[["time", "open", "high", "low", "close", "volume"]]
        return df.sort_values("time").reset_index(drop=True)

    except Exception as e:
        logger.warning(f"get_candles({symbol}/{timeframe}) failed: {e}")
        return None


async def get_tick(symbol: str) -> Optional[dict]:
    """Get the latest bid/ask price via MetaAPI RPC connection."""
    conn = mt5_client.get_connection()
    if conn is None:
        logger.warning("No MetaAPI connection — cannot fetch tick")
        return None
    try:
        price = await conn.get_symbol_price(symbol)
        bid = price.get("bid", 0)
        ask = price.get("ask", 0)
        return {
            "symbol": symbol,
            "time":   price.get("time", datetime.now(timezone.utc).isoformat()),
            "bid":    bid,
            "ask":    ask,
            "spread": round((ask - bid) * 10000, 1),
        }
    except Exception as e:
        logger.warning(f"get_tick({symbol}) failed: {e}")
        return None


async def get_symbol_info(symbol: str) -> Optional[dict]:
    """Get symbol metadata via MetaAPI RPC connection."""
    conn = mt5_client.get_connection()
    if conn is None:
        return None
    try:
        spec = await conn.get_symbol_specification(symbol)
        return {
            "symbol":              symbol,
            "digits":              spec.get("digits"),
            "point":               spec.get("point"),
            "trade_contract_size": spec.get("contractSize"),
            "volume_min":          spec.get("minVolume"),
            "volume_max":          spec.get("maxVolume"),
            "volume_step":         spec.get("volumeStep"),
        }
    except Exception as e:
        logger.warning(f"get_symbol_info({symbol}) failed: {e}")
        return None
