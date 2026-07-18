"""
Technical indicator computation using the `ta` library (replaces pandas-ta).
All functions accept a DataFrame with columns: open, high, low, close, volume.
They return the same DataFrame enriched with indicator columns.
"""
import pandas as pd
import numpy as np
from ta.trend import EMAIndicator, MACD
from ta.momentum import RSIIndicator
from ta.volatility import BollingerBands, AverageTrueRange
from ta.volume import OnBalanceVolumeIndicator
from config import settings


def add_all_indicators(df: pd.DataFrame) -> pd.DataFrame:
    """Compute all indicators used by the strategy."""
    df = df.copy()

    # ── Trend ────────────────────────────────────────────────────
    df[f"ema_{settings.ema_fast}"] = EMAIndicator(df["close"], window=settings.ema_fast).ema_indicator()
    df[f"ema_{settings.ema_slow}"] = EMAIndicator(df["close"], window=settings.ema_slow).ema_indicator()
    df["ema_200"]                  = EMAIndicator(df["close"], window=200).ema_indicator()

    # ── Momentum ─────────────────────────────────────────────────
    df["rsi"] = RSIIndicator(df["close"], window=settings.rsi_period).rsi()

    macd_ind         = MACD(df["close"])
    df["macd"]       = macd_ind.macd()
    df["macd_signal"] = macd_ind.macd_signal()
    df["macd_hist"]  = macd_ind.macd_diff()

    # ── Volatility ───────────────────────────────────────────────
    bb               = BollingerBands(df["close"], window=20, window_dev=2)
    df["bb_upper"]   = bb.bollinger_hband()
    df["bb_lower"]   = bb.bollinger_lband()
    df["bb_mid"]     = bb.bollinger_mavg()
    df["atr"]        = AverageTrueRange(df["high"], df["low"], df["close"],
                                        window=settings.atr_period).average_true_range()

    # ── Volume ───────────────────────────────────────────────────
    df["obv"] = OnBalanceVolumeIndicator(df["close"], df["volume"]).on_balance_volume()

    # ── Price action ─────────────────────────────────────────────
    df["candle_body"] = abs(df["close"] - df["open"])
    df["upper_wick"]  = df["high"] - df[["open", "close"]].max(axis=1)
    df["lower_wick"]  = df[["open", "close"]].min(axis=1) - df["low"]

    return df


def compute_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Compute normalised feature set for the ML model.
    Returns a DataFrame with feature columns only (no raw OHLCV).
    """
    df = add_all_indicators(df)

    ema_f = f"ema_{settings.ema_fast}"
    ema_s = f"ema_{settings.ema_slow}"

    features = pd.DataFrame(index=df.index)

    # EMA crossover distance (normalised by close)
    features["ema_cross"]        = (df[ema_f] - df[ema_s]) / df["close"]
    features["price_vs_ema200"]  = (df["close"] - df["ema_200"]) / df["close"]

    # RSI normalised to [-1, 1]
    features["rsi_norm"]         = (df["rsi"] - 50) / 50

    # MACD histogram normalised by ATR
    features["macd_hist_norm"]   = df["macd_hist"] / df["atr"]

    # Bollinger Band position: 0 = lower band, 1 = upper band
    bb_range = df["bb_upper"] - df["bb_lower"]
    features["bb_pos"]           = (df["close"] - df["bb_lower"]) / bb_range.replace(0, float("nan"))

    # ATR as % of close (volatility)
    features["atr_pct"]          = df["atr"] / df["close"]

    # Candle body direction and size
    features["body_dir"]         = np.sign(df["close"] - df["open"])
    features["body_pct"]         = df["candle_body"] / df["close"]

    # OBV 1-period % change
    features["obv_change"]       = df["obv"].pct_change()

    return features.dropna()
