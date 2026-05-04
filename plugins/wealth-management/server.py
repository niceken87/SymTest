"""
Wealth management MCP server.
Provides portfolio construction, asset allocation, and risk management tools.
Requires: financial-analysis@financial-services-plugins
"""

import json
import math
import sys
from typing import Any


def asset_allocation(
    portfolio_value: float,
    target_allocations: list[dict],
    current_allocations: list[dict] = None,
    risk_tolerance: str = "moderate",
) -> dict[str, Any]:
    """
    Compute target dollar allocations and drift from current positions.
    Each allocation dict: {"asset_class": str, "target_pct": float}
    Current allocation dict: {"asset_class": str, "current_value": float}
    """
    total_target_pct = sum(a.get("target_pct", 0) for a in target_allocations)
    if abs(total_target_pct - 100) > 0.01:
        raise ValueError(f"Target allocations must sum to 100%, got {total_target_pct}%")

    current_map: dict[str, float] = {}
    if current_allocations:
        current_map = {c["asset_class"]: c.get("current_value", 0) for c in current_allocations}

    allocation_details = []
    for alloc in target_allocations:
        asset = alloc["asset_class"]
        target_pct = alloc["target_pct"]
        target_value = portfolio_value * target_pct / 100
        current_value = current_map.get(asset, 0)
        drift = target_value - current_value
        drift_pct = (current_value / portfolio_value * 100) if portfolio_value else 0

        allocation_details.append({
            "asset_class": asset,
            "target_pct": round(target_pct, 2),
            "target_value": round(target_value, 2),
            "current_value": round(current_value, 2),
            "current_pct": round(drift_pct, 2),
            "drift": round(drift, 2),
            "action": "BUY" if drift > 0 else ("SELL" if drift < 0 else "HOLD"),
        })

    return {
        "portfolio_value": portfolio_value,
        "risk_tolerance": risk_tolerance,
        "allocations": allocation_details,
        "rebalancing_needed": any(abs(a["drift"]) / portfolio_value * 100 > 5 for a in allocation_details if portfolio_value),
    }


def portfolio_risk(
    weights: list[float],
    expected_returns: list[float],
    covariance_matrix: list[list[float]],
    asset_names: list[str] = None,
    risk_free_rate: float = 0.05,
) -> dict[str, Any]:
    """Portfolio expected return, variance, volatility, and Sharpe ratio."""
    n = len(weights)
    if sum(weights) == 0:
        raise ValueError("Weights must not all be zero")
    w = [wi / sum(weights) for wi in weights]

    portfolio_return = sum(w[i] * expected_returns[i] for i in range(n))

    portfolio_variance = sum(
        w[i] * w[j] * covariance_matrix[i][j]
        for i in range(n) for j in range(n)
    )
    portfolio_vol = math.sqrt(max(0, portfolio_variance))
    sharpe = (portfolio_return - risk_free_rate) / portfolio_vol if portfolio_vol else 0

    marginal_contributions = []
    for i in range(n):
        mc = sum(w[j] * covariance_matrix[i][j] for j in range(n)) / portfolio_vol if portfolio_vol else 0
        risk_contribution = w[i] * mc / portfolio_vol if portfolio_vol else 0
        marginal_contributions.append({
            "asset": asset_names[i] if asset_names else f"asset_{i}",
            "weight": round(w[i], 4),
            "expected_return": round(expected_returns[i] * 100, 2),
            "risk_contribution_pct": round(risk_contribution * 100, 2),
        })

    return {
        "portfolio_expected_return_pct": round(portfolio_return * 100, 4),
        "portfolio_volatility_pct": round(portfolio_vol * 100, 4),
        "portfolio_variance": round(portfolio_variance, 6),
        "sharpe_ratio": round(sharpe, 4),
        "risk_free_rate_pct": round(risk_free_rate * 100, 2),
        "asset_contributions": marginal_contributions,
    }


