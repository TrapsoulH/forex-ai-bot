"""
Signal Engine — FastAPI service that computes hybrid trading signals.
"""
import sys
import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel
import httpx
import pandas as pd
from loguru import logger

from strategy.hybrid import HybridStrategy, SignalResult
from ml.model import ModelTrainer
from config import settings


logger.remove()
logger.add(sys.stdout, format="{time:HH:mm:ss} | {level} | {message}", level="DEBUG")

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
    url = f"{settings.mt5_bridge_url}/candles/{symbol}"
    params = {"timeframe": settings.timeframe, "count": settings.candle_count}
    async with httpx.AsyncClient(timeout=15.0) as client:
        resp = await client.get(url, params=params)
        resp.raise_for_status()
    data = resp.json()
    df = pd.DataFrame(data)
    df["time"] = pd.to_datetime(df["time"])
    return df


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

    result: SignalResult = _strategies[symbol].evaluate(df)
    return {
        "symbol": symbol,
        "signal": result.signal,
        "confidence": result.confidence,
        "technical": result.technical_signal,
        "ml": result.ml_signal,
        "ml_confidence": result.ml_confidence,
        "reason": result.reason,
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
            "rsi_buy_zone":  bool(settings.rsi_oversold < last["rsi"] < 60),
            "rsi_sell_zone": bool(40 < last["rsi"] < settings.rsi_overbought),
            "macd_bullish":  bool(last["macd_hist"] > 0),
            "macd_bearish":  bool(last["macd_hist"] < 0),
        },
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host=settings.host, port=settings.port, reload=False)
