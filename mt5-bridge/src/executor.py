"""
Order executor — places, modifies, and closes trades via MT5.
Paper-trading mode blocks all real order submission.
"""
import MetaTrader5 as mt5
from loguru import logger
from config import settings
from typing import Optional


def _build_request(
    symbol: str,
    order_type: int,
    volume: float,
    price: float,
    sl: float,
    tp: float,
    comment: str = "forex-ai-bot",
) -> dict:
    return {
        "action": mt5.TRADE_ACTION_DEAL,
        "symbol": symbol,
        "volume": volume,
        "type": order_type,
        "price": price,
        "sl": sl,
        "tp": tp,
        "deviation": 10,
        "magic": 20240101,
        "comment": comment,
        "type_time": mt5.ORDER_TIME_GTC,
        "type_filling": mt5.ORDER_FILLING_IOC,
    }


def open_trade(
    symbol: str,
    direction: str,        # "BUY" or "SELL"
    volume: float,
    sl_pips: float = 30.0,
    tp_pips: float = 60.0,
) -> dict:
    """
    Open a market order.
    Returns a result dict with `success`, `order_id`, and `message`.
    """
    if settings.paper_trading:
        logger.info(f"[PAPER] Would open {direction} {volume} {symbol} SL={sl_pips}pip TP={tp_pips}pip")
        return {"success": True, "paper": True, "message": f"Paper trade: {direction} {volume} {symbol}"}

    tick = mt5.symbol_info_tick(symbol)
    info = mt5.symbol_info(symbol)
    if tick is None or info is None:
        return {"success": False, "message": f"Could not get tick/info for {symbol}"}

    point = info.point
    if direction.upper() == "BUY":
        price = tick.ask
        sl = price - sl_pips * point * 10
        tp = price + tp_pips * point * 10
        order_type = mt5.ORDER_TYPE_BUY
    else:
        price = tick.bid
        sl = price + sl_pips * point * 10
        tp = price - tp_pips * point * 10
        order_type = mt5.ORDER_TYPE_SELL

    request = _build_request(symbol, order_type, volume, price, sl, tp)
    result = mt5.order_send(request)

    if result.retcode != mt5.TRADE_RETCODE_DONE:
        logger.error(f"Order failed [{result.retcode}]: {result.comment}")
        return {"success": False, "message": result.comment, "retcode": result.retcode}

    logger.info(f"Order placed: #{result.order} {direction} {volume} {symbol} @ {price}")
    return {"success": True, "order_id": result.order, "price": price, "message": "OK"}


def close_trade(ticket: int) -> dict:
    """Close an open position by ticket."""
    if settings.paper_trading:
        logger.info(f"[PAPER] Would close position #{ticket}")
        return {"success": True, "paper": True, "message": f"Paper close: #{ticket}"}

    position = None
    positions = mt5.positions_get()
    if positions:
        for p in positions:
            if p.ticket == ticket:
                position = p
                break

    if position is None:
        return {"success": False, "message": f"Position #{ticket} not found"}

    symbol = position.symbol
    volume = position.volume
    order_type = mt5.ORDER_TYPE_SELL if position.type == mt5.ORDER_TYPE_BUY else mt5.ORDER_TYPE_BUY
    tick = mt5.symbol_info_tick(symbol)
    price = tick.bid if order_type == mt5.ORDER_TYPE_SELL else tick.ask

    request = {
        "action": mt5.TRADE_ACTION_DEAL,
        "symbol": symbol,
        "volume": volume,
        "type": order_type,
        "position": ticket,
        "price": price,
        "deviation": 10,
        "magic": 20240101,
        "comment": "close by forex-ai-bot",
        "type_time": mt5.ORDER_TIME_GTC,
        "type_filling": mt5.ORDER_FILLING_IOC,
    }
    result = mt5.order_send(request)

    if result.retcode != mt5.TRADE_RETCODE_DONE:
        return {"success": False, "message": result.comment, "retcode": result.retcode}

    return {"success": True, "order_id": result.order, "message": "Closed"}


def get_open_positions() -> list:
    """Return all open positions."""
    positions = mt5.positions_get()
    if not positions:
        return []
    result = []
    for p in positions:
        result.append({
            "ticket": p.ticket,
            "symbol": p.symbol,
            "type": "BUY" if p.type == mt5.ORDER_TYPE_BUY else "SELL",
            "volume": p.volume,
            "open_price": p.price_open,
            "current_price": p.price_current,
            "sl": p.sl,
            "tp": p.tp,
            "profit": p.profit,
            "open_time": p.time,
        })
    return result
