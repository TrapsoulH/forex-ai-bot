"""
Signal Engine — FastAPI service that computes hybrid trading signals.
"""
from __future__ import annotations   # allows X | Y union syntax on Python 3.9
import sys
import asyncio
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel
import httpx
import pandas as pd
from loguru import logger

from strategy.hybrid import HybridStrategy, SignalResult
from strategy.dxy_filter import get_dxy_bias, DxyBias
from ml.model import ModelTrainer
from config import settings


logger.remove()
logger.add(sys.stdout, format="{time:HH:mm:ss} | {level} | {message}", level="DEBUG")

# ── Candle cache ──────────────────────────────────────────────────────────────
# H1 candles update once per hour. Caching for 55 minutes avoids redundant
# HTTP calls to mt5-bridge on every scan cycle (up to 60× reduction in traffic).
_candle_cache: dict[str, tuple[pd.DataFrame, datetime]] = {}
_CACHE_TTL = timedelta(minutes=55)

# DXY candle cache — refreshed at the same TTL as normal candles
# DXY is fetched as a non-tradeable context symbol (no strategy instance created)
_dxy_cache: tuple[pd.DataFrame, datetime] | None = None
_DXY_SYMBOL = "DXY"

# One strategy instance per symbol
_strategies: dict[str, HybridStrategy] = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    for symbol in settings.symbols:
        _strategies[symbol] = HybridStrategy(symbol)
        logger.info(f"Strategy initialised for {symbol}")
    yield


app = FastAPI(
    title="Forex AI Bot — Signal Engine",
    version="1.0.0",
    description="Computes hybrid technical + ML trading signals.",
    lifespan=lifespan,
)


# ── Helpers ───────────────────────────────────────────────────────────────────
async def _fetch_candles(symbol: str) -> pd.DataFrame:
    """
    Fetch OHLCV candles with in-memory TTL cache.
    H1 candles only update every 60 minutes, so 55-min caching is safe
    and eliminates redundant HTTP round-trips on every scan cycle.
    """
    cache_key = f"{symbol}_{settings.timeframe}"
    now = datetime.now(timezone.utc)

    if cache_key in _candle_cache:
        df_cached, cached_at = _candle_cache[cache_key]
        age = now - cached_at
        if age < _CACHE_TTL:
            logger.debug(f"[{symbol}] Candle cache hit (age {int(age.total_seconds())}s)")
            return df_cached

    logger.debug(f"[{symbol}] Fetching fresh candles from MT5 bridge")
    url = f"{settings.mt5_bridge_url}/candles/{symbol}"
    params = {"timeframe": settings.timeframe, "count": settings.candle_count}
    async with httpx.AsyncClient(timeout=15.0) as client:
        resp = await client.get(url, params=params)
        resp.raise_for_status()
    data = resp.json()
    df = pd.DataFrame(data)
    df["time"] = pd.to_datetime(df["time"])
    _candle_cache[cache_key] = (df, now)
    return df


async def _fetch_dxy_candles() -> pd.DataFrame | None:
    """
    Fetch DXY H1 candles from the MT5 bridge with TTL cache.
    Returns None silently if DXY is not available on the broker
    (in that case the DXY filter falls back to neutral).
    """
    global _dxy_cache
    now = datetime.now(timezone.utc)
    if _dxy_cache is not None:
        df_cached, cached_at = _dxy_cache
        if (now - cached_at) < _CACHE_TTL:
            return df_cached

    try:
        url = f"{settings.mt5_bridge_url}/candles/{_DXY_SYMBOL}"
        params = {"timeframe": settings.timeframe, "count": 100}
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.get(url, params=params)
            resp.raise_for_status()
        data = resp.json()
        df = pd.DataFrame(data)
        df["time"] = pd.to_datetime(df["time"])
        _dxy_cache = (df, now)
        logger.debug(f"[DXY] Fetched {len(df)} candles")
        return df
    except Exception as e:
        logger.debug(f"[DXY] Not available on this broker ({e}) — filter will be neutral")
        return None


