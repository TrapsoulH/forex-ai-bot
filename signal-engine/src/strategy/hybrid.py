"""
Hybrid strategy v2 — SMA + ADX + RSI(9) technical gate with ATR-based SL/TP.

Decision logic:
  1. Technical gate: macro trend (SMA 200) + trend strength (ADX > 20)
     + momentum (SMA 5 vs SMA 30) + RSI zone + entry quality check.
  2. ML gate: XGBoost must agree AND confidence ≥ MIN_CONFIDENCE (45%).
  3. Both agree → signal emitted with ATR-based SL/TP (1:3 R:R).

Gate optimisation: ML is only called when technical gate passes first.

Key changes from v1:
  - MACD removed
  - EMA cross replaced by SMA 5/30/62/100/200 alignment
  - ADX(14) added as trend strength gate (no trade when ADX < 20)
  - RSI(9) replaces RSI(14) — faster, slightly wider zones
  - ATR-based SL/TP replaces fixed pip values (1.5 ATR stop, 4.5 ATR target)
  - ML confidence threshold lowered from 55% to 45% (less strict)
  - Entry quality: rejects entries when price is > 2 ATR from SMA 30
"""
from dataclasses import dataclass, field
from typing import Literal, Optional
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
    sl_price: Optional[float] = field(default=None)
    tp_price: Optional[float] = field(default=None)


def _hold(tech: Signal = "HOLD", ml: Signal = "HOLD",
          ml_conf: float = 0.0, reason: str = "") -> SignalResult:
    """Convenience constructor for HOLD results."""
    return SignalResult(
        signal="HOLD", confidence=0.0,
        technical_signal=tech, ml_signal=ml,
        ml_confidence=ml_conf, reason=reason,
    )


