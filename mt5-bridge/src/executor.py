"""
Order executor — places and closes trades via MetaAPI RPC connection.
Paper-trading mode blocks all real order submission.

SL/TP can be supplied as:
  - sl_price / tp_price  → absolute price levels (preferred, from ATR calculation)
  - sl_pips  / tp_pips   → pip distance (fallback for manual/legacy calls)
"""
from loguru import logger
from config import settings
import mt5_client

# Instruments where 1 "pip" = 0.01 (JPY pairs, Gold)
_PIP_001 = {"XAUUSD", "USDJPY", "EURJPY", "AUDJPY", "GBPJPY", "CADJPY"}
# Index instruments — 1 point = 1.0
_INDEX   = {"US100", "US500", "US30", "DE40", "UK100"}


def _pip_size(symbol: str, price: float) -> float:
    """
    Return pip size for a symbol.
    Indices use points (1.0); JPY/Gold pairs use 0.01; all others 0.0001.
    """
    sym = symbol.upper()
    if sym in _INDEX:
        return 1.0
    if sym in _PIP_001:
        return 0.01
    return 0.0001


async def open_trade(
    symbol: str,
    direction: str,
    volume: float,
    sl_pips: float = None,
    tp_pips: float = None,
    sl_price: float = None,
    tp_price: float = None,
) -> dict:
    """
    Open a market order.
    Prefers sl_price/tp_price (absolute levels from ATR).
    Falls back to sl_pips/tp_pips if price levels not provided.
    """
    if settings.paper_trading:
        sl_info = f"SL={sl_price}" if sl_price else f"SL={sl_pips}pip"
        tp_info = f"TP={tp_price}" if tp_price else f"TP={tp_pips}pip"
        logger.info(f"[PAPER] Would open {direction} {volume} {symbol} {sl_info} {tp_info}")
        # Fetch current price so the backend can record the entry price in the trade row
        entry_price = None
        conn = mt5_client.get_connection()
        if conn:
            try:
                price_info = await conn.get_symbol_price(symbol)
                entry_price = price_info["ask"] if direction.upper() == "BUY" else price_info["bid"]
            except Exception as e:
                logger.debug(f"[PAPER] Could not fetch entry price for {symbol}: {e}")
        return {"success": True, "paper": True, "price": entry_price,
                "message": f"Paper trade: {direction} {volume} {symbol}"}

    conn = mt5_client.get_connection()
    if not conn:
        return {"success": False, "message": "MetaAPI not connected"}

    try:
        price_info = await conn.get_symbol_price(symbol)

        if direction.upper() == "BUY":
            price = price_info["ask"]
            if sl_price is None:
                pip = _pip_size(symbol, price)
                sl_price = round(price - (sl_pips or 30.0) * pip, 5)
                tp_price = round(price + (tp_pips or 90.0) * pip, 5)
            result = await conn.create_market_buy_order(symbol, volume, sl_price, tp_price)
        else:
            price = price_info["bid"]
            if sl_price is None:
                pip = _pip_size(symbol, price)
                sl_price = round(price + (sl_pips or 30.0) * pip, 5)
                tp_price = round(price - (tp_pips or 90.0) * pip, 5)
            result = await conn.create_market_sell_order(symbol, volume, sl_price, tp_price)

        order_id = result.get("orderId") or result.get("positionId")
        logger.info(f"Order placed: #{order_id} {direction} {volume} {symbol} @ {price} "
                    f"SL={sl_price} TP={tp_price}")
        return {"success": True, "order_id": order_id, "price": price, "message": "OK"}

    except Exception as e:
        logger.error(f"open_trade failed for {symbol}: {e}")
        return {"success": False, "message": str(e)}


async def close_trade(ticket: int) -> dict:
    """Close an open position by ticket/position ID."""
    if settings.paper_trading:
        logger.info(f"[PAPER] Would close position #{ticket}")
        return {"success": True, "paper": True, "message": f"Paper close: #{ticket}"}

    conn = mt5_client.get_connection()
    if not conn:
        return {"success": False, "message": "MetaAPI not connected"}

    try:
        result = await conn.close_position(str(ticket))
        return {"success": True, "order_id": result.get("orderId"), "message": "Closed"}
    except Exception as e:
        logger.error(f"close_trade({ticket}) failed: {e}")
        return {"success": False, "message": str(e)}


async def get_open_positions() -> list:
    """Return all open positions."""
    conn = mt5_client.get_connection()
    if not conn:
        return []
    try:
        positions = await conn.get_positions()
        result = []
        for p in (positions or []):
            raw_type  = p.get("type", "")
            direction = "BUY" if "BUY" in raw_type.upper() else "SELL"
            result.append({
                "ticket":        p.get("id"),
                "symbol":        p.get("symbol"),
                "type":          direction,
                "volume":        p.get("volume"),
                "open_price":    p.get("openPrice"),
                "current_price": p.get("currentPrice"),
                "sl":            p.get("stopLoss"),
                "tp":            p.get("takeProfit"),
                "profit":        p.get("profit"),
                "open_time":     p.get("time"),
            })
        return result
    except Exception as e:
        logger.warning(f"get_open_positions failed: {e}")
        return []
