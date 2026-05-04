"""
Private equity MCP server.
Provides LBO modeling, fund returns, and portfolio company analysis tools.
Requires: financial-analysis@financial-services-plugins
"""

import json
import sys
from typing import Any


def lbo_model(
    entry_enterprise_value: float,
    entry_ebitda: float,
    ebitda_projections: list[float],
    exit_ebitda_multiple: float,
    debt_tranches: list[dict],
    equity_check: float,
    management_option_pool_pct: float = 0.0,
    transaction_fees_pct: float = 0.01,
) -> dict[str, Any]:
    """Full leveraged buyout model with debt paydown and exit returns."""
    total_debt = sum(t.get("amount", 0) for t in debt_tranches)
    entry_equity = equity_check
    fees = entry_enterprise_value * transaction_fees_pct

    years = len(ebitda_projections)
    exit_ebitda = ebitda_projections[-1]
    exit_ev = exit_ebitda * exit_ebitda_multiple

    remaining_debt = total_debt
    for year, ebitda in enumerate(ebitda_projections, start=1):
        mandatory_amort = sum(t.get("amortization_pct", 0) * t.get("amount", 0) for t in debt_tranches)
        cash_sweep = max(0, ebitda * 0.5 - mandatory_amort)
        remaining_debt = max(0, remaining_debt - mandatory_amort - cash_sweep)

    exit_equity = max(0, exit_ev - remaining_debt)
    sponsor_equity = exit_equity * (1 - management_option_pool_pct)

    moic = sponsor_equity / entry_equity if entry_equity else 0
    irr = (moic ** (1 / years) - 1) if years and moic > 0 else 0

    entry_leverage = total_debt / entry_ebitda if entry_ebitda else 0
    exit_leverage = remaining_debt / exit_ebitda if exit_ebitda else 0

    return {
        "entry": {
            "enterprise_value": round(entry_enterprise_value, 2),
            "entry_multiple": round(entry_enterprise_value / entry_ebitda, 2) if entry_ebitda else None,
            "total_debt": round(total_debt, 2),
            "equity_check": round(entry_equity, 2),
            "leverage_turn": round(entry_leverage, 2),
            "transaction_fees": round(fees, 2),
        },
        "exit": {
            "exit_ebitda": round(exit_ebitda, 2),
            "exit_multiple": exit_ebitda_multiple,
            "exit_enterprise_value": round(exit_ev, 2),
            "remaining_debt": round(remaining_debt, 2),
            "exit_equity": round(exit_equity, 2),
            "sponsor_equity": round(sponsor_equity, 2),
            "exit_leverage_turn": round(exit_leverage, 2),
        },
        "returns": {
            "moic": round(moic, 2),
            "irr_pct": round(irr * 100, 2),
            "holding_period_years": years,
        },
        "debt_tranches": debt_tranches,
    }


def irr_calculator(
    cash_flows: list[float],
    initial_investment: float,
    max_iterations: int = 1000,
    tolerance: float = 1e-7,
) -> dict[str, Any]:
    """Calculate IRR using Newton-Raphson for an arbitrary cash flow stream."""
    flows = [-initial_investment] + cash_flows

    def npv_and_derivative(rate: float):
        npv = 0.0
        d_npv = 0.0
        for t, cf in enumerate(flows):
            factor = (1 + rate) ** t
            npv += cf / factor
            if t > 0:
                d_npv -= t * cf / ((1 + rate) ** (t + 1))
        return npv, d_npv

    rate = 0.1
    for _ in range(max_iterations):
        npv, d_npv = npv_and_derivative(rate)
        if abs(d_npv) < 1e-12:
            break
        new_rate = rate - npv / d_npv
        if abs(new_rate - rate) < tolerance:
            rate = new_rate
            break
        rate = new_rate

    npv_check, _ = npv_and_derivative(rate)
    return {
        "irr_pct": round(rate * 100, 4),
        "initial_investment": initial_investment,
        "cash_flows": cash_flows,
        "npv_at_irr": round(npv_check, 6),
        "holding_period_years": len(cash_flows),
    }