# ── Routes ────────────────────────────────────────────────────────────────────
@app.get("/health")
def health():
    return {"status": "ok", "symbols": list(_strategies.keys())}


@app.get("/signal/{symbol}")
async def signal(symbol: str):
    symbol = symbol.upper()
    if symbol not in _strategies:
        raise HTTPException(404, f"Symbol {symbol} not configured")

    try:
        df = await _fetch_candles(symbol)
    except Exception as e:
        raise HTTPException(503, f"Could not fetch candles from MT5 bridge: {e}")

    # DXY filter — fetch in parallel with candles (already cached after first call)
    df_dxy   = await _fetch_dxy_candles()
    dxy_bias = get_dxy_bias(df_dxy)

    result: SignalResult = _strategies[symbol].evaluate(df, dxy_bias=dxy_bias)
    return {
        "symbol":        symbol,
        "signal":        result.signal,
        "confidence":    result.confidence,
        "technical":     result.technical_signal,
        "ml":            result.ml_signal,
        "ml_confidence": result.ml_confidence,
        "reason":        result.reason,
        "sl_price":      result.sl_price,
        "tp_price":      result.tp_price,
        # ATR(14) at signal time — backend uses this to recalculate SL/TP
        # with per-symbol ATR multipliers from SymbolSettings.
        "atr":           result.atr,
        "dxy_bias":      dxy_bias,
    }


@app.post("/train/{symbol}")
async def train(symbol: str):
    """Train (or retrain) the ML model for a symbol using live data from MT5.

    Fetches `training_candle_count` candles (default 5000 ≈ 7 months of H1)
    to give XGBoost enough history for meaningful cross-validation.
    """
    symbol = symbol.upper()
    logger.info(f"[{symbol}] Fetching {settings.training_candle_count} candles for training ...")
    try:
        url = f"{settings.mt5_bridge_url}/candles/{symbol}"
        params = {"timeframe": settings.timeframe, "count": settings.training_candle_count}
        async with httpx.AsyncClient(timeout=60.0) as client:
            resp = await client.get(url, params=params)
            resp.raise_for_status()
        data = resp.json()
        df = pd.DataFrame(data)
        df["time"] = pd.to_datetime(df["time"])
        logger.info(f"[{symbol}] Fetched {len(df)} candles")
    except Exception as e:
        raise HTTPException(503, f"Could not fetch candles: {e}")

    trainer = ModelTrainer(symbol)
    try:
        result = trainer.train(df)
    except ValueError as e:
        raise HTTPException(400, str(e))

    # Reload predictor
    _strategies[symbol] = HybridStrategy(symbol)
    return {"symbol": symbol, "trained": True, **result}


@app.get("/scan")
async def scan():
    """Evaluate signals for all configured symbols."""
    results = {}
    for symbol in _strategies:
        try:
            df = await _fetch_candles(symbol)
            result = _strategies[symbol].evaluate(df)
            results[symbol] = {
                "signal": result.signal,
                "confidence": result.confidence,
                "reason": result.reason,
            }
        except Exception as e:
            results[symbol] = {"error": str(e)}
    return results


