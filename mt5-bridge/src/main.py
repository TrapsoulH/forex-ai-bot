"""
MT5 Bridge — FastAPI service exposing MT5 data and order execution over HTTP.
"""
import sys
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, BackgroundTasks
from pydantic import BaseModel
from loguru import logger
import asyncio

import mt5_client
import feed
import executor
from config import settings


# ── Logging ──────────────────────────────────────────────────────────────────
logger.remove()
logger.add(sys.stdout, format="{time:HH:mm:ss} | {level} | {message}", level="DEBUG")


# ── Lifespan ─────────────────────────────────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Connecting to MetaTrader 5 ...")
    if not mt5_client.connect():
        logger.critical("Cannot start — MT5 connection failed. Check credentials in .env")
        sys.exit(1)
    yield
    mt5_client.disconnect()


app = FastAPI(
    title="Forex AI Bot — MT5 Bridge",
    version="1.0.0",
    description="Exposes MetaTrader 5 data feed and order execution via REST API.",
    lifespan=lifespan,
)


# ── Schemas ───────────────────────────────────────────────────────────────────
class TradeRequest(BaseModel):
    symbol: str
    direction: str          # "BUY" | "SELL"
    volume: float = 0.01
    sl_pips: float = 30.0
    tp_pips: float = 60.0


class CloseRequest(BaseModel):
    ticket: int


# ── Routes ────────────────────────────────────────────────────────────────────
@app.get("/health")
def health():
    return {"status": "ok", "connected": mt5_client.is_connected()}


@app.get("/account")
def account():
    info = mt5_client.get_account_info()
    if not info:
        raise HTTPException(503, "MT5 not connected")
    return info


@app.get("/candles/{symbol}")
def candles(symbol: str, timeframe: str = "H1", count: int = 500):
    """Fetch OHLCV candles. timeframe: M1 M5 M15 M30 H1 H4 D1"""
    df = feed.get_candles(symbol.upper(), timeframe, count)
    if df is None:
        raise HTTPException(404, f"No data for {symbol}/{timeframe}")
    return df.to_dict(orient="records")


@app.get("/tick/{symbol}")
def tick(symbol: str):
    data = feed.get_tick(symbol.upper())
    if data is None:
        raise HTTPException(404, f"No tick for {symbol}")
    return data


@app.get("/symbol/{symbol}")
def symbol_info(symbol: str):
    data = feed.get_symbol_info(symbol.upper())
    if data is None:
        raise HTTPException(404, f"Symbol {symbol} not found")
    return data


@app.get("/positions")
def positions():
    return executor.get_open_positions()


@app.post("/trade/open")
def open_trade(req: TradeRequest):
    result = executor.open_trade(
        symbol=req.symbol.upper(),
        direction=req.direction.upper(),
        volume=req.volume,
        sl_pips=req.sl_pips,
        tp_pips=req.tp_pips,
    )
    if not result["success"]:
        raise HTTPException(400, result.get("message", "Trade failed"))
    return result


@app.post("/trade/close")
def close_trade(req: CloseRequest):
    result = executor.close_trade(req.ticket)
    if not result["success"]:
        raise HTTPException(400, result.get("message", "Close failed"))
    return result


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host=settings.host, port=settings.port, reload=False)
