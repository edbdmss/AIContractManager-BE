
import re
import time

CATEGORY_ALIASES = {
    "FITNESS": ("헬스", "헬스장", "피트니스", "운동", "pt", "필라테스", "요가"),
    "TELECOM": ("통신", "휴대폰", "핸드폰", "인터넷", "와이파이"),
    "RENTAL": ("렌탈", "정수기", "공기청정기"),
    "HOUSING": ("월세", "전세", "임대차", "집 계약", "주택"),
    "INSURANCE": ("보험", "보험료"),
    "SUBSCRIPTION": ("구독", "ott", "넷플릭스", "유튜브"),
}

FOLLOWUP_WORDS = (
    "그럼", "그러면", "그 계약", "그거", "그것",
    "거기", "아까", "방금", "그 경우",
)


def _normalize(value):
    return re.sub(r"\s+", "", str(value or "").lower())


def _score(contract, question):
    q = _normalize(question)
    score = 0

    for field in ("title", "contractName", "providerName"):
        name = _normalize(contract.get(field))
        if len(name) >= 2 and name in q:
            score += 10

    category = str(contract.get("category") or "").upper()
    for alias in CATEGORY_ALIASES.get(category, ()):
        if _normalize(alias) in q:
            score += 5
            break

    return score


def select_contract(message, contracts, contract_id=None, history=None):
    start = time.perf_counter()
    history = history or []

    def result(contract=None, ambiguous=False, candidates=None, reason=""):
        return {
            "contract": contract,
            "ambiguous": ambiguous,
            "candidates": candidates or [],
            "reason": reason,
            "selection_time": round(time.perf_counter() - start, 6),
        }

    if contract_id is not None:
        matches = [
            c for c in contracts
            if str(c.get("id")) == str(contract_id)
        ]
        if not matches:
            return result(reason="INVALID_CONTRACT_ID")
        return result(matches[0], reason="EXPLICIT_ID")

    if not contracts:
        return result(reason="NO_CONTRACTS")

    scored = sorted(
        [(_score(c, message), c) for c in contracts],
        key=lambda item: item[0],
        reverse=True,
    )

    best_score = scored[0][0]

    if best_score > 0:
        matches = [c for score, c in scored if score == best_score]
        if len(matches) == 1:
            return result(matches[0], reason="QUESTION_MATCH")
        return result(
            ambiguous=True,
            candidates=matches,
            reason="MULTIPLE_MATCHES",
        )

    if len(contracts) == 1:
        return result(contracts[0], reason="ONLY_CONTRACT")

    # 현재 질문에 명시된 계약이 없을 때만 이전 사용자 질문을 참고한다.
    # 가장 최근 질문부터 살펴보고, 유일하게 일치하는 경우에만 선택한다.
    if any(word in message.lower() for word in FOLLOWUP_WORDS):
        for item in reversed(history[-10:]):
            role = item.role if hasattr(item, "role") else item.get("role")
            content = (
                item.content if hasattr(item, "content")
                else item.get("content", "")
            )
            if role != "user":
                continue

            previous_scores = sorted(
                [(_score(c, content), c) for c in contracts],
                key=lambda pair: pair[0],
                reverse=True,
            )
            top = previous_scores[0][0]
            matches = [c for s, c in previous_scores if s == top]

            if top > 0:
                if len(matches) == 1:
                    return result(matches[0], reason="HISTORY_MATCH")
                break

    return result(
        ambiguous=True,
        candidates=contracts,
        reason="CONTRACT_NOT_IDENTIFIED",
    )
