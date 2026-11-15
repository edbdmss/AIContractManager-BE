
import json
import re
import time
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent.parent
LEGAL_DATA_DIR = BASE_DIR / "data" / "legal"

SUPPORTED_CATEGORIES = {
    "FITNESS",
    "TELECOM",
    "SUBSCRIPTION",
    "RENTAL",
    "HOUSING",
    "INSURANCE",
    "OTHER",
}


def normalize(text):
    """검색용 문자열 정규화."""
    return re.sub(r"\s+", " ", str(text).lower()).strip()


def load_legal_rules(category):
    """
    카테고리에 해당하는 법적 기준을 불러온다.

    반환값:
        {
            "category": str | None,
            "status": str,
            "rules": list,
            "load_time": float
        }
    """
    start = time.perf_counter()

    if not isinstance(category, str):
        return {
            "category": None,
            "status": "INVALID_CATEGORY",
            "rules": [],
            "load_time": round(time.perf_counter() - start, 6),
        }

    category = category.strip().upper()

    if category not in SUPPORTED_CATEGORIES:
        return {
            "category": category,
            "status": "INVALID_CATEGORY",
            "rules": [],
            "load_time": round(time.perf_counter() - start, 6),
        }

    path = (
        LEGAL_DATA_DIR
        / category.lower()
        / "legal_rules.json"
    )

    if not path.is_file():
        return {
            "category": category,
            "status": "LEGAL_DATA_NOT_READY",
            "rules": [],
            "load_time": round(time.perf_counter() - start, 6),
        }

    with open(path, "r", encoding="utf-8") as f:
        all_rules = json.load(f)

    if not isinstance(all_rules, list):
        raise ValueError(
            f"법률 데이터는 JSON 배열이어야 합니다: {path}"
        )

    rules = [
        rule
        for rule in all_rules
        if isinstance(rule, dict)
        and rule.get("category") == category
        and rule.get("verification_status") == "VERIFIED"
    ]

    return {
        "category": category,
        "status": "SUCCESS" if rules else "NO_USABLE_LEGAL_RULES",
        "rules": rules,
        "load_time": round(time.perf_counter() - start, 6),
    }




def score_rule(query, rule):
    """
    법률별 키워드 검색 점수 계산.

    - keywords: 일반 검색 키워드
    - core_keywords: 핵심 검색 키워드 (선택)
    """
    query = normalize(query)

    matched_keywords = []
    score = 0

    keywords = rule.get("keywords", [])
    core_keywords = rule.get("core_keywords", [])

    if not isinstance(keywords, list):
        keywords = []

    if not isinstance(core_keywords, list):
        core_keywords = []

    for keyword in keywords:
        if not isinstance(keyword, str) or not keyword.strip():
            continue

        if normalize(keyword) in query:
            matched_keywords.append(keyword)
            score += 3

    # 핵심 키워드가 지정된 경우
    # 최소 하나 이상 일치해야 검색 대상으로 인정
    if core_keywords:
        core_matched = [
            keyword
            for keyword in core_keywords
            if isinstance(keyword, str)
            and keyword.strip()
            and normalize(keyword) in query
        ]

        if not core_matched:
            return 0, []

        score += len(core_matched) * 2

        for keyword in core_matched:
            if keyword not in matched_keywords:
                matched_keywords.append(keyword)

    if not matched_keywords:
        return 0, []

    title_words = re.findall(
        r"[가-힣A-Za-z]{2,}",
        rule.get("title", "")
    )

    for word in set(title_words):
        if normalize(word) in query:
            score += 1

    return score, matched_keywords



def retrieve_legal_rules(query, category, top_k=3):
    """
    Gemini가 추출한 category를 기준으로 법적 기준 검색.

    예:
        retrieve_legal_rules(
            query="중도 해지 시 위약금이 발생한다.",
            category="FITNESS",
            top_k=3
        )
    """
    total_start = time.perf_counter()

    if not isinstance(top_k, int) or isinstance(top_k, bool) or top_k < 1:
        raise ValueError("top_k는 1 이상의 정수여야 합니다.")

    loaded = load_legal_rules(category)

    if loaded["status"] != "SUCCESS":
        return {
            "category": loaded["category"],
            "query": query,
            "status": loaded["status"],
            "results": [],
            "load_time": loaded["load_time"],
            "search_time": 0.0,
            "retrieval_time": round(
                time.perf_counter() - total_start, 6
            ),
        }

    search_start = time.perf_counter()
    results = []

    for rule in loaded["rules"]:
        score, matched = score_rule(query, rule)

        if score <= 0:
            continue

        results.append({
            **rule,
            "score": score,
            "matched_keywords": matched,
        })

    results.sort(
        key=lambda item: (
            -item["score"],
            item["rule_id"]
        )
    )

    search_time = time.perf_counter() - search_start

    return {
        "category": loaded["category"],
        "query": query,
        "status": "SUCCESS" if results else "NO_MATCHING_RULES",
        "results": results[:top_k],
        "load_time": loaded["load_time"],
        "search_time": round(search_time, 6),
        "retrieval_time": round(
            time.perf_counter() - total_start, 6
        ),
    }
