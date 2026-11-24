import time

from datetime import datetime, date, timedelta, timezone
from decimal import Decimal


KST = timezone(timedelta(hours=9))


ALLOWED_FIELDS = (
    "id",
    "contractId",
    "title",
    "contractName",
    "category",
    "providerName",
    "status",
    "sourceType",
    "contractStartDate",
    "contractEndDate",
    "autoRenewalDate",
    "nextPaymentDate",
    "cancellationDeadline",
    "monthlyAmount",
    "penaltyAmount",
    "currency",
    "amountVerified",
    "cancellationTerms",
    "plainSummary",
    "riskReports",
)


DATE_FIELDS = (
    "contractEndDate",
    "autoRenewalDate",
    "nextPaymentDate",
    "cancellationDeadline",
)


def get_days_remaining(value, today):
    if not value:
        return None

    try:
        target = date.fromisoformat(str(value))
        return (target - today).days

    except (ValueError, TypeError):
        return None


def build_contract_context(
    contracts,
    contract_id=None,
):
    start = time.perf_counter()

    today = datetime.now(KST).date()

    selected = []

    for contract in contracts:

        if not isinstance(contract, dict):
            continue

        if contract_id is not None:
            if str(contract.get("id")) != str(contract_id):
                continue

        item = {
            key: contract[key]
            for key in ALLOWED_FIELDS
            if key in contract
        }

        remaining_days = {}

        for field in DATE_FIELDS:
            remaining_days[field] = get_days_remaining(
                item.get(field),
                today,
            )

        item["remainingDays"] = remaining_days

        selected.append(item)

    active_contracts = [
        contract
        for contract in selected
        if contract.get("status") == "ACTIVE"
    ]

    monthly_totals = {}
    missing_amount_count = 0

    for contract in active_contracts:
        amount = contract.get("monthlyAmount")

        if amount is None:
            missing_amount_count += 1
            continue

        currency = contract.get("currency") or "KRW"

        monthly_totals[currency] = (
            monthly_totals.get(currency, Decimal("0"))
            + Decimal(str(amount))
        )

    monthly_totals = {
        currency: int(amount)
        if amount == amount.to_integral_value()
        else float(amount)
        for currency, amount in monthly_totals.items()
    }

    return {
        "current_date": today.isoformat(),

        "timezone": "Asia/Seoul",

        "contracts": selected,

        "contract_count": len(selected),

        "active_contract_count": len(active_contracts),

        "active_monthly_totals": monthly_totals,

        "active_missing_amount_count": missing_amount_count,

        "scope": (
            "SELECTED_CONTRACT"
            if contract_id
            else "PROVIDED_CONTRACTS"
        ),

        "context_time": round(
            time.perf_counter() - start,
            6,
        ),
    }