"""
MT5 client wrapper — handles connection lifecycle and login.
Auto-reconnects when the terminal loses its broker connection or the IPC pipe breaks.
"""
import MetaTrader5 as mt5
from loguru import logger
from config import settings

# MT5 error code for broken IPC pipe between Python library and terminal
IPC_SEND_FAILED = -10001


def connect() -> bool:
    """Initialize and log in to MetaTrader 5."""
    if not mt5.initialize(path=settings.mt5_path):
        logger.error(f"MT5 initialize() failed: {mt5.last_error()}")
        return False

    authorized = mt5.login(
        login=int(settings.mt5_login),
        password=settings.mt5_password,
        server=settings.mt5_server,
    )
    if not authorized:
        logger.error(f"MT5 login failed: {mt5.last_error()}")
        mt5.shutdown()
        return False

    info = mt5.account_info()
    logger.info(
        f"Connected to MT5 | Account: {info.login} | "
        f"Server: {info.server} | Balance: {info.balance} {info.currency}"
    )
    return True


def disconnect() -> None:
    mt5.shutdown()
    logger.info("Disconnected from MT5")


def is_connected() -> bool:
    """True only if the terminal is running AND account info is readable."""
    return mt5.terminal_info() is not None and mt5.account_info() is not None


def try_reconnect() -> bool:
    """
    Attempt a single reconnect cycle (shutdown → initialize → login).
    Called automatically on IPC errors or missing account info.
    Returns True if reconnect succeeded.
    """
    logger.warning("MT5 connection lost — attempting reconnect...")
    try:
        mt5.shutdown()
    except Exception:
        pass
    ok = connect()
    if ok:
        logger.info("MT5 reconnected successfully.")
    else:
        logger.error("MT5 reconnect failed — will retry on next request.")
    return ok


def get_account_info() -> dict:
    info = mt5.account_info()

    if info is None:
        if try_reconnect():
            info = mt5.account_info()

    if info is None:
        return {}

    return {
        "login":       info.login,
        "server":      info.server,
        "balance":     info.balance,
        "equity":      info.equity,
        "margin":      info.margin,
        "free_margin": info.margin_free,
        "currency":    info.currency,
        "leverage":    info.leverage,
    }
