"""
MetaAPI client — replaces the Windows-only MetaTrader5 Python package.

Manages the MetaAPI RPC connection lifecycle: connect, reconnect, disconnect.
The RPC connection is established once at startup and shared across all requests.
"""
from loguru import logger
from metaapi_cloud_sdk import MetaApi
from config import settings

_api        = None   # MetaApi SDK instance
_account    = None   # MetatraderAccount (needed for historical candles)
_connection = None   # RPC connection (one shared instance)


async def connect() -> bool:
    """Initialize MetaAPI and establish a synchronised RPC connection."""
    global _api, _account, _connection

    if not settings.metaapi_token:
        logger.critical("MT5_BRIDGE_METAAPI_TOKEN is not set in .env")
        return False
    if not settings.metaapi_account_id:
        logger.critical("MT5_BRIDGE_METAAPI_ACCOUNT_ID is not set in .env")
        return False

    try:
        _api     = MetaApi(token=settings.metaapi_token)
        account  = await _api.metatrader_account_api.get_account(settings.metaapi_account_id)
        _account = account

        logger.info(f"MetaAPI account state: {account.state}")

        # Deploy only if the account is fully stopped — skip if already running or deploying
        if account.state in ["CREATED", "UNDEPLOYED"]:
            logger.info("Deploying MetaAPI account — may take up to 60 s on first run...")
            try:
                await account.deploy()
            except Exception as deploy_err:
                logger.warning(f"Deploy call failed (may already be running): {deploy_err}")

        logger.info("Waiting for MetaAPI account to connect to broker...")
        await account.wait_connected(timeout_in_seconds=120)

        _connection = account.get_rpc_connection()
        await _connection.connect()
        await _connection.wait_synchronized(timeout_in_seconds=120)

        info = await _connection.get_account_information()
        logger.info(
            f"Connected via MetaAPI | Account: {info['login']} | "
            f"Balance: {info['balance']} {info['currency']}"
        )
        return True

    except Exception as e:
        logger.error(f"MetaAPI connection failed: {e}")
        return False


def get_account():
    """Return the MetatraderAccount object (needed for historical candles)."""
    return _account


async def disconnect() -> None:
    global _api, _account, _connection
    try:
        if _connection:
            await _connection.close()
        if _api:
            _api.close()
    except Exception:
        pass
    _connection = None
    _account    = None
    _api        = None
    logger.info("Disconnected from MetaAPI")


def get_connection():
    """Return the active RPC connection, or None if not connected."""
    return _connection


def is_connected() -> bool:
    return _connection is not None


async def get_account_info() -> dict:
    if not _connection:
        return {}
    try:
        info = await _connection.get_account_information()
        return {
            "login":       info.get("login"),
            "server":      info.get("server"),
            "balance":     info.get("balance"),
            "equity":      info.get("equity"),
            "margin":      info.get("margin"),
            "free_margin": info.get("freeMargin"),
            "currency":    info.get("currency"),
            "leverage":    info.get("leverage"),
        }
    except Exception as e:
        logger.warning(f"get_account_info failed: {e}")
        return {}
