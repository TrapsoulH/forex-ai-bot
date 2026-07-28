"""
XGBoost model for direction prediction.

Labels:
  1  = BUY  — a long trade from this candle's close would have hit TP before SL
 -1  = SELL — a short trade from this candle's close would have hit TP before SL
  0  = HOLD — ambiguous or no clean outcome within the forward window

Labeling uses forward ATR-outcome logic that mirrors the actual trading strategy:
  TP = entry ± tp_mult × ATR(14)   (default 4.5×)
  SL = entry ∓ sl_mult × ATR(14)   (default 1.5×)
  max_bars = 20 H1 candles forward look (~20 trading hours)

This is more meaningful than next-candle direction because it directly
measures whether the signal would have been profitable at the configured R:R.

Usage:
  trainer = ModelTrainer("EURUSD")
  trainer.train(df_ohlcv)          # train and save
  predictor = ModelPredictor("EURUSD")
  label, confidence = predictor.predict(df_ohlcv)
"""
import os
import numpy as np
import pandas as pd
import joblib
from xgboost import XGBClassifier
from sklearn.model_selection import TimeSeriesSplit
from sklearn.metrics import classification_report
from loguru import logger

from indicators import compute_features
from config import settings


def _model_path(symbol: str) -> str:
    os.makedirs(settings.model_dir, exist_ok=True)
    return os.path.join(settings.model_dir, f"{symbol}_xgb.joblib")


def _make_labels(
    df: pd.DataFrame,
    features: pd.DataFrame,
    sl_mult: float = 1.5,
    tp_mult: float = 4.5,
    max_bars: int = 20,
) -> pd.Series:
    """
    Forward ATR-outcome labeling — mirrors how the bot actually trades.

    For each candle, simulates placing both a BUY and SELL from the close:
      BUY  (+1): high hits  entry + tp_mult×ATR  before low  hits entry - sl_mult×ATR
      SELL (-1): low  hits  entry - tp_mult×ATR  before high hits entry + sl_mult×ATR
      HOLD  (0): ambiguous (both or neither resolved) within max_bars candles

    Default sl_mult=1.5 and tp_mult=4.5 matches the bot's R:R (1:3).
    max_bars=20 H1 candles ≈ 20 trading hours of forward look.
    """
    closes = df["close"].values
    highs  = df["high"].values
    lows   = df["low"].values
    atrs   = df["atr"].values if "atr" in df.columns else np.zeros(len(df))
    n      = len(df)

    labels = np.zeros(n, dtype=int)

    for i in range(n - max_bars):
        atr = atrs[i]
        if np.isnan(atr) or atr <= 0:
            continue

        entry   = closes[i]
        buy_tp  = entry + tp_mult * atr
        buy_sl  = entry - sl_mult * atr
        sell_tp = entry - tp_mult * atr
        sell_sl = entry + sl_mult * atr

        buy_result  = 0   # 1 = TP hit, -1 = SL hit, 0 = timeout
        sell_result = 0

        for j in range(i + 1, min(i + max_bars + 1, n)):
            if buy_result == 0:
                if highs[j] >= buy_tp:
                    buy_result = 1
                elif lows[j] <= buy_sl:
                    buy_result = -1

            if sell_result == 0:
                if lows[j] <= sell_tp:
                    sell_result = 1
                elif highs[j] >= sell_sl:
                    sell_result = -1

            if buy_result != 0 and sell_result != 0:
                break  # both resolved — no need to look further

        # Only assign a directional label when one side cleanly wins
        if buy_result == 1 and sell_result != 1:
            labels[i] = 1    # clear BUY opportunity
        elif sell_result == 1 and buy_result != 1:
            labels[i] = -1   # clear SELL opportunity
        # else: 0 — both TP hit (too volatile), neither hit (no move), or SL first

    label_series = pd.Series(labels, index=df.index)
    return label_series.reindex(features.index).dropna().astype(int)


class ModelTrainer:
    def __init__(self, symbol: str):
        self.symbol = symbol

    def train(self, df_ohlcv: pd.DataFrame) -> dict:
        from indicators.technical import add_all_indicators

        df = add_all_indicators(df_ohlcv)
        features = compute_features(df_ohlcv)
        labels = _make_labels(df, features)

        # Align
        idx = features.index.intersection(labels.index)
        X = features.loc[idx].values
        y = labels.loc[idx].values

        if len(X) < 100:
            raise ValueError(f"Not enough data to train: {len(X)} samples")

        # Time-series cross validation
        tscv = TimeSeriesSplit(n_splits=5)
        model = XGBClassifier(
            n_estimators=300,
            max_depth=4,
            learning_rate=0.05,
            subsample=0.8,
            colsample_bytree=0.8,
            use_label_encoder=False,
            eval_metric="mlogloss",
            random_state=42,
        )

        # Map labels {-1, 0, 1} → {0, 1, 2} for XGBoost
        y_mapped = y + 1

        model.fit(X, y_mapped)

        # Evaluate on last split
        for train_idx, val_idx in tscv.split(X):
            pass  # use last fold
        model.fit(X[train_idx], y_mapped[train_idx])
        y_pred = model.predict(X[val_idx]) - 1
        report = classification_report(y[val_idx], y_pred, output_dict=True)

        # Retrain on full data
        model.fit(X, y_mapped)
        joblib.dump(model, _model_path(self.symbol))
        logger.info(f"Model saved: {_model_path(self.symbol)} | accuracy: {report.get('accuracy', 0):.3f}")

        return {"accuracy": report.get("accuracy", 0), "samples": len(X)}


class ModelPredictor:
    def __init__(self, symbol: str):
        self.symbol = symbol
        self._model = None

    def _load(self):
        path = _model_path(self.symbol)
        if not os.path.exists(path):
            raise FileNotFoundError(f"No trained model for {self.symbol}. Run training first.")
        self._model = joblib.load(path)

    def predict(self, df_ohlcv: pd.DataFrame) -> tuple[int, float]:
        """
        Returns (label, confidence):
          label: 1=BUY, -1=SELL, 0=HOLD
          confidence: probability of predicted class (0–1)
        """
        if self._model is None:
            self._load()

        features = compute_features(df_ohlcv)
        if features.empty:
            return 0, 0.0

        X = features.iloc[[-1]].values  # use latest row
        proba = self._model.predict_proba(X)[0]
        mapped_label = int(np.argmax(proba))  # 0, 1, or 2
        label = mapped_label - 1             # -1, 0, or 1
        confidence = float(proba[mapped_label])

        return label, confidence

    def is_trained(self) -> bool:
        return os.path.exists(_model_path(self.symbol))
