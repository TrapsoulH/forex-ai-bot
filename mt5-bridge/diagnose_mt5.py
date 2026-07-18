"""
Run this from the mt5-bridge folder to diagnose MT5 connectivity.
Usage: python diagnose_mt5.py
"""
import sys

print("=" * 50)
print("MT5 Connection Diagnostic")
print("=" * 50)

# Step 1 — import
try:
    import MetaTrader5 as mt5
    print("[OK] MetaTrader5 package imported")
except ImportError as e:
    print(f"[FAIL] Cannot import MetaTrader5: {e}")
    sys.exit(1)

# Step 2 — initialize (no login yet, just find the terminal)
print("\nAttempting mt5.initialize() ...")
if mt5.initialize():
    info = mt5.terminal_info()
    print(f"[OK] Terminal found!")
    print(f"     Path    : {info.path}")
    print(f"     Connected: {info.connected}")
    print(f"     Trade allowed: {info.trade_allowed}")
else:
    err = mt5.last_error()
    print(f"[FAIL] mt5.initialize() failed: {err}")
    print()
    print("Possible causes:")
    print("  1. MetaTrader 5 desktop app is not running — open it and log in first")
    print("  2. MT5 is installed in a non-standard folder")
    print()

    # Try with explicit path
    import glob, os
    candidates = (
        glob.glob(r"C:\Program Files\MetaTrader 5\terminal64.exe") +
        glob.glob(r"C:\Program Files (x86)\MetaTrader 5\terminal64.exe") +
        glob.glob(r"C:\Users\*\AppData\Roaming\MetaQuotes\Terminal\*\terminal64.exe") +
        glob.glob(r"C:\Users\*\Desktop\*\terminal64.exe")
    )
    if candidates:
        print(f"Found MT5 terminal at: {candidates[0]}")
        print("Retrying with explicit path ...")
        mt5.shutdown()
        if mt5.initialize(path=candidates[0]):
            print(f"[OK] Connected via explicit path: {candidates[0]}")
            print("     Add this to mt5_client.py: mt5.initialize(path=r'" + candidates[0] + "')")
        else:
            print(f"[FAIL] Still failed: {mt5.last_error()}")
    else:
        print("Could not auto-detect MT5 installation path.")
        print("Make sure MetaTrader 5 is installed and currently running.")
    sys.exit(1)

# Step 3 — login
print("\nAttempting login ...")
import os
from pathlib import Path
from dotenv import load_dotenv

load_dotenv(Path(__file__).parent.parent / ".env")

login    = int(os.getenv("MT5_BRIDGE_MT5_LOGIN", "0"))
password = os.getenv("MT5_BRIDGE_MT5_PASSWORD", "")
server   = os.getenv("MT5_BRIDGE_MT5_SERVER", "")

print(f"     Login : {login}")
print(f"     Server: {server}")

if mt5.login(login=login, password=password, server=server):
    acc = mt5.account_info()
    print(f"[OK] Logged in!")
    print(f"     Account : {acc.login}")
    print(f"     Name    : {acc.name}")
    print(f"     Balance : {acc.balance} {acc.currency}")
    print(f"     Server  : {acc.server}")
else:
    err = mt5.last_error()
    print(f"[FAIL] Login failed: {err}")
    print("Check MT5_BRIDGE_MT5_LOGIN, MT5_BRIDGE_MT5_PASSWORD, MT5_BRIDGE_MT5_SERVER in .env")

mt5.shutdown()
print("\nDone.")