def sharpe_ratio(
    portfolio_returns: list[float],
    risk_free_rate: float = 0.05,
    annualization_factor: int = 252,
) -> dict[str, Any]:
    """Sharpe ratio from a return series (daily or monthly)."""
    n = len(portfolio_returns)
    if n < 2:
        raise ValueError("Need at least 2 return observations")

    mean_r = sum(portfolio_returns) / n
    variance = sum((r - mean_r) ** 2 for r in portfolio_returns) / (n - 1)
    std_dev = math.sqrt(variance)

    annualized_return = mean_r * annualization_factor
    annualized_vol = std_dev * math.sqrt(annualization_factor)
    annualized_sharpe = (annualized_return - risk_free_rate) / annualized_vol if annualized_vol else 0

    sorted_r = sorted(portfolio_returns)
    var_5 = sorted_r[max(0, int(n * 0.05))]
    cvar_5 = sum(r for r in sorted_r if r <= var_5) / max(1, sum(1 for r in sorted_r if r <= var_5))

    return {
        "observations": n,
        "annualization_factor": annualization_factor,
        "annualized_return_pct": round(annualized_return * 100, 4),
        "annualized_volatility_pct": round(annualized_vol * 100, 4),
        "sharpe_ratio": round(annualized_sharpe, 4),
        "risk_free_rate_pct": round(risk_free_rate * 100, 2),
        "var_5pct": round(var_5 * 100, 4),
        "cvar_5pct": round(cvar_5 * 100, 4),
    }


def rebalancing_plan(
    portfolio: list[dict],
    target_allocations: list[dict],
    transaction_cost_pct: float = 0.001,
    min_trade_size: float = 100.0,
    tax_aware: bool = False,
) -> dict[str, Any]:
    """
    Generate trades to rebalance portfolio to targets.
    portfolio: [{"asset": str, "value": float, "cost_basis": float}]
    target_allocations: [{"asset": str, "target_pct": float}]
    """
    total_value = sum(p.get("value", 0) for p in portfolio)
    if total_value == 0:
        raise ValueError("Portfolio has zero value")

    current_map = {p["asset"]: p for p in portfolio}
    target_map = {t["asset"]: t["target_pct"] / 100 for t in target_allocations}

    all_assets = set(list(current_map.keys()) + list(target_map.keys()))
    trades = []
    estimated_cost = 0.0

    for asset in all_assets:
        current_val = current_map.get(asset, {}).get("value", 0)
        target_val = total_value * target_map.get(asset, 0)
        trade_amount = target_val - current_val

        if abs(trade_amount) < min_trade_size:
            continue

        cost_basis = current_map.get(asset, {}).get("cost_basis", current_val)
        gain_loss = current_val - cost_basis if tax_aware else None
        txn_cost = abs(trade_amount) * transaction_cost_pct

        trades.append({
            "asset": asset,
            "current_value": round(current_val, 2),
            "target_value": round(target_val, 2),
            "trade_amount": round(trade_amount, 2),
            "action": "BUY" if trade_amount > 0 else "SELL",
            "transaction_cost": round(txn_cost, 2),
            "unrealized_gain_loss": round(gain_loss, 2) if gain_loss is not None else None,
        })
        estimated_cost += txn_cost

    return {
        "portfolio_value": round(total_value, 2),
        "trades": trades,
        "total_estimated_transaction_cost": round(estimated_cost, 2),
        "transaction_cost_pct": transaction_cost_pct,
        "tax_aware": tax_aware,
    }


