
import time


MAX_CONTRACTS = 30

ALLOWED_FIELDS = (
    "id",
    "contractName",
    "title",
    "category",
    "providerName",
    "contractStartDate",
    "contractEndDate",
    "monthlyAmount",
    "penaltyAmount",
    "cancellationTerms",
    "plainSummary",
    "riskReports",
)


def build_contract_context(contracts, contract_id=None):
    start = time.perf_counter()

    if len(contracts) > MAX_CONTRACTS:
        raise ValueError(
            f"한 번에 최대 {MAX_CONTRACTS}개 계약만 처리합니다."
        )

    selected = []

    for contract in contracts:
        if not isinstance(contract, dict):
            continue

        if contract_id is not None:
            if contract.get("id") != contract_id:
                continue

        filtered = {
            key: contract[key]
            for key in ALLOWED_FIELDS
            if key in contract
        }

        selected.append(filtered)

    return {
        "contracts": selected,
        "contract_count": len(selected),
        "context_time": round(
            time.perf_counter() - start, 6
        ),
    }
