"""
Technical indicator computation.

Indicator set (v2):
  - SMA 5 / 30 / 62 / 100 / 200  — trend direction and momentum alignment
  - ADX(14)                        — trend strength filter (no trade in ranging markets)
  - RSI(9)                         — momentum zone check (faster than RSI 14)
  - ATR(14)                        — volatility; used for ATR-based SL/TP calculation
  - Bollinger Bands(20)            — retained as ML feature context
  - OBV                            — volume confirmation (ML feature)

MACD and old EMA fast/slow have been removed.
"""
import pandas as pd
import numpy as np
from ta.trend import ADXIndicator
from ta.momentum import RSIIndicator
from ta.volatility import BollingerBands, AverageTrueRange
from ta.volume import OnBalanceVolumeIndicator
from config import settings


def add_all_indicators(df: pd.DataFrame) -> pd.DataFrame:
    """Compute all indicators used by the strategy and ML model."""
    df = df.copy()

    # ── Simple Moving Averages ────────────────────────────────────
    for period in settings.sma_periods:
        df[f"sma_{period}"] = df["close"].rolling(window=period).mean()

    # ── ADX(14) — trend strength ──────────────────────────────────
    adx_ind      = ADXIndicator(df["high"], df["low"], df["close"],
                                window=settings.adx_period)
    df["adx"]    = adx_ind.adx()
    df["adx_pos"] = adx_ind.adx_pos()   # +DI
    df["adx_neg"] = adx_ind.adx_neg()   # −DI

    # ── RSI(9) ────────────────────────────────────────────────────
    df["rsi"] = RSIIndicator(df["close"], window=settings.rsi_period).rsi()

    # ── ATR(14) — volatility / SL-TP sizing ──────────────────────
    df["atr"] = AverageTrueRange(
        df["high"], df["low"], df["close"], window=settings.atr_period
    ).average_true_range()

    # ── Bollinger Bands(20) — ML feature context ──────────────────
    bb           = BollingerBands(df["close"], window=20, window_dev=2)
    df["bb_upper"] = bb.bollinger_hband()
    df["bb_lower"] = bb.bollinger_lband()
    df["bb_mid"]   = bb.bollinger_mavg()

    # ── OBV — volume confirmation ─────────────────────────────────
    df["obv"] = OnBalanceVolumeIndicator(df["close"], df["volume"]).on_balance_volume()

    # ── Price action ──────────────────────────────────────────────
    df["candle_body"] = abs(df["close"] - df["open"])
    df["upper_wick"]  = df["high"] - df[["open", "close"]].max(axis=1)
    df["lower_wick"]  = df[["open", "close"]].min(axis=1) - df["low"]

    return df


def compute_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Compute normalised feature set for the ML model.
    Returns a DataFrame with feature columns only (no raw OHLCV).

    NOTE: changing this function invalidates existing trained models.
    Delete model .pkl files and retrain after deploying this change.
    """
    df = add_all_indicators(df)

    features = pd.DataFrame(index=df.index)

    # SMA momentum alignment (normalised by close price)
    features["sma5_vs_sma30"]   = (df["sma_5"]  - df["sma_30"])  / df["close"]
    features["sma30_vs_sma62"]  = (df["sma_30"] - df["sma_62"])  / df["close"]
    features["sma62_vs_sma100"] = (df["sma_62"] - df["sma_100"]) / df["close"]
    features["price_vs_sma100"] = (df["close"]  - df["sma_100"]) / df["close"]
    features["price_vs_sma200"] = (df["close"]  - df["sma_200"]) / df["close"]

    # ADX — trend strength [0,1]
    features["adx_norm"]     = df["adx"] / 100.0
    features["adx_di_diff"]  = (df["adx_pos"] - df["adx_neg"]) / 100.0

    # RSI normalised to [-1, 1]
    features["rsi_norm"] = (df["rsi"] - 50) / 50

    # Bollinger Band position: 0 = lower band, 1 = upper band
    bb_range = (df["bb_upper"] - df["bb_lower"]).replace(0, float("nan"))
    features["bb_pos"] = (df["close"] - df["bb_lower"]) / bb_range

    # ATR as % of close (volatility)
    features["atr_pct"] = df["atr"] / df["close"]

    # Candle body direction and size
    features["body_dir"] = np.sign(df["close"] - df["open"])
    features["body_pct"] = df["candle_body"] / df["close"]

    # OBV 1-period % change
    features["obv_change"] = df["obv"].pct_change()

    return features.dropna()
