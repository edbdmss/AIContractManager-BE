CATEGORY_ALIASES = {
    "FITNESS": (
        "헬스",
        "헬스장",
        "피트니스",
        "운동",
        "필라테스",
        "요가",
        "fitness",
    ),

    "TELECOM": (
        "통신",
        "휴대폰",
        "핸드폰",
        "인터넷",
        "와이파이",
        "telecom",
    ),

    "RENTAL": (
        "렌탈",
        "렌털",
        "정수기",
        "공기청정기",
        "rental",
    ),

    "HOUSING": (
        "부동산",
        "임대차",
        "임대인",
        "임차인",
        "월세",
        "전세",
        "주택",
        "보증금",
        "housing",
    ),

    "INSURANCE": (
        "보험",
        "보험료",
        "보험사",
        "insurance",
    ),

    "SUBSCRIPTION": (
        "구독",
        "넷플릭스",
        "유튜브",
        "디즈니",
        "스포티파이",
        "ott",
        "subscription",
    ),
}


LEGAL_CATEGORIES = {
    "FITNESS",
    "TELECOM",
    "RENTAL",
    "HOUSING",
    "INSURANCE",
}


def normalize(text):
    return "".join(
        str(text or "").lower().split()
    )


def infer_categories(
    message,
    contracts=None,
    history=None,
):
    contracts = contracts or []
    history = history or []

    query = normalize(message)

    categories = set()

    # 1. 현재 질문의 카테고리 확인
    for category, aliases in CATEGORY_ALIASES.items():

        if any(
            normalize(alias) in query
            for alias in aliases
        ):
            categories.add(category)

    # 2. 계약명 및 업체명 확인
    for contract in contracts:

        names = (
            contract.get("title"),
            contract.get("contractName"),
            contract.get("providerName"),
        )

        for name in names:
            normalized_name = normalize(name)

            if (
                len(normalized_name) >= 2
                and normalized_name in query
            ):
                category = contract.get("category")

                if category:
                    categories.add(category)

    if categories:
        return sorted(categories)

    # 3. 후속 질문인 경우 최근 대화 참고
    for item in reversed(history):

        if hasattr(item, "model_dump"):
            item = item.model_dump()

        if item.get("role", "").lower() != "user":
            continue

        previous_query = normalize(
            item.get("content")
        )

        for category, aliases in CATEGORY_ALIASES.items():

            if any(
                normalize(alias) in previous_query
                for alias in aliases
            ):
                categories.add(category)

        for contract in contracts:

            names = (
                contract.get("title"),
                contract.get("contractName"),
                contract.get("providerName"),
            )

            if any(
                len(normalize(name)) >= 2
                and normalize(name) in previous_query
                for name in names
            ):
                category = contract.get("category")

                if category:
                    categories.add(category)

        if categories:
            return sorted(categories)

    # 4. 특정 카테고리를 찾지 못한 경우
    # 전달받은 계약들의 카테고리 참고
    for contract in contracts:

        category = contract.get("category")

        if category:
            categories.add(category)

    return sorted(categories)