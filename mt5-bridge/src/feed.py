"""
Price feed — fetches OHLCV candles and current tick data from MT5.
Auto-reconnects on IPC errors before returning None.
"""
import MetaTrader5 as mt5
import pandas as pd
from datetime import datetime, timezone
from loguru import logger
from typing import Optional

import mt5_client


TIMEFRAME_MAP = {
    "M1":  mt5.TIMEFRAME_M1,
    "M5":  mt5.TIMEFRAME_M5,
    "M15": mt5.TIMEFRAME_M15,
    "M30": mt5.TIMEFRAME_M30,
    "H1":  mt5.TIMEFRAME_H1,
    "H4":  mt5.TIMEFRAME_H4,
    "D1":  mt5.TIMEFRAME_D1,
}


def _rates_to_df(rates) -> pd.DataFrame:
    df = pd.DataFrame(rates)
    df["time"] = pd.to_datetime(df["time"], unit="s", utc=True)
    df.rename(columns={"tick_volume": "volume"}, inplace=True)
    return df[["time", "open", "high", "low", "close", "volume"]]


def get_candles(symbol: str, timeframe: str = "H1", count: int = 500) -> Optional[pd.DataFrame]:
    """Fetch the last `count` OHLCV candles for a symbol."""
    tf = TIMEFRAME_MAP.get(timeframe.upper())
    if tf is None:
        logger.error(f"Unknown timeframe: {timeframe}")
        return None

    rates = mt5.copy_rates_from_pos(symbol, tf, 0, count)

    if rates is None or len(rates) == 0:
        err_code, err_msg = mt5.last_error()
        logger.warning(f"No data returned for {symbol}/{timeframe}: ({err_code}, '{err_msg}')")

        # IPC pipe broken — reconnect and retry once
        if err_code == mt5_client.IPC_SEND_FAILED:
            logger.warning(f"IPC error on candle fetch for {symbol} — reconnecting...")
            if mt5_client.try_reconnect():
                rates = mt5.copy_rates_from_pos(symbol, tf, 0, count)

        if rates is None or len(rates) == 0:
            return None

    return _rates_to_df(rates)


def get_tick(symbol: str) -> Optional[dict]:
    """Get the latest bid/ask tick for a symbol."""
    tick = mt5.symbol_info_tick(symbol)

    if tick is None:
        err_code, err_msg = mt5.last_error()
        logger.warning(f"No tick for {symbol}: ({err_code}, '{err_msg}')")

        if err_code == mt5_client.IPC_SEND_FAILED:
            logger.warning(f"IPC error on tick fetch for {symbol} — reconnecting...")
            if mt5_client.try_reconnect():
                tick = mt5.symbol_info_tick(symbol)

        if tick is None:
            return None

    return {
        "symbol": symbol,
        "time":   datetime.fromtimestamp(tick.time, tz=timezone.utc).isoformat(),
        "bid":    tick.bid,
        "ask":    tick.ask,
        "spread": round((tick.ask - tick.bid) * 10000, 1),
    }


def get_symbol_info(symbol: str) -> Optional[dict]:
    """Get symbol metadata."""
    info = mt5.symbol_info(symbol)
    if info is None:
        return None
    return {
        "symbol":              symbol,
        "digits":              info.digits,
        "point":               info.point,
        "trade_contract_size": info.trade_contract_size,
        "volume_min":          info.volume_min,
        "volume_max":          info.volume_max,
        "volume_step":         info.volume_step,
    }