def moic_calculator(
    invested_capital: float,
    realized_proceeds: list[float],
    unrealized_nav: float = 0.0,
    management_fees: float = 0.0,
    carried_interest_pct: float = 0.20,
    preferred_return_pct: float = 0.08,
) -> dict[str, Any]:
    """MOIC and net returns after fees and carried interest."""
    gross_proceeds = sum(realized_proceeds) + unrealized_nav
    gross_moic = gross_proceeds / invested_capital if invested_capital else 0

    net_of_fees = gross_proceeds - management_fees
    hurdle = invested_capital * (1 + preferred_return_pct) ** len(realized_proceeds)
    profit_above_hurdle = max(0, net_of_fees - hurdle)
    carry = profit_above_hurdle * carried_interest_pct
    net_proceeds = net_of_fees - carry

    net_moic = net_proceeds / invested_capital if invested_capital else 0
    total_value_to_paid_in = gross_moic
    distributed_to_paid_in = sum(realized_proceeds) / invested_capital if invested_capital else 0
    residual_to_paid_in = unrealized_nav / invested_capital if invested_capital else 0

    return {
        "invested_capital": invested_capital,
        "gross_proceeds": round(gross_proceeds, 2),
        "gross_moic": round(gross_moic, 2),
        "management_fees": round(management_fees, 2),
        "carried_interest": round(carry, 2),
        "net_proceeds": round(net_proceeds, 2),
        "net_moic": round(net_moic, 2),
        "tvpi": round(total_value_to_paid_in, 2),
        "dpi": round(distributed_to_paid_in, 2),
        "rvpi": round(residual_to_paid_in, 2),
    }


def fund_returns(
    vintage_year: int,
    capital_calls: list[dict],
    distributions: list[dict],
    nav: float,
    management_fee_pct: float = 0.02,
    carry_pct: float = 0.20,
    hurdle_pct: float = 0.08,
) -> dict[str, Any]:
    """Aggregate fund-level performance metrics (DPI, RVPI, TVPI, net IRR)."""
    total_called = sum(c.get("amount", 0) for c in capital_calls)
    total_distributed = sum(d.get("amount", 0) for d in distributions)

    total_fees = total_called * management_fee_pct * len(capital_calls)
    net_invested = total_called - total_fees

    dpi = total_distributed / total_called if total_called else 0
    rvpi = nav / total_called if total_called else 0
    tvpi = dpi + rvpi

    return {
        "vintage_year": vintage_year,
        "total_capital_called": round(total_called, 2),
        "total_distributions": round(total_distributed, 2),
        "nav": round(nav, 2),
        "management_fees_paid": round(total_fees, 2),
        "metrics": {
            "dpi": round(dpi, 2),
            "rvpi": round(rvpi, 2),
            "tvpi": round(tvpi, 2),
        },
        "fee_terms": {
            "management_fee_pct": management_fee_pct,
            "carry_pct": carry_pct,
            "hurdle_pct": hurdle_pct,
        },
    }


def debt_schedule(
    initial_balance: float,
    interest_rate: float,
    years: int,
    amortization_pct: float = 0.0,
    cash_sweep_pct: float = 0.5,
    ebitda_projections: list[float] = None,
    cash_interest: bool = True,
) -> dict[str, Any]:
    """Annual debt amortization schedule with optional cash sweep."""
    schedule = []
    balance = initial_balance
    total_interest = 0.0
    total_principal = 0.0

    for year in range(1, years + 1):
        interest = balance * interest_rate
        mandatory_amort = initial_balance * amortization_pct
        if ebitda_projections and year <= len(ebitda_projections):
            ebitda = ebitda_projections[year - 1]
            cash_available = ebitda - (interest if cash_interest else 0) - mandatory_amort
            sweep = max(0, cash_available * cash_sweep_pct)
        else:
            sweep = 0
        principal_paid = min(balance, mandatory_amort + sweep)
        ending_balance = max(0, balance - principal_paid)
        schedule.append({
            "year": year,
            "beginning_balance": round(balance, 2),
            "interest_expense": round(interest, 2),
            "mandatory_amortization": round(mandatory_amort, 2),
            "cash_sweep": round(sweep, 2),
            "total_principal_paid": round(principal_paid, 2),
            "ending_balance": round(ending_balance, 2),
        })
        total_interest += interest
        total_principal += principal_paid
        balance = ending_balance

    return {
        "initial_balance": initial_balance,
        "interest_rate_pct": round(interest_rate * 100, 2),
        "schedule": schedule,
        "summary": {
            "total_interest_paid": round(total_interest, 2),
            "total_principal_paid": round(total_principal, 2),
            "remaining_balance": round(balance, 2),
        },
    }


