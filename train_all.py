"""
train_all.py — trigger ML model training for all symbols via the signal-engine API.

Usage (from project root, any Python, no venv needed — only stdlib):
    python train_all.py

The signal-engine must be running on http://localhost:8002.
"""
import urllib.request
import urllib.error
import json
import sys

SIGNAL_ENGINE_URL = "http://localhost:8002"
SYMBOLS = ["EURUSD", "GBPUSD", "USDJPY", "AUDUSD"]


def post(url: str) -> dict:
    req = urllib.request.Request(url, data=b"", method="POST")
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read())


def main():
    print(f"Training ML models for: {', '.join(SYMBOLS)}")
    print(f"Signal engine: {SIGNAL_ENGINE_URL}\n")

    for symbol in SYMBOLS:
        url = f"{SIGNAL_ENGINE_URL}/train/{symbol}"
        print(f"  [{symbol}] POST {url} ...", end=" ", flush=True)
        try:
            result = post(url)
            print(f"✓  accuracy={result.get('accuracy', '?'):.3f}  samples={result.get('samples', '?')}")
        except urllib.error.HTTPError as e:
            body = e.read().decode()
            print(f"✗  HTTP {e.code}: {body}")
            sys.exit(1)
        except Exception as e:
            print(f"✗  {e}")
            sys.exit(1)

    print("\nAll models trained. Restart the signal-engine to pick them up (or they reload automatically).")


if __name__ == "__main__":
    main()
