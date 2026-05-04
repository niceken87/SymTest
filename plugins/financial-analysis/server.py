"""
Core financial analysis MCP server.
Required by all other financial-services plugins.
"""

import json
import sys
from typing import Any


def dcf_valuation(
    free_cash_flows: list[float],
    wacc: float,
    terminal_growth_rate: float,
    net_debt: float = 0.0,
    shares_outstanding: float = 1.0,
) -> dict[str, Any]:
    """Discounted Cash Flow valuation."""
    if wacc <= terminal_growth_rate:
        raise ValueError("WACC must exceed terminal growth rate")

    pv_fcfs = []
    for i, fcf in enumerate(free_cash_flows, start=1):
        pv_fcfs.append(fcf / (1 + wacc) ** i)

    terminal_value = free_cash_flows[-1] * (1 + terminal_growth_rate) / (wacc - terminal_growth_rate)
    pv_terminal = terminal_value / (1 + wacc) ** len(free_cash_flows)

    enterprise_value = sum(pv_fcfs) + pv_terminal
    equity_value = enterprise_value - net_debt
    price_per_share = equity_value / shares_outstanding if shares_outstanding else equity_value

    return {
        "pv_free_cash_flows": round(sum(pv_fcfs), 2),
        "terminal_value": round(terminal_value, 2),
        "pv_terminal_value": round(pv_terminal, 2),
        "enterprise_value": round(enterprise_value, 2),
        "equity_value": round(equity_value, 2),
        "price_per_share": round(price_per_share, 2),
        "terminal_value_pct": round(pv_terminal / enterprise_value * 100, 1),
    }


def calculate_wacc(
    equity_weight: float,
    cost_of_equity: float,
    debt_weight: float,
    cost_of_debt: float,
    tax_rate: float,
) -> dict[str, Any]:
    """Weighted Average Cost of Capital."""
    after_tax_cost_of_debt = cost_of_debt * (1 - tax_rate)
    wacc = equity_weight * cost_of_equity + debt_weight * after_tax_cost_of_debt
    return {
        "wacc": round(wacc * 100, 4),
        "after_tax_cost_of_debt": round(after_tax_cost_of_debt * 100, 4),
        "equity_component": round(equity_weight * cost_of_equity * 100, 4),
        "debt_component": round(debt_weight * after_tax_cost_of_debt * 100, 4),
    }


def financial_ratios(
    revenue: float,
    ebitda: float,
    ebit: float,
    net_income: float,
    total_assets: float,
    total_equity: float,
    total_debt: float,
    current_assets: float,
    current_liabilities: float,
    market_cap: float = 0.0,
    shares_outstanding: float = 0.0,
    eps: float = 0.0,
) -> dict[str, Any]:
    """Calculate key financial ratios."""
    ratios: dict[str, Any] = {}

    ratios["ebitda_margin"] = round(ebitda / revenue * 100, 2) if revenue else None
    ratios["ebit_margin"] = round(ebit / revenue * 100, 2) if revenue else None
    ratios["net_margin"] = round(net_income / revenue * 100, 2) if revenue else None

    ratios["roa"] = round(net_income / total_assets * 100, 2) if total_assets else None
    ratios["roe"] = round(net_income / total_equity * 100, 2) if total_equity else None

    ratios["current_ratio"] = round(current_assets / current_liabilities, 2) if current_liabilities else None
    ratios["debt_to_equity"] = round(total_debt / total_equity, 2) if total_equity else None
    ratios["net_debt_to_ebitda"] = round((total_debt - current_assets) / ebitda, 2) if ebitda else None

    if market_cap and eps:
        ratios["pe_ratio"] = round(market_cap / shares_outstanding / eps, 2) if shares_outstanding else None
    if market_cap and ebitda:
        enterprise_value = market_cap + total_debt
        ratios["ev_to_ebitda"] = round(enterprise_value / ebitda, 2)
        ratios["ev_to_revenue"] = round(enterprise_value / revenue, 2) if revenue else None

    return ratios


def working_capital_analysis(
    accounts_receivable: float,
    inventory: float,
    accounts_payable: float,
    revenue: float,
    cogs: float,
) -> dict[str, Any]:
    """Working capital and cash conversion cycle analysis."""
    days_sales_outstanding = (accounts_receivable / revenue * 365) if revenue else 0
    days_inventory_outstanding = (inventory / cogs * 365) if cogs else 0
    days_payable_outstanding = (accounts_payable / cogs * 365) if cogs else 0
    cash_conversion_cycle = days_sales_outstanding + days_inventory_outstanding - days_payable_outstanding
    net_working_capital = accounts_receivable + inventory - accounts_payable

    return {
        "net_working_capital": round(net_working_capital, 2),
        "days_sales_outstanding": round(days_sales_outstanding, 1),
        "days_inventory_outstanding": round(days_inventory_outstanding, 1),
        "days_payable_outstanding": round(days_payable_outstanding, 1),
        "cash_conversion_cycle": round(cash_conversion_cycle, 1),
    }


