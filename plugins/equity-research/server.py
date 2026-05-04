"""
Equity research MCP server.
Provides public equity valuation, earnings modeling, and stock analysis tools.
Requires: financial-analysis@financial-services-plugins
"""

import json
import math
import sys
from typing import Any


def earnings_model(
    base_revenue: float,
    revenue_growth_rates: list[float],
    ebitda_margins: list[float],
    da_rates: list[float],
    interest_expense: list[float],
    tax_rate: float,
    shares_outstanding: float,
) -> dict[str, Any]:
    """Multi-year income statement model projecting EPS."""
    years = min(len(revenue_growth_rates), len(ebitda_margins), len(da_rates), len(interest_expense))
    projections = []
    revenue = base_revenue
    for i in range(years):
        revenue = revenue * (1 + revenue_growth_rates[i])
        ebitda = revenue * ebitda_margins[i]
        da = revenue * da_rates[i]
        ebit = ebitda - da
        ebt = ebit - interest_expense[i]
        net_income = ebt * (1 - tax_rate)
        eps = net_income / shares_outstanding if shares_outstanding else 0
        projections.append({
            "year": i + 1,
            "revenue": round(revenue, 2),
            "revenue_growth_pct": round(revenue_growth_rates[i] * 100, 1),
            "ebitda": round(ebitda, 2),
            "ebitda_margin_pct": round(ebitda_margins[i] * 100, 1),
            "ebit": round(ebit, 2),
            "ebt": round(ebt, 2),
            "net_income": round(net_income, 2),
            "eps": round(eps, 4),
        })
    return {"projections": projections, "shares_outstanding": shares_outstanding}


def price_target(
    forward_eps: float,
    target_pe: float,
    current_price: float,
    dividend_per_share: float = 0.0,
    holding_period_years: float = 1.0,
) -> dict[str, Any]:
    """Calculate price target and implied return."""
    target_price = forward_eps * target_pe + dividend_per_share
    total_return_pct = (target_price - current_price) / current_price * 100 if current_price else 0
    annualized_return_pct = ((target_price / current_price) ** (1 / holding_period_years) - 1) * 100 if current_price and holding_period_years else total_return_pct
    upside_downside = target_price - current_price

    return {
        "target_price": round(target_price, 2),
        "current_price": round(current_price, 2),
        "upside_downside": round(upside_downside, 2),
        "total_return_pct": round(total_return_pct, 2),
        "annualized_return_pct": round(annualized_return_pct, 2),
        "rating": "BUY" if total_return_pct > 10 else ("SELL" if total_return_pct < -10 else "HOLD"),
        "inputs": {
            "forward_eps": forward_eps,
            "target_pe": target_pe,
            "dividend_per_share": dividend_per_share,
        },
    }