@app.get("/market-overview")
async def market_overview():
    """
    Return current indicator snapshot for all symbols in one call.
    Used by the dashboard Market Overview cards.
    No DB writes — read-only snapshot of the latest candle state.
    """
    from indicators.technical import add_all_indicators
    results = {}

    # Fetch DXY once for all symbols (cached — negligible overhead)
    df_dxy   = await _fetch_dxy_candles()
    dxy_bias = get_dxy_bias(df_dxy)

    for symbol in _strategies:
        try:
            df = await _fetch_candles(symbol)
            enriched = add_all_indicators(df)
            last = enriched.iloc[-1]

            strategy = _strategies[symbol]
            result = strategy.evaluate(df, dxy_bias=dxy_bias)

            ml_label, ml_conf = (
                strategy._predictor.predict(df)
                if strategy._predictor.is_trained()
                else (0, 0.0)
            )
            ml_sig = {1: "BUY", -1: "SELL", 0: "HOLD"}.get(ml_label, "HOLD")

            rsi_val  = round(float(last["rsi"]), 1)
            adx_val  = round(float(last["adx"]), 1)
            sma_5    = float(last["sma_5"])
            sma_30   = float(last["sma_30"])
            sma_200  = float(last["sma_200"])
            close    = float(last["close"])

            # Trend direction
            if sma_5 > sma_30 and close > sma_200:
                trend = "bullish"
            elif sma_5 < sma_30 and close < sma_200:
                trend = "bearish"
            else:
                trend = "ranging"

            # ADX strength label
            adx_state = "strong" if adx_val >= 25 else "moderate" if adx_val >= 20 else "weak"

            # RSI zone (using RSI 9 — slightly wider zones)
            if rsi_val < settings.rsi_oversold + 7:   # < 35
                rsi_zone = "oversold"
            elif rsi_val > settings.rsi_overbought - 7:  # > 65
                rsi_zone = "overbought"
            else:
                rsi_zone = "neutral"

            results[symbol] = {
                "symbol":        symbol,
                "signal":        result.signal,
                "trend":         trend,
                "rsi":           rsi_val,
                "rsi_zone":      rsi_zone,
                "adx":           adx_val,
                "adx_state":     adx_state,
                "sma_cross":     "fast_above" if sma_5 > sma_30 else "fast_below",
                "price_vs_200":  "above" if close > sma_200 else "below",
                "technical":     result.technical_signal,
                "ml":            ml_sig,
                "ml_confidence": round(ml_conf * 100, 1),
                "reason":        result.reason,
                "dxy_bias":      dxy_bias,
                "error":         None,
            }
        except Exception as e:
            err_msg = str(e) or "failed to load data"
            logger.warning(f"[{symbol}] market-overview error: {err_msg}")
            results[symbol] = {"symbol": symbol, "error": err_msg}

    return results


@app.get("/debug/{symbol}")
async def debug(symbol: str):
    """Show raw indicator values and both gate decisions for a symbol."""
    from indicators.technical import add_all_indicators
    symbol = symbol.upper()
    if symbol not in _strategies:
        raise HTTPException(404, f"Symbol {symbol} not configured")

    df = await _fetch_candles(symbol)
    enriched = add_all_indicators(df)
    last = enriched.iloc[-1]

    strategy = _strategies[symbol]
    result = strategy.evaluate(df)
    ml_label, ml_conf = strategy._predictor.predict(df) if strategy._predictor.is_trained() else (0, 0.0)
    ml_sig = {1: "BUY", -1: "SELL", 0: "HOLD"}.get(ml_label, "HOLD")

    return {
        "symbol": symbol,
        "signal": result.signal,
        "reason": result.reason,
        "gates": {
            "technical": result.technical_signal,
            "ml": ml_sig,
            "ml_confidence": round(ml_conf, 4),
        },
        "indicators": {
            f"ema_{settings.ema_fast}": round(float(last[f"ema_{settings.ema_fast}"]), 5),
            f"ema_{settings.ema_slow}": round(float(last[f"ema_{settings.ema_slow}"]), 5),
            "ema_200":    round(float(last["ema_200"]), 5),
            "close":      round(float(last["close"]), 5),
            "rsi":        round(float(last["rsi"]), 2),
            "macd_hist":  round(float(last["macd_hist"]), 6),
            "atr":        round(float(last["atr"]), 6),
        },
        "conditions": {
            "price_above_ema200": bool(last["close"] > last["ema_200"]),
            "ema_fast_above_slow": bool(last[f"ema_{settings.ema_fast}"] > last[f"ema_{settings.ema_slow}"]),
            "rsi_buy_zone":  bool(settings.rsi_oversold < last["rsi"] < 65),
            "rsi_sell_zone": bool(35 < last["rsi"] < settings.rsi_overbought),
            "macd_bullish":  bool(last["macd_hist"] > 0),   # sign only (slope dropped)
            "macd_bearish":  bool(last["macd_hist"] < 0),   # sign only (slope dropped)
        },
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host=settings.host, port=settings.port, reload=False)