class HybridStrategy:
    """Stateless signal generator. One instance per symbol."""

    MIN_CONFIDENCE = 0.45   # lowered from 0.55 — less strict AI gate

    def __init__(self, symbol: str):
        self.symbol = symbol
        self._predictor = ModelPredictor(symbol)

    # ── Public API ────────────────────────────────────────────────────────────

    def evaluate(self, df_ohlcv: pd.DataFrame) -> SignalResult:
        df   = add_all_indicators(df_ohlcv)
        tech = self._technical_signal(df)

        if tech == "HOLD":
            return _hold(reason=self._technical_hold_reason(df))

        # ── ML gate ───────────────────────────────────────────────────────────
        ml_label, ml_conf = self._ml_signal(df_ohlcv)
        ml_sig: Signal    = {1: "BUY", -1: "SELL", 0: "HOLD"}.get(ml_label, "HOLD")
        pct = lambda v: f"{v * 100:.0f}%"

        if ml_sig == "HOLD":
            return _hold(tech=tech, ml="HOLD", ml_conf=ml_conf,
                         reason=f"AI sees no trade ({pct(ml_conf)}) — chart says {tech}")

        if tech != ml_sig:
            return _hold(tech=tech, ml=ml_sig, ml_conf=ml_conf,
                         reason=f"Split opinion — chart: {tech}, AI: {ml_sig} ({pct(ml_conf)})")

        if ml_conf < self.MIN_CONFIDENCE:
            return _hold(tech=tech, ml=ml_sig, ml_conf=ml_conf,
                         reason=f"AI not confident enough ({pct(ml_conf)}, need {pct(self.MIN_CONFIDENCE)})")

        # ── Signal confirmed — calculate ATR-based SL/TP ──────────────────────
        last  = df.iloc[-1]
        close = float(last["close"])
        atr   = float(last["atr"])
        sl_dist = settings.sl_atr_mult * atr
        tp_dist = settings.tp_atr_mult * atr

        if tech == "BUY":
            sl_price = round(close - sl_dist, 5)
            tp_price = round(close + tp_dist, 5)
        else:
            sl_price = round(close + sl_dist, 5)
            tp_price = round(close - tp_dist, 5)

        logger.info(f"[{self.symbol}] {tech} | AI {pct(ml_conf)} | "
                    f"SL={sl_price} TP={tp_price} (ATR={atr:.5f})")

        return SignalResult(
            signal=tech, confidence=ml_conf,
            technical_signal=tech, ml_signal=ml_sig, ml_confidence=ml_conf,
            reason=f"{tech} — chart & AI agree ({pct(ml_conf)})",
            sl_price=sl_price, tp_price=tp_price,
        )

    # ── Technical gate ────────────────────────────────────────────────────────

    def _technical_signal(self, df: pd.DataFrame) -> Signal:
        """
        Five-gate technical filter:
          1. Macro trend  — price vs SMA 200
          2. Trend strength — ADX > 20 (no ranging markets)
          3. Momentum      — SMA 5 vs SMA 30 alignment
          4. RSI(9) zone   — not at extremes
          5. Entry quality — price within 2 ATR of SMA 30 (no chasing)
        """
        last = df.iloc[-1]

        close   = float(last["close"])
        sma_5   = float(last["sma_5"])
        sma_30  = float(last["sma_30"])
        sma_200 = float(last["sma_200"])
        adx     = float(last["adx"])
        rsi     = float(last["rsi"])
        atr     = float(last["atr"])

        macro_bull = close > sma_200
        macro_bear = close < sma_200

        trend_strong = adx >= settings.adx_min_strength

        mom_bull = sma_5 > sma_30
        mom_bear = sma_5 < sma_30

        rsi_ok_buy  = settings.rsi_oversold < rsi < settings.rsi_overbought
        rsi_ok_sell = settings.rsi_oversold < rsi < settings.rsi_overbought

        # Entry quality: don't enter if price has already run > 2 ATR from SMA 30
        good_entry_buy  = (close - sma_30) < 2.0 * atr
        good_entry_sell = (sma_30 - close) < 2.0 * atr

        if macro_bull and trend_strong and mom_bull and rsi_ok_buy and good_entry_buy:
            return "BUY"
        if macro_bear and trend_strong and mom_bear and rsi_ok_sell and good_entry_sell:
            return "SELL"
        return "HOLD"

    def _technical_hold_reason(self, df: pd.DataFrame) -> str:
        """Plain-English explanation of why the technical gate returned HOLD."""
        last = df.iloc[-1]

        close   = float(last["close"])
        sma_5   = float(last["sma_5"])
        sma_30  = float(last["sma_30"])
        sma_200 = float(last["sma_200"])
        adx     = round(float(last["adx"]), 1)
        rsi     = round(float(last["rsi"]), 1)
        atr     = float(last["atr"])

        macro_bull = close > sma_200
        macro_bear = close < sma_200
        mom_bull   = sma_5 > sma_30
        mom_bear   = sma_5 < sma_30

        # ADX too low — ranging market
        if adx < settings.adx_min_strength:
            return f"Market ranging — ADX {adx} below {settings.adx_min_strength} (no clear trend)"

        # No macro trend
        if not macro_bull and not macro_bear:
            return f"Price near SMA 200 — no directional bias yet (RSI {rsi})"

        if macro_bear:
            if not mom_bear:
                return f"Downtrend but short-term momentum flipping up (SMA 5 > SMA 30) — waiting"
            if rsi <= settings.rsi_oversold:
                return f"Downtrend but RSI oversold ({rsi}) — waiting before entering SELL"
            if rsi >= settings.rsi_overbought:
                return f"Downtrend but RSI overbought ({rsi}) — momentum not aligned"
            overextended = (sma_30 - close) >= 2.0 * atr
            if overextended:
                return f"Downtrend confirmed but price overextended — waiting for pullback to SMA 30"
            return f"Downtrend — not all conditions met (ADX {adx}, RSI {rsi})"

        if macro_bull:
            if not mom_bull:
                return f"Uptrend but short-term momentum flipping down (SMA 5 < SMA 30) — waiting"
            if rsi >= settings.rsi_overbought:
                return f"Uptrend but RSI overbought ({rsi}) — waiting before entering BUY"
            if rsi <= settings.rsi_oversold:
                return f"Uptrend but RSI oversold ({rsi}) — momentum not aligned"
            overextended = (close - sma_30) >= 2.0 * atr
            if overextended:
                return f"Uptrend confirmed but price overextended — waiting for pullback to SMA 30"
            return f"Uptrend — not all conditions met (ADX {adx}, RSI {rsi})"

        return f"Mixed signals — holding (ADX {adx}, RSI {rsi})"

    # ── ML gate ───────────────────────────────────────────────────────────────

    def _ml_signal(self, df_ohlcv: pd.DataFrame) -> tuple[int, float]:
        if not self._predictor.is_trained():
            logger.warning(f"[{self.symbol}] No ML model — falling back to HOLD")
            return 0, 0.0
        return self._predictor.predict(df_ohlcv)
