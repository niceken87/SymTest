"""
Investment banking MCP server.
Provides M&A advisory, deal valuation, and transaction structuring tools.
Requires: financial-analysis@financial-services-plugins
"""

import json
import sys
from typing import Any


def comps_analysis(
    target_revenue: float,
    target_ebitda: float,
    target_ebit: float,
    target_net_income: float,
    comparable_ev_revenue_multiples: list[float],
    comparable_ev_ebitda_multiples: list[float],
    comparable_pe_multiples: list[float],
    net_debt: float = 0.0,
    shares_outstanding: float = 1.0,
) -> dict[str, Any]:
    """Comparable company analysis (trading comps)."""
    def stats(multiples: list[float]) -> dict:
        sorted_m = sorted(multiples)
        n = len(sorted_m)
        mean = sum(sorted_m) / n
        median = sorted_m[n // 2] if n % 2 else (sorted_m[n // 2 - 1] + sorted_m[n // 2]) / 2
        return {
            "min": round(sorted_m[0], 2),
            "25th": round(sorted_m[n // 4], 2),
            "median": round(median, 2),
            "75th": round(sorted_m[3 * n // 4], 2),
            "max": round(sorted_m[-1], 2),
            "mean": round(mean, 2),
        }

    ev_rev_stats = stats(comparable_ev_revenue_multiples)
    ev_ebitda_stats = stats(comparable_ev_ebitda_multiples)
    pe_stats = stats(comparable_pe_multiples)

    implied_ev_from_revenue = {k: round(v * target_revenue, 2) for k, v in ev_rev_stats.items()}
    implied_ev_from_ebitda = {k: round(v * target_ebitda, 2) for k, v in ev_ebitda_stats.items()}
    implied_eq_from_pe = {k: round(v * target_net_income - net_debt, 2) for k, v in pe_stats.items()}

    return {
        "multiple_statistics": {
            "ev_revenue": ev_rev_stats,
            "ev_ebitda": ev_ebitda_stats,
            "pe": pe_stats,
        },
        "implied_enterprise_value": {
            "from_ev_revenue": implied_ev_from_revenue,
            "from_ev_ebitda": implied_ev_from_ebitda,
        },
        "implied_equity_value": implied_eq_from_pe,
        "implied_price_per_share": {
            k: round(v / shares_outstanding, 2) if shares_outstanding else v
            for k, v in implied_eq_from_pe.items()
        },
    }


def precedent_transactions(
    target_ebitda: float,
    target_revenue: float,
    transaction_ev_ebitda_multiples: list[float],
    transaction_ev_revenue_multiples: list[float],
    control_premium_pct: float = 0.0,
    net_debt: float = 0.0,
    shares_outstanding: float = 1.0,
) -> dict[str, Any]:
    """Precedent transaction analysis with optional control premium."""
    def median(values: list[float]) -> float:
        s = sorted(values)
        n = len(s)
        return s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2

    median_ebitda_mult = median(transaction_ev_ebitda_multiples)
    median_rev_mult = median(transaction_ev_revenue_multiples)
    mean_ebitda_mult = sum(transaction_ev_ebitda_multiples) / len(transaction_ev_ebitda_multiples)
    mean_rev_mult = sum(transaction_ev_revenue_multiples) / len(transaction_ev_revenue_multiples)

    premium = 1 + control_premium_pct / 100

    implied_ev_ebitda = target_ebitda * median_ebitda_mult * premium
    implied_ev_revenue = target_revenue * median_rev_mult * premium

    return {
        "multiple_statistics": {
            "ev_ebitda": {"median": round(median_ebitda_mult, 2), "mean": round(mean_ebitda_mult, 2)},
            "ev_revenue": {"median": round(median_rev_mult, 2), "mean": round(mean_rev_mult, 2)},
        },
        "control_premium_pct": control_premium_pct,
        "implied_enterprise_value": {
            "from_ev_ebitda": round(implied_ev_ebitda, 2),
            "from_ev_revenue": round(implied_ev_revenue, 2),
        },
        "implied_equity_value": {
            "from_ev_ebitda": round(implied_ev_ebitda - net_debt, 2),
            "from_ev_revenue": round(implied_ev_revenue - net_debt, 2),
        },
        "implied_price_per_share": {
            "from_ev_ebitda": round((implied_ev_ebitda - net_debt) / shares_outstanding, 2) if shares_outstanding else None,
            "from_ev_revenue": round((implied_ev_revenue - net_debt) / shares_outstanding, 2) if shares_outstanding else None,
        },
    }


def merger_model(
    acquirer_shares: float,
    acquirer_eps: float,
    acquirer_price: float,
    target_net_income: float,
    target_shares: float,
    deal_value: float,
    pct_stock: float,
    cost_synergies: float = 0.0,
    revenue_synergies: float = 0.0,
    synergy_tax_rate: float = 0.25,
    financing_cost: float = 0.05,
    acquirer_tax_rate: float = 0.25,
) -> dict[str, Any]:
    """Merger accretion/dilution model."""
    pct_cash = 1 - pct_stock
    stock_consideration = deal_value * pct_stock
    cash_consideration = deal_value * pct_cash

    new_shares_issued = stock_consideration / acquirer_price if acquirer_price else 0
    pro_forma_shares = acquirer_shares + new_shares_issued

    interest_expense = cash_consideration * financing_cost * (1 - acquirer_tax_rate)

    after_tax_synergies = (cost_synergies + revenue_synergies) * (1 - synergy_tax_rate)

    pro_forma_net_income = (
        acquirer_eps * acquirer_shares
        + target_net_income
        - interest_expense
        + after_tax_synergies
    )
    pro_forma_eps = pro_forma_net_income / pro_forma_shares if pro_forma_shares else 0
    standalone_eps = acquirer_eps
    accretion_dilution_pct = (pro_forma_eps - standalone_eps) / standalone_eps * 100 if standalone_eps else 0
    exchange_ratio = new_shares_issued / target_shares if target_shares else 0

    return {
        "deal_structure": {
            "total_deal_value": round(deal_value, 2),
            "stock_consideration": round(stock_consideration, 2),
            "cash_consideration": round(cash_consideration, 2),
            "new_shares_issued": round(new_shares_issued, 2),
            "exchange_ratio": round(exchange_ratio, 4),
        },
        "pro_forma": {
            "total_shares": round(pro_forma_shares, 2),
            "net_income": round(pro_forma_net_income, 2),
            "eps": round(pro_forma_eps, 4),
        },
        "acquirer_standalone_eps": round(standalone_eps, 4),
        "accretion_dilution_pct": round(accretion_dilution_pct, 2),
        "is_accretive": accretion_dilution_pct > 0,
        "after_tax_synergies": round(after_tax_synergies, 2),
    }


def synergies_analysis(
    revenue_synergies: list[dict],
    cost_synergies: list[dict],
    integration_costs: list[float],
    tax_rate: float = 0.25,
    wacc: float = 0.10,
) -> dict[str, Any]:
    """PV of synergies net of integration costs."""
    total_rev_synergy = sum(s.get("amount", 0) for s in revenue_synergies)
    total_cost_synergy = sum(s.get("amount", 0) for s in cost_synergies)
    gross_synergies = total_rev_synergy + total_cost_synergy
    after_tax_synergies = gross_synergies * (1 - tax_rate)

    pv_synergies = after_tax_synergies / wacc if wacc else 0

    total_integration_costs = sum(integration_costs)
    pv_integration = sum(c / (1 + wacc) ** (i + 1) for i, c in enumerate(integration_costs))

    net_pv_synergies = pv_synergies - pv_integration

    return {
        "gross_synergies": round(gross_synergies, 2),
        "after_tax_synergies": round(after_tax_synergies, 2),
        "pv_synergies": round(pv_synergies, 2),
        "total_integration_costs": round(total_integration_costs, 2),
        "pv_integration_costs": round(pv_integration, 2),
        "net_pv_synergies": round(net_pv_synergies, 2),
        "revenue_synergies": revenue_synergies,
        "cost_synergies": cost_synergies,
    }


def deal_structure(
    enterprise_value: float,
    existing_debt: float,
    target_leverage_ratio: float,
    equity_check: float,
    debt_tranches: list[dict],
) -> dict[str, Any]:
    """Structure a deal into debt and equity components."""
    total_debt = sum(t.get("amount", 0) for t in debt_tranches)
    blended_rate = (
        sum(t.get("amount", 0) * t.get("rate", 0) for t in debt_tranches) / total_debt
        if total_debt else 0
    )
    total_financing = total_debt + equity_check
    leverage = total_debt / enterprise_value if enterprise_value else 0
    annual_interest = sum(t.get("amount", 0) * t.get("rate", 0) for t in debt_tranches)

    return {
        "enterprise_value": round(enterprise_value, 2),
        "sources": {
            "total_debt": round(total_debt, 2),
            "equity_check": round(equity_check, 2),
            "total_sources": round(total_financing, 2),
        },
        "debt_tranches": debt_tranches,
        "blended_interest_rate_pct": round(blended_rate * 100, 2),
        "leverage_ratio": round(leverage, 2),
        "annual_interest_expense": round(annual_interest, 2),
        "target_leverage_ratio": target_leverage_ratio,
        "meets_leverage_target": leverage <= target_leverage_ratio,
    }


TOOLS = {
    "comps_analysis": comps_analysis,
    "precedent_transactions": precedent_transactions,
    "merger_model": merger_model,
    "synergies_analysis": synergies_analysis,
    "deal_structure": deal_structure,
}

TOOL_SCHEMAS = {
    "comps_analysis": {
        "name": "comps_analysis",
        "description": "Comparable company analysis: derive implied valuation ranges from peer trading multiples.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "target_revenue": {"type": "number"},
                "target_ebitda": {"type": "number"},
                "target_ebit": {"type": "number"},
                "target_net_income": {"type": "number"},
                "comparable_ev_revenue_multiples": {"type": "array", "items": {"type": "number"}},
                "comparable_ev_ebitda_multiples": {"type": "array", "items": {"type": "number"}},
                "comparable_pe_multiples": {"type": "array", "items": {"type": "number"}},
                "net_debt": {"type": "number", "default": 0},
                "shares_outstanding": {"type": "number", "default": 1},
            },
            "required": ["target_revenue", "target_ebitda", "target_ebit", "target_net_income",
                         "comparable_ev_revenue_multiples", "comparable_ev_ebitda_multiples", "comparable_pe_multiples"],
        },
    },
    "precedent_transactions": {
        "name": "precedent_transactions",
        "description": "Precedent transaction analysis with optional control premium.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "target_ebitda": {"type": "number"},
                "target_revenue": {"type": "number"},
                "transaction_ev_ebitda_multiples": {"type": "array", "items": {"type": "number"}},
                "transaction_ev_revenue_multiples": {"type": "array", "items": {"type": "number"}},
                "control_premium_pct": {"type": "number", "default": 0},
                "net_debt": {"type": "number", "default": 0},
                "shares_outstanding": {"type": "number", "default": 1},
            },
            "required": ["target_ebitda", "target_revenue", "transaction_ev_ebitda_multiples", "transaction_ev_revenue_multiples"],
        },
    },
    "merger_model": {
        "name": "merger_model",
        "description": "Merger accretion/dilution model for stock-and-cash deals.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "acquirer_shares": {"type": "number"},
                "acquirer_eps": {"type": "number"},
                "acquirer_price": {"type": "number"},
                "target_net_income": {"type": "number"},
                "target_shares": {"type": "number"},
                "deal_value": {"type": "number"},
                "pct_stock": {"type": "number", "description": "Fraction paid in stock (0-1)"},
                "cost_synergies": {"type": "number", "default": 0},
                "revenue_synergies": {"type": "number", "default": 0},
                "synergy_tax_rate": {"type": "number", "default": 0.25},
                "financing_cost": {"type": "number", "default": 0.05},
                "acquirer_tax_rate": {"type": "number", "default": 0.25},
            },
            "required": ["acquirer_shares", "acquirer_eps", "acquirer_price", "target_net_income", "target_shares", "deal_value", "pct_stock"],
        },
    },
    "synergies_analysis": {
        "name": "synergies_analysis",
        "description": "Present value of synergies net of one-time integration costs.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "revenue_synergies": {"type": "array", "items": {"type": "object"}},
                "cost_synergies": {"type": "array", "items": {"type": "object"}},
                "integration_costs": {"type": "array", "items": {"type": "number"}},
                "tax_rate": {"type": "number", "default": 0.25},
                "wacc": {"type": "number", "default": 0.10},
            },
            "required": ["revenue_synergies", "cost_synergies", "integration_costs"],
        },
    },
    "deal_structure": {
        "name": "deal_structure",
        "description": "Structure a deal's sources of financing across debt tranches and equity.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "enterprise_value": {"type": "number"},
                "existing_debt": {"type": "number"},
                "target_leverage_ratio": {"type": "number"},
                "equity_check": {"type": "number"},
                "debt_tranches": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"},
                            "amount": {"type": "number"},
                            "rate": {"type": "number"},
                        },
                    },
                },
            },
            "required": ["enterprise_value", "existing_debt", "target_leverage_ratio", "equity_check", "debt_tranches"],
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
                "serverInfo": {"name": "investment-banking", "version": "1.0.0"},
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