def relative_valuation(
    metric_value: float,
    metric_name: str,
    peer_multiples: list[float],
    net_debt: float = 0.0,
    shares_outstanding: float = 1.0,
) -> dict[str, Any]:
    """Relative valuation using peer group multiples."""
    sorted_m = sorted(peer_multiples)
    n = len(sorted_m)
    mean = sum(sorted_m) / n
    median = sorted_m[n // 2] if n % 2 else (sorted_m[n // 2 - 1] + sorted_m[n // 2]) / 2
    p25 = sorted_m[n // 4]
    p75 = sorted_m[3 * n // 4]

    def implied_price(multiple: float) -> float:
        ev = metric_value * multiple
        eq = ev - net_debt
        return round(eq / shares_outstanding, 2) if shares_outstanding else round(eq, 2)

    return {
        "metric": metric_name,
        "metric_value": metric_value,
        "peer_multiple_stats": {
            "min": round(sorted_m[0], 2),
            "25th_pct": round(p25, 2),
            "median": round(median, 2),
            "mean": round(mean, 2),
            "75th_pct": round(p75, 2),
            "max": round(sorted_m[-1], 2),
        },
        "implied_price_per_share": {
            "at_min": implied_price(sorted_m[0]),
            "at_25th": implied_price(p25),
            "at_median": implied_price(median),
            "at_mean": implied_price(mean),
            "at_75th": implied_price(p75),
            "at_max": implied_price(sorted_m[-1]),
        },
    }


def eps_forecast(
    historical_eps: list[float],
    periods_to_forecast: int,
    growth_assumption: str = "linear",
    override_growth_rate: float = None,
) -> dict[str, Any]:
    """EPS forecast using historical trend or override growth rate."""
    if not historical_eps:
        raise ValueError("historical_eps must not be empty")

    if override_growth_rate is not None:
        growth = override_growth_rate
    elif len(historical_eps) >= 2:
        cagr = (historical_eps[-1] / historical_eps[0]) ** (1 / (len(historical_eps) - 1)) - 1
        growth = cagr
    else:
        growth = 0.0

    forecasted = []
    last = historical_eps[-1]
    for i in range(1, periods_to_forecast + 1):
        last = last * (1 + growth)
        forecasted.append(round(last, 4))

    return {
        "historical_eps": historical_eps,
        "implied_historical_cagr_pct": round(growth * 100, 2),
        "forecast_growth_rate_pct": round(growth * 100, 2),
        "forecasted_eps": forecasted,
    }


def analyst_consensus(
    price_targets: list[float],
    ratings: list[str],
    current_price: float,
) -> dict[str, Any]:
    """Summarize analyst price targets and ratings."""
    if not price_targets:
        raise ValueError("price_targets must not be empty")

    sorted_pts = sorted(price_targets)
    n = len(sorted_pts)
    mean_pt = sum(sorted_pts) / n
    median_pt = sorted_pts[n // 2] if n % 2 else (sorted_pts[n // 2 - 1] + sorted_pts[n // 2]) / 2

    rating_counts: dict[str, int] = {}
    for r in ratings:
        normalized = r.strip().upper()
        rating_counts[normalized] = rating_counts.get(normalized, 0) + 1

    buy_count = sum(v for k, v in rating_counts.items() if k in ("BUY", "OUTPERFORM", "OVERWEIGHT"))
    hold_count = sum(v for k, v in rating_counts.items() if k in ("HOLD", "NEUTRAL", "MARKET PERFORM", "EQUAL WEIGHT"))
    sell_count = sum(v for k, v in rating_counts.items() if k in ("SELL", "UNDERPERFORM", "UNDERWEIGHT"))
    total = len(ratings)

    upside_to_mean = (mean_pt - current_price) / current_price * 100 if current_price else 0

    return {
        "current_price": current_price,
        "price_target_stats": {
            "low": round(sorted_pts[0], 2),
            "median": round(median_pt, 2),
            "mean": round(mean_pt, 2),
            "high": round(sorted_pts[-1], 2),
        },
        "upside_to_mean_pt_pct": round(upside_to_mean, 2),
        "rating_distribution": {
            "buy": buy_count,
            "hold": hold_count,
            "sell": sell_count,
            "total": total,
            "buy_pct": round(buy_count / total * 100, 1) if total else 0,
        },
        "consensus_rating": "BUY" if buy_count > hold_count and buy_count > sell_count
                            else ("SELL" if sell_count > buy_count and sell_count > hold_count else "HOLD"),
    }


TOOLS = {
    "earnings_model": earnings_model,
    "price_target": price_target,
    "relative_valuation": relative_valuation,
    "eps_forecast": eps_forecast,
    "analyst_consensus": analyst_consensus,
}

TOOL_SCHEMAS = {
    "earnings_model": {
        "name": "earnings_model",
        "description": "Project multi-year income statement and EPS from revenue growth and margin assumptions.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "base_revenue": {"type": "number"},
                "revenue_growth_rates": {"type": "array", "items": {"type": "number"}},
                "ebitda_margins": {"type": "array", "items": {"type": "number"}},
                "da_rates": {"type": "array", "items": {"type": "number"}},
                "interest_expense": {"type": "array", "items": {"type": "number"}},
                "tax_rate": {"type": "number"},
                "shares_outstanding": {"type": "number"},
            },
            "required": ["base_revenue", "revenue_growth_rates", "ebitda_margins", "da_rates", "interest_expense", "tax_rate", "shares_outstanding"],
        },
    },
    "price_target": {
        "name": "price_target",
        "description": "Calculate analyst price target and implied total return from forward EPS and target P/E.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "forward_eps": {"type": "number"},
                "target_pe": {"type": "number"},
                "current_price": {"type": "number"},
                "dividend_per_share": {"type": "number", "default": 0},
                "holding_period_years": {"type": "number", "default": 1},
            },
            "required": ["forward_eps", "target_pe", "current_price"],
        },
    },
    "relative_valuation": {
        "name": "relative_valuation",
        "description": "Derive implied price range from peer group multiples applied to a financial metric.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "metric_value": {"type": "number"},
                "metric_name": {"type": "string"},
                "peer_multiples": {"type": "array", "items": {"type": "number"}},
                "net_debt": {"type": "number", "default": 0},
                "shares_outstanding": {"type": "number", "default": 1},
            },
            "required": ["metric_value", "metric_name", "peer_multiples"],
        },
    },
    "eps_forecast": {
        "name": "eps_forecast",
        "description": "Forecast future EPS periods from historical trend or an override growth rate.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "historical_eps": {"type": "array", "items": {"type": "number"}},
                "periods_to_forecast": {"type": "integer"},
                "growth_assumption": {"type": "string", "enum": ["linear"], "default": "linear"},
                "override_growth_rate": {"type": "number"},
            },
            "required": ["historical_eps", "periods_to_forecast"],
        },
    },
    "analyst_consensus": {
        "name": "analyst_consensus",
        "description": "Summarize sell-side analyst price targets and ratings into a consensus view.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "price_targets": {"type": "array", "items": {"type": "number"}},
                "ratings": {"type": "array", "items": {"type": "string"}},
                "current_price": {"type": "number"},
            },
            "required": ["price_targets", "ratings", "current_price"],
        },
    },
}


def handle_request(request: dict) -> dict:
    method = request.get("method")
    req_id = request.get("id")

    if method == "initialize":
        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "result": {
                "protocolVersion": "2024-11-05",
                "capabilities": {"tools": {}},
                "serverInfo": {"name": "equity-research", "version": "1.0.0"},
            },
        }

    if method == "tools/list":
        return {"jsonrpc": "2.0", "id": req_id, "result": {"tools": list(TOOL_SCHEMAS.values())}}

    if method == "tools/call":
        params = request.get("params", {})
        tool_name = params.get("name")
        arguments = params.get("arguments", {})
        if tool_name not in TOOLS:
            return {"jsonrpc": "2.0", "id": req_id, "error": {"code": -32601, "message": f"Unknown tool: {tool_name}"}}
        try:
            result = TOOLS[tool_name](**arguments)
            return {"jsonrpc": "2.0", "id": req_id, "result": {"content": [{"type": "text", "text": json.dumps(result, indent=2)}]}}
        except Exception as exc:
            return {"jsonrpc": "2.0", "id": req_id, "error": {"code": -32603, "message": str(exc)}}

    return {"jsonrpc": "2.0", "id": req_id, "error": {"code": -32601, "message": f"Method not found: {method}"}}


if __name__ == "__main__":
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
        except json.JSONDecodeError:
            continue
        response = handle_request(request)
        print(json.dumps(response), flush=True)