def sensitivity_analysis(
    base_value: float,
    variable_1_range: list[float],
    variable_2_range: list[float],
    variable_1_name: str,
    variable_2_name: str,
    base_variable_1: float,
    base_variable_2: float,
) -> dict[str, Any]:
    """Two-variable sensitivity table around a base DCF or valuation."""
    table = {}
    for v1 in variable_1_range:
        row = {}
        for v2 in variable_2_range:
            delta_1 = (v1 - base_variable_1) / base_variable_1 if base_variable_1 else 0
            delta_2 = (v2 - base_variable_2) / base_variable_2 if base_variable_2 else 0
            row[str(round(v2, 4))] = round(base_value * (1 + delta_1) * (1 + delta_2), 2)
        table[str(round(v1, 4))] = row

    return {
        "variable_1": variable_1_name,
        "variable_2": variable_2_name,
        "table": table,
    }


TOOLS = {
    "dcf_valuation": dcf_valuation,
    "calculate_wacc": calculate_wacc,
    "financial_ratios": financial_ratios,
    "working_capital_analysis": working_capital_analysis,
    "sensitivity_analysis": sensitivity_analysis,
}

TOOL_SCHEMAS = {
    "dcf_valuation": {
        "name": "dcf_valuation",
        "description": "Perform a Discounted Cash Flow (DCF) valuation given projected free cash flows.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "free_cash_flows": {"type": "array", "items": {"type": "number"}, "description": "Projected FCFs for each forecast year"},
                "wacc": {"type": "number", "description": "Weighted average cost of capital (decimal, e.g. 0.10 for 10%)"},
                "terminal_growth_rate": {"type": "number", "description": "Perpetuity growth rate (decimal)"},
                "net_debt": {"type": "number", "description": "Net debt to subtract from enterprise value", "default": 0},
                "shares_outstanding": {"type": "number", "description": "Shares outstanding for per-share calculation", "default": 1},
            },
            "required": ["free_cash_flows", "wacc", "terminal_growth_rate"],
        },
    },
    "calculate_wacc": {
        "name": "calculate_wacc",
        "description": "Calculate the Weighted Average Cost of Capital (WACC).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "equity_weight": {"type": "number", "description": "Equity as a fraction of total capital (e.g. 0.6)"},
                "cost_of_equity": {"type": "number", "description": "Cost of equity (decimal)"},
                "debt_weight": {"type": "number", "description": "Debt as a fraction of total capital (e.g. 0.4)"},
                "cost_of_debt": {"type": "number", "description": "Pre-tax cost of debt (decimal)"},
                "tax_rate": {"type": "number", "description": "Effective corporate tax rate (decimal)"},
            },
            "required": ["equity_weight", "cost_of_equity", "debt_weight", "cost_of_debt", "tax_rate"],
        },
    },
    "financial_ratios": {
        "name": "financial_ratios",
        "description": "Compute a comprehensive set of profitability, leverage, and valuation ratios.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "revenue": {"type": "number"},
                "ebitda": {"type": "number"},
                "ebit": {"type": "number"},
                "net_income": {"type": "number"},
                "total_assets": {"type": "number"},
                "total_equity": {"type": "number"},
                "total_debt": {"type": "number"},
                "current_assets": {"type": "number"},
                "current_liabilities": {"type": "number"},
                "market_cap": {"type": "number", "default": 0},
                "shares_outstanding": {"type": "number", "default": 0},
                "eps": {"type": "number", "default": 0},
            },
            "required": ["revenue", "ebitda", "ebit", "net_income", "total_assets", "total_equity", "total_debt", "current_assets", "current_liabilities"],
        },
    },
    "working_capital_analysis": {
        "name": "working_capital_analysis",
        "description": "Calculate net working capital and the cash conversion cycle.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "accounts_receivable": {"type": "number"},
                "inventory": {"type": "number"},
                "accounts_payable": {"type": "number"},
                "revenue": {"type": "number"},
                "cogs": {"type": "number"},
            },
            "required": ["accounts_receivable", "inventory", "accounts_payable", "revenue", "cogs"],
        },
    },
    "sensitivity_analysis": {
        "name": "sensitivity_analysis",
        "description": "Build a two-variable sensitivity table around a base valuation.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "base_value": {"type": "number", "description": "Base case valuation or price"},
                "variable_1_range": {"type": "array", "items": {"type": "number"}},
                "variable_2_range": {"type": "array", "items": {"type": "number"}},
                "variable_1_name": {"type": "string"},
                "variable_2_name": {"type": "string"},
                "base_variable_1": {"type": "number"},
                "base_variable_2": {"type": "number"},
            },
            "required": ["base_value", "variable_1_range", "variable_2_range", "variable_1_name", "variable_2_name", "base_variable_1", "base_variable_2"],
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
                "serverInfo": {"name": "financial-analysis", "version": "1.0.0"},
            },
        }

    if method == "tools/list":
        return {
            "jsonrpc": "2.0",
            "id": req_id,
            "result": {"tools": list(TOOL_SCHEMAS.values())},
        }

    if method == "tools/call":
        params = request.get("params", {})
        tool_name = params.get("name")
        arguments = params.get("arguments", {})
        if tool_name not in TOOLS:
            return {"jsonrpc": "2.0", "id": req_id, "error": {"code": -32601, "message": f"Unknown tool: {tool_name}"}}
        try:
            result = TOOLS[tool_name](**arguments)
            return {
                "jsonrpc": "2.0",
                "id": req_id,
                "result": {"content": [{"type": "text", "text": json.dumps(result, indent=2)}]},
            }
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
