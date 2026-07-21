"""
Order executor — places and closes trades via MetaAPI RPC connection.
Paper-trading mode blocks all real order submission.
"""
from loguru import logger
from config import settings
import mt5_client


def _pip_size(price: float) -> float:
    """
    Return the pip size for a symbol based on its current price.
    JPY pairs trade around 100–160 → pip = 0.01
    All other major pairs trade below 10  → pip = 0.0001
    """
    return 0.01 if price > 20 else 0.0001


async def open_trade(
    symbol: str,
    direction: str,
    volume: float,
    sl_pips: float = 30.0,
    tp_pips: float = 60.0,
) -> dict:
    """
    Open a market order.
    SL/TP are given in pips and converted to absolute prices internally.
    Returns a result dict with `success`, `order_id`, and `message`.
    """
    if settings.paper_trading:
        logger.info(f"[PAPER] Would open {direction} {volume} {symbol} SL={sl_pips}pip TP={tp_pips}pip")
        return {"success": True, "paper": True, "message": f"Paper trade: {direction} {volume} {symbol}"}

    conn = mt5_client.get_connection()
    if not conn:
        return {"success": False, "message": "MetaAPI not connected"}

    try:
        price_info = await conn.get_symbol_price(symbol)
        pip = _pip_size(price_info.get("ask", 1.0))

        if direction.upper() == "BUY":
            price = price_info["ask"]
            sl    = round(price - sl_pips * pip, 5)
            tp    = round(price + tp_pips * pip, 5)
            result = await conn.create_market_buy_order(symbol, volume, sl, tp)
        else:
            price = price_info["bid"]
            sl    = round(price + sl_pips * pip, 5)
            tp    = round(price - tp_pips * pip, 5)
            result = await conn.create_market_sell_order(symbol, volume, sl, tp)

        order_id = result.get("orderId") or result.get("positionId")
        logger.info(f"Order placed: #{order_id} {direction} {volume} {symbol} @ {price}")
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
            # MetaAPI returns type as "POSITION_TYPE_BUY" / "POSITION_TYPE_SELL"
            raw_type = p.get("type", "")
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