def tax_loss_harvesting(
    holdings: list[dict],
    loss_threshold: float = -500.0,
    wash_sale_days: int = 30,
) -> dict[str, Any]:
    """
    Identify candidates for tax-loss harvesting.
    holdings: [{"asset": str, "value": float, "cost_basis": float, "days_held": int}]
    """
    candidates = []
    total_harvestable_loss = 0.0

    for h in holdings:
        value = h.get("value", 0)
        cost_basis = h.get("cost_basis", value)
        gain_loss = value - cost_basis
        days_held = h.get("days_held", 0)
        is_long_term = days_held >= 365

        if gain_loss <= loss_threshold:
            candidates.append({
                "asset": h.get("asset"),
                "current_value": round(value, 2),
                "cost_basis": round(cost_basis, 2),
                "unrealized_loss": round(gain_loss, 2),
                "days_held": days_held,
                "is_long_term": is_long_term,
                "loss_type": "long-term" if is_long_term else "short-term",
                "wash_sale_rule_applies_after_days": wash_sale_days,
            })
            total_harvestable_loss += gain_loss

    candidates.sort(key=lambda x: x["unrealized_loss"])

    return {
        "total_candidates": len(candidates),
        "total_harvestable_loss": round(total_harvestable_loss, 2),
        "estimated_tax_benefit_at_37pct": round(abs(total_harvestable_loss) * 0.37, 2),
        "candidates": candidates,
        "wash_sale_warning": f"Must wait {wash_sale_days} days before repurchasing substantially identical securities.",
    }


TOOLS = {
    "asset_allocation": asset_allocation,
    "portfolio_risk": portfolio_risk,
    "sharpe_ratio": sharpe_ratio,
    "rebalancing_plan": rebalancing_plan,
    "tax_loss_harvesting": tax_loss_harvesting,
}

TOOL_SCHEMAS = {
    "asset_allocation": {
        "name": "asset_allocation",
        "description": "Compute target dollar allocations and drift from current positions.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "portfolio_value": {"type": "number"},
                "target_allocations": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {"asset_class": {"type": "string"}, "target_pct": {"type": "number"}},
                    },
                },
                "current_allocations": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {"asset_class": {"type": "string"}, "current_value": {"type": "number"}},
                    },
                },
                "risk_tolerance": {"type": "string", "enum": ["conservative", "moderate", "aggressive"], "default": "moderate"},
            },
            "required": ["portfolio_value", "target_allocations"],
        },
    },
    "portfolio_risk": {
        "name": "portfolio_risk",
        "description": "Portfolio expected return, volatility, Sharpe ratio, and risk contributions from a covariance matrix.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "weights": {"type": "array", "items": {"type": "number"}},
                "expected_returns": {"type": "array", "items": {"type": "number"}},
                "covariance_matrix": {"type": "array", "items": {"type": "array", "items": {"type": "number"}}},
                "asset_names": {"type": "array", "items": {"type": "string"}},
                "risk_free_rate": {"type": "number", "default": 0.05},
            },
            "required": ["weights", "expected_returns", "covariance_matrix"],
        },
    },
    "sharpe_ratio": {
        "name": "sharpe_ratio",
        "description": "Compute annualized Sharpe ratio, VaR, and CVaR from a return series.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "portfolio_returns": {"type": "array", "items": {"type": "number"}},
                "risk_free_rate": {"type": "number", "default": 0.05},
                "annualization_factor": {"type": "integer", "default": 252},
            },
            "required": ["portfolio_returns"],
        },
    },
    "rebalancing_plan": {
        "name": "rebalancing_plan",
        "description": "Generate a rebalancing trade list to move portfolio from current to target allocations.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "portfolio": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "asset": {"type": "string"},
                            "value": {"type": "number"},
                            "cost_basis": {"type": "number"},
                        },
                    },
                },
                "target_allocations": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {"asset": {"type": "string"}, "target_pct": {"type": "number"}},
                    },
                },
                "transaction_cost_pct": {"type": "number", "default": 0.001},
                "min_trade_size": {"type": "number", "default": 100},
                "tax_aware": {"type": "boolean", "default": False},
            },
            "required": ["portfolio", "target_allocations"],
        },
    },
    "tax_loss_harvesting": {
        "name": "tax_loss_harvesting",
        "description": "Identify holdings eligible for tax-loss harvesting and estimate the tax benefit.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "holdings": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "asset": {"type": "string"},
                            "value": {"type": "number"},
                            "cost_basis": {"type": "number"},
                            "days_held": {"type": "integer"},
                        },
                    },
                },
                "loss_threshold": {"type": "number", "default": -500},
                "wash_sale_days": {"type": "integer", "default": 30},
            },
            "required": ["holdings"],
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
                "serverInfo": {"name": "wealth-management", "version": "1.0.0"},
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
