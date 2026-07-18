"""
XGBoost model for direction prediction.

Labels:
  1  = BUY  (next candle closes higher by > 0.5× ATR)
 -1  = SELL (next candle closes lower  by > 0.5× ATR)
  0  = HOLD (inside the dead zone)

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


def _make_labels(df: pd.DataFrame, features: pd.DataFrame, atr_mult: float = 0.5) -> pd.Series:
    """Create forward-looking labels aligned with feature rows."""
    # Next candle close change
    future_close = df["close"].shift(-1)
    atr = df["atr"] if "atr" in df.columns else pd.Series(index=df.index, dtype=float)

    diff = future_close - df["close"]
    threshold = atr * atr_mult

    label = pd.Series(0, index=df.index)
    label[diff > threshold] = 1
    label[diff < -threshold] = -1

    # Align with feature index (features may have fewer rows due to dropna)
    return label.reindex(features.index).dropna().astype(int)


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
