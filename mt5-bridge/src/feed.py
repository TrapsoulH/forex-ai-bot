"""
Price feed — fetches OHLCV candles and current tick data from MT5.
"""
import MetaTrader5 as mt5
import pandas as pd
from datetime import datetime, timezone
from loguru import logger
from typing import Optional


TIMEFRAME_MAP = {
    "M1": mt5.TIMEFRAME_M1,
    "M5": mt5.TIMEFRAME_M5,
    "M15": mt5.TIMEFRAME_M15,
    "M30": mt5.TIMEFRAME_M30,
    "H1": mt5.TIMEFRAME_H1,
    "H4": mt5.TIMEFRAME_H4,
    "D1": mt5.TIMEFRAME_D1,
}


def get_candles(symbol: str, timeframe: str = "H1", count: int = 500) -> Optional[pd.DataFrame]:
    """Fetch the last `count` OHLCV candles for a symbol."""
    tf = TIMEFRAME_MAP.get(timeframe.upper())
    if tf is None:
        logger.error(f"Unknown timeframe: {timeframe}")
        return None

    rates = mt5.copy_rates_from_pos(symbol, tf, 0, count)
    if rates is None or len(rates) == 0:
        logger.warning(f"No data returned for {symbol}/{timeframe}: {mt5.last_error()}")
        return None

    df = pd.DataFrame(rates)
    df["time"] = pd.to_datetime(df["time"], unit="s", utc=True)
    df.rename(columns={"tick_volume": "volume"}, inplace=True)
    df = df[["time", "open", "high", "low", "close", "volume"]]
    return df


def get_tick(symbol: str) -> Optional[dict]:
    """Get the latest bid/ask tick for a symbol."""
    tick = mt5.symbol_info_tick(symbol)
    if tick is None:
        logger.warning(f"No tick for {symbol}: {mt5.last_error()}")
        return None
    return {
        "symbol": symbol,
        "time": datetime.fromtimestamp(tick.time, tz=timezone.utc).isoformat(),
        "bid": tick.bid,
        "ask": tick.ask,
        "spread": round((tick.ask - tick.bid) * 10000, 1),  # in pips (4-digit pairs)
    }


def get_symbol_info(symbol: str) -> Optional[dict]:
    """Get symbol metadata."""
    info = mt5.symbol_info(symbol)
    if info is None:
        return None
    return {
        "symbol": symbol,
        "digits": info.digits,
        "point": info.point,
        "trade_contract_size": info.trade_contract_size,
        "volume_min": info.volume_min,
        "volume_max": info.volume_max,
        "volume_step": info.volume_step,
    }