TOOLS = {
    "lbo_model": lbo_model,
    "irr_calculator": irr_calculator,
    "moic_calculator": moic_calculator,
    "fund_returns": fund_returns,
    "debt_schedule": debt_schedule,
}

TOOL_SCHEMAS = {
    "lbo_model": {
        "name": "lbo_model",
        "description": "Full LBO model projecting entry/exit returns, MOIC, and IRR.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "entry_enterprise_value": {"type": "number"},
                "entry_ebitda": {"type": "number"},
                "ebitda_projections": {"type": "array", "items": {"type": "number"}},
                "exit_ebitda_multiple": {"type": "number"},
                "debt_tranches": {"type": "array", "items": {"type": "object"}},
                "equity_check": {"type": "number"},
                "management_option_pool_pct": {"type": "number", "default": 0},
                "transaction_fees_pct": {"type": "number", "default": 0.01},
            },
            "required": ["entry_enterprise_value", "entry_ebitda", "ebitda_projections", "exit_ebitda_multiple", "debt_tranches", "equity_check"],
        },
    },
    "irr_calculator": {
        "name": "irr_calculator",
        "description": "Calculate IRR via Newton-Raphson for any cash flow stream.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "cash_flows": {"type": "array", "items": {"type": "number"}, "description": "Cash inflows (positive) by year"},
                "initial_investment": {"type": "number", "description": "Initial outflow (positive value)"},
            },
            "required": ["cash_flows", "initial_investment"],
        },
    },
    "moic_calculator": {
        "name": "moic_calculator",
        "description": "Compute gross and net MOIC, DPI, RVPI, and TVPI after fees and carry.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "invested_capital": {"type": "number"},
                "realized_proceeds": {"type": "array", "items": {"type": "number"}},
                "unrealized_nav": {"type": "number", "default": 0},
                "management_fees": {"type": "number", "default": 0},
                "carried_interest_pct": {"type": "number", "default": 0.20},
                "preferred_return_pct": {"type": "number", "default": 0.08},
            },
            "required": ["invested_capital", "realized_proceeds"],
        },
    },
    "fund_returns": {
        "name": "fund_returns",
        "description": "Calculate fund-level DPI, RVPI, TVPI from capital calls and distributions.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "vintage_year": {"type": "integer"},
                "capital_calls": {"type": "array", "items": {"type": "object"}},
                "distributions": {"type": "array", "items": {"type": "object"}},
                "nav": {"type": "number"},
                "management_fee_pct": {"type": "number", "default": 0.02},
                "carry_pct": {"type": "number", "default": 0.20},
                "hurdle_pct": {"type": "number", "default": 0.08},
            },
            "required": ["vintage_year", "capital_calls", "distributions", "nav"],
        },
    },
    "debt_schedule": {
        "name": "debt_schedule",
        "description": "Build an annual debt amortization and cash sweep schedule.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "initial_balance": {"type": "number"},
                "interest_rate": {"type": "number"},
                "years": {"type": "integer"},
                "amortization_pct": {"type": "number", "default": 0},
                "cash_sweep_pct": {"type": "number", "default": 0.5},
                "ebitda_projections": {"type": "array", "items": {"type": "number"}},
                "cash_interest": {"type": "boolean", "default": True},
            },
            "required": ["initial_balance", "interest_rate", "years"],
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
                "serverInfo": {"name": "private-equity", "version": "1.0.0"},
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
