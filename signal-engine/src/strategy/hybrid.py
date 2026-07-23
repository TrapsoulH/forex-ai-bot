"""
Hybrid strategy — combines rule-based technical filters with ML prediction.

Decision logic:
  1. Technical filter must agree (trend + momentum alignment).
  2. ML model must predict the same direction with confidence ≥ min_confidence.
  3. Both must agree → signal emitted.  Either neutral → HOLD.

Gate sequence optimisation: ML (XGBoost) is only called if the technical gate
passes first. This avoids unnecessary CPU usage on HOLD cycles.
"""
from dataclasses import dataclass
from typing import Literal
from loguru import logger

import pandas as pd

from indicators.technical import add_all_indicators
from ml.model import ModelPredictor
from config import settings


Signal = Literal["BUY", "SELL", "HOLD"]


@dataclass
class SignalResult:
    signal: Signal
    confidence: float
    technical_signal: Signal
    ml_signal: Signal
    ml_confidence: float
    reason: str


class HybridStrategy:
    """
    Stateless signal generator. One instance per symbol.
    """

    MIN_CONFIDENCE = 0.55  # ML confidence threshold

    def __init__(self, symbol: str):
        self.symbol = symbol
        self._predictor = ModelPredictor(symbol)

    def evaluate(self, df_ohlcv: pd.DataFrame) -> SignalResult:
        df = add_all_indicators(df_ohlcv)
        tech = self._technical_signal(df)

        # ── Gate optimisation: skip ML entirely if technical HOLD ─────────────
        if tech == "HOLD":
            return SignalResult(
                signal="HOLD",
                confidence=0.0,
                technical_signal="HOLD",
                ml_signal="HOLD",
                ml_confidence=0.0,
                reason=self._technical_hold_reason(df),
            )

        # ── ML gate ───────────────────────────────────────────────────────────
        ml_label, ml_conf = self._ml_signal(df_ohlcv)
        ml_sig: Signal = {1: "BUY", -1: "SELL", 0: "HOLD"}.get(ml_label, "HOLD")
        pct = lambda v: f"{v * 100:.0f}%"

        if ml_sig == "HOLD":
            return SignalResult(
                signal="HOLD",
                confidence=0.0,
                technical_signal=tech,
                ml_signal="HOLD",
                ml_confidence=ml_conf,
                reason=f"AI predicts no trade ({pct(ml_conf)} sure) — Technical gate: {tech}",
            )

        if tech != ml_sig:
            return SignalResult(
                signal="HOLD",
                confidence=0.0,
                technical_signal=tech,
                ml_signal=ml_sig,
                ml_confidence=ml_conf,
                reason=f"Gates disagree — Technical: {tech}, AI predicts: {ml_sig} ({pct(ml_conf)})",
            )

        if ml_conf < self.MIN_CONFIDENCE:
            return SignalResult(
                signal="HOLD",
                confidence=ml_conf,
                technical_signal=tech,
                ml_signal=ml_sig,
                ml_confidence=ml_conf,
                reason=f"AI confidence too low to trade ({pct(ml_conf)} — minimum {pct(self.MIN_CONFIDENCE)} required)",
            )

        logger.info(f"[{self.symbol}] Signal: {tech} | AI conf: {pct(ml_conf)}")
        return SignalResult(
            signal=tech,
            confidence=ml_conf,
            technical_signal=tech,
            ml_signal=ml_sig,
            ml_confidence=ml_conf,
            reason=f"Signal confirmed — Technical & AI agree: {tech} ({pct(ml_conf)} confidence)",
        )

    def _technical_signal(self, df: pd.DataFrame) -> Signal:
        """
        Rule-based filter: trend + momentum + MACD confirmation.

        RSI upper cap for BUY is 65 (not 70) to stay conservative while
        allowing trades in mild upward momentum (RSI 60-65 is not overbought).
        Standard overbought = 70; we give 5 points of margin.
        """
        last = df.iloc[-1]
        prev = df.iloc[-2]

        ema_f = f"ema_{settings.ema_fast}"
        ema_s = f"ema_{settings.ema_slow}"

        # ── Trend gate ───────────────────────────────────────────────────────
        bullish_trend = (
            last[ema_f] > last[ema_s]
            and last["close"] > last["ema_200"]
        )
        bearish_trend = (
            last[ema_f] < last[ema_s]
            and last["close"] < last["ema_200"]
        )

        # ── Momentum gate (RSI) ──────────────────────────────────────────────
        # BUY:  oversold < RSI < 65  (was 60 — too tight, blocked valid uptrends)
        # SELL: 35 < RSI < overbought (was 40 — symmetric relaxation)
        rsi_buy  = settings.rsi_oversold < last["rsi"] < 65
        rsi_sell = 35 < last["rsi"] < settings.rsi_overbought

        # ── MACD confirmation ────────────────────────────────────────────────
        # Sign-only check: histogram above/below zero confirms momentum direction.
        # Dropped the "rising/falling" slope requirement — on H1 the histogram
        # oscillates naturally and the slope check was causing false negatives
        # on valid entries where momentum existed but ticked flat for one candle.
        macd_bullish = last["macd_hist"] > 0
        macd_bearish = last["macd_hist"] < 0

        if bullish_trend and rsi_buy and macd_bullish:
            return "BUY"
        if bearish_trend and rsi_sell and macd_bearish:
            return "SELL"
        return "HOLD"

    def _technical_hold_reason(self, df: pd.DataFrame) -> str:
        """Plain-English explanation of exactly why the technical gate returned HOLD."""
        from indicators.technical import add_all_indicators
        enriched = add_all_indicators(df)
        last = enriched.iloc[-1]

        ema_f = f"ema_{settings.ema_fast}"
        ema_s = f"ema_{settings.ema_slow}"

        bullish_trend = last[ema_f] > last[ema_s] and last["close"] > last["ema_200"]
        bearish_trend = last[ema_f] < last[ema_s] and last["close"] < last["ema_200"]
        rsi = round(float(last["rsi"]), 1)
        macd_hist = float(last["macd_hist"])

        if not bullish_trend and not bearish_trend:
            return f"Price is between EMAs — no clear trend direction yet (RSI {rsi})"

        if bearish_trend:
            if rsi <= 35:
                return (f"Bearish trend confirmed but RSI oversold ({rsi}) — "
                        f"waiting for RSI to recover above 35 before entering SELL")
            if rsi >= settings.rsi_overbought:
                return (f"Bearish trend but RSI overbought ({rsi}) — "
                        f"momentum not aligned for SELL entry")
            if macd_hist >= 0:
                return f"Bearish trend, RSI {rsi} in range — MACD histogram not negative yet, holding"
            return f"Bearish trend — not all conditions met for SELL (RSI {rsi})"

        if bullish_trend:
            if rsi >= 65:
                return (f"Bullish trend confirmed but RSI overbought ({rsi}) — "
                        f"waiting for RSI to pull back below 65 before entering BUY")
            if rsi <= settings.rsi_oversold:
                return (f"Bullish trend but RSI oversold ({rsi}) — "
                        f"momentum not aligned for BUY entry")
            if macd_hist <= 0:
                return f"Bullish trend, RSI {rsi} in range — MACD histogram not positive yet, holding"
            return f"Bullish trend — not all conditions met for BUY (RSI {rsi})"

        return f"Mixed signals — no entry (RSI {rsi})"

    def _ml_signal(self, df_ohlcv: pd.DataFrame) -> tuple[int, float]:
        if not self._predictor.is_trained():
            logger.warning(f"[{self.symbol}] No ML model — falling back to HOLD")
            return 0, 0.0
        return self._predictor.predict(df_ohlcv)
