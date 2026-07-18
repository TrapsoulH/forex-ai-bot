"""
Hybrid strategy — combines rule-based technical filters with ML prediction.

Decision logic:
  1. Technical filter must agree (trend + momentum alignment).
  2. ML model must predict the same direction with confidence ≥ min_confidence.
  3. Both must agree → signal emitted.  Either neutral → HOLD.

This two-gate design keeps the bot conservative and auditable.
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
        ml_label, ml_conf = self._ml_signal(df_ohlcv)

        # Map ML label to signal
        ml_sig: Signal = {1: "BUY", -1: "SELL", 0: "HOLD"}.get(ml_label, "HOLD")

        # Both gates must agree
        if tech == "HOLD" or ml_sig == "HOLD":
            if tech == "HOLD" and ml_sig == "HOLD":
                gate_reason = "Both gates HOLD"
            elif tech == "HOLD":
                gate_reason = f"Technical HOLD (ml={ml_sig} conf={ml_conf:.2f})"
            else:
                gate_reason = f"ML HOLD conf={ml_conf:.2f} (tech={tech})"
            return SignalResult(
                signal="HOLD",
                confidence=0.0,
                technical_signal=tech,
                ml_signal=ml_sig,
                ml_confidence=ml_conf,
                reason=gate_reason,
            )

        if tech != ml_sig:
            return SignalResult(
                signal="HOLD",
                confidence=0.0,
                technical_signal=tech,
                ml_signal=ml_sig,
                ml_confidence=ml_conf,
                reason=f"Disagreement: tech={tech}, ml={ml_sig}",
            )

        if ml_conf < self.MIN_CONFIDENCE:
            return SignalResult(
                signal="HOLD",
                confidence=ml_conf,
                technical_signal=tech,
                ml_signal=ml_sig,
                ml_confidence=ml_conf,
                reason=f"ML confidence too low: {ml_conf:.2f} < {self.MIN_CONFIDENCE}",
            )

        logger.info(f"[{self.symbol}] Signal: {tech} | ML conf: {ml_conf:.2f}")
        return SignalResult(
            signal=tech,  # both agree
            confidence=ml_conf,
            technical_signal=tech,
            ml_signal=ml_sig,
            ml_confidence=ml_conf,
            reason="Both gates agree",
        )

    def _technical_signal(self, df: pd.DataFrame) -> Signal:
        """
        Rule-based filter combining trend, momentum, and structure.
        Uses the latest 3 candles to reduce noise.
        """
        last = df.iloc[-1]
        prev = df.iloc[-2]

        ema_f = f"ema_{settings.ema_fast}"
        ema_s = f"ema_{settings.ema_slow}"

        # ── Trend gate ──────────────────────────────────────────────────────
        # EMA fast above slow AND price above EMA 200 → bullish trend
        bullish_trend = (
            last[ema_f] > last[ema_s]
            and last["close"] > last["ema_200"]
        )
        bearish_trend = (
            last[ema_f] < last[ema_s]
            and last["close"] < last["ema_200"]
        )

        # ── Momentum gate (RSI) ──────────────────────────────────────────────
        rsi_buy  = settings.rsi_oversold  < last["rsi"] < 60     # not overbought
        rsi_sell = 40 < last["rsi"] < settings.rsi_overbought    # not oversold

        # ── MACD confirmation ────────────────────────────────────────────────
        macd_bullish = last["macd_hist"] > 0 and last["macd_hist"] > prev["macd_hist"]
        macd_bearish = last["macd_hist"] < 0 and last["macd_hist"] < prev["macd_hist"]

        if bullish_trend and rsi_buy and macd_bullish:
            return "BUY"
        if bearish_trend and rsi_sell and macd_bearish:
            return "SELL"
        return "HOLD"

    def _ml_signal(self, df_ohlcv: pd.DataFrame) -> tuple[int, float]:
        if not self._predictor.is_trained():
            logger.warning(f"[{self.symbol}] No ML model — falling back to HOLD")
            return 0, 0.0
        return self._predictor.predict(df_ohlcv)
