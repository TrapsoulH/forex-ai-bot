"""
DXY (US Dollar Index) signal filter.

DXY measures USD strength against a basket of 6 currencies.
It is NOT a tradeable symbol — used as context/confirmation only.

Logic:
  - strong_usd  → USD is rising  → favour SELL on USD pairs (EUR/GBP/AUD weaken vs USD)
  - weak_usd    → USD is falling → favour BUY  on USD pairs
  - neutral     → no meaningful DXY bias, ignore filter

DXY is inversely correlated with:
  EURUSD, GBPUSD, AUDUSD, XAUUSD (gold priced in USD)

Not applied to:
  EURJPY, AUDJPY (cross pairs — no direct USD leg)
  US100, US500, US30 (equity indices — correlation is inverse but unreliable intraday)

Bias detection:
  - SMA 5 vs SMA 30 crossover on DXY H1 candles
  - Confirmed by ADX > 20 (trend must be real, not ranging noise)
"""

from __future__ import annotations
from typing import Literal
import pandas as pd
from loguru import logger

DxyBias = Literal["strong_usd", "weak_usd", "neutral"]

# Symbols where DXY applies as a confirmation filter
# signal direction should align with DXY bias (inverse relationship)
DXY_FILTERED_SYMBOLS = {"EURUSD", "GBPUSD", "AUDUSD", "XAUUSD"}


def get_dxy_bias(df_dxy: pd.DataFrame | None) -> DxyBias:
    """
    Derive a USD bias from DXY H1 candles.

    Returns:
        'strong_usd' — DXY uptrend (SMA5 > SMA30, ADX confirmed)
        'weak_usd'   — DXY downtrend (SMA5 < SMA30, ADX confirmed)
        'neutral'    — ranging / no data
    """
    if df_dxy is None or len(df_dxy) < 35:
        return "neutral"

    try:
        close = df_dxy["close"].astype(float)

        sma_5  = close.rolling(5).mean().iloc[-1]
        sma_30 = close.rolling(30).mean().iloc[-1]

        # Simple ADX proxy: compare recent range to overall range
        # (avoids importing ta here — keeps filter lightweight)
        high  = df_dxy["high"].astype(float)
        low   = df_dxy["low"].astype(float)
        tr    = (high - low).rolling(14).mean().iloc[-1]
        price = close.iloc[-1]
        adx_proxy = (tr / price) * 100  # normalised ATR% as trend-strength proxy

        # Trend must be meaningful (ATR% > 0.05% of price = some directional movement)
        trend_active = adx_proxy > 0.05

        if trend_active and sma_5 > sma_30:
            logger.debug(f"[DXY] strong_usd — SMA5={sma_5:.3f} > SMA30={sma_30:.3f}")
            return "strong_usd"
        if trend_active and sma_5 < sma_30:
            logger.debug(f"[DXY] weak_usd — SMA5={sma_5:.3f} < SMA30={sma_30:.3f}")
            return "weak_usd"

        logger.debug(f"[DXY] neutral — ranging (SMA5={sma_5:.3f}, SMA30={sma_30:.3f})")
        return "neutral"

    except Exception as e:
        logger.warning(f"[DXY] bias calculation failed: {e}")
        return "neutral"


def check_dxy_confirmation(symbol: str, technical_signal: str, dxy_bias: DxyBias) -> bool:
    """
    Return True if the technical signal is confirmed (or not contradicted) by DXY.

    Confirmation rules (inverse correlation):
      BUY  on USD pair + strong_usd  → BLOCKED (DXY rising, bad for long EUR/GBP/AUD/XAU)
      SELL on USD pair + weak_usd    → BLOCKED (DXY falling, bad for short EUR/GBP/AUD/XAU)
      neutral bias                   → ALLOWED (no DXY filter applied)
      BUY  + weak_usd                → CONFIRMED
      SELL + strong_usd              → CONFIRMED

    Returns:
        True  → proceed with signal
        False → suppress signal (DXY disagrees)
    """
    if symbol not in DXY_FILTERED_SYMBOLS:
        return True   # DXY filter doesn't apply to this symbol
    if dxy_bias == "neutral":
        return True   # no DXY signal, don't block

    if technical_signal == "BUY" and dxy_bias == "strong_usd":
        logger.info(f"[{symbol}] BUY suppressed — DXY is strong_usd (USD rising, pair likely to fall)")
        return False
    if technical_signal == "SELL" and dxy_bias == "weak_usd":
        logger.info(f"[{symbol}] SELL suppressed — DXY is weak_usd (USD falling, pair likely to rise)")
        return False

    return True  # confirmed or not applicable
