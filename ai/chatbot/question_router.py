import time


LEGAL_KEYWORDS = (
    "법률",
    "법적",
    "법적으로",
    "법령",
    "법규",
    "법상",
    "관련법",
    "보호법",
    "소비자보호",
    "위약금",
    "환불",
    "해지",
    "해약",
    "중도해지",
    "취소",
    "불공정",
    "위법",
    "불법",
    "합법",
    "권리",
    "분쟁",
    "손해배상",
    "보상",
    "위반",
    "신고",
    "약관",
    "청약철회",
    "보증금",
    "갱신청구권",
    "돌려받",
    "돌려줘",
    "계약해제",
    "부당",
)


def normalize(text):
    return "".join(
        str(text or "").lower().split()
    )


def classify_question(
    message,
    has_contract=False,
    history=None,
):
    start = time.perf_counter()

    query = normalize(message)

    needs_legal_rag = any(
        normalize(keyword) in query
        for keyword in LEGAL_KEYWORDS
    )

    # 짧은 후속 질문에서 이전 법률 질문 참고
    followup_words = (
        "그럼",
        "그러면",
        "그경우",
        "그거",
        "그건",
        "얼마",
        "가능해",
        "어떻게",
        "맞아",
    )

    if not needs_legal_rag:

        is_followup = (
            len(query) <= 35
            and any(
                word in query
                for word in followup_words
            )
        )

        if is_followup:

            for item in reversed(history or []):

                if hasattr(item, "model_dump"):
                    item = item.model_dump()

                if item.get("role", "").lower() != "user":
                    continue

                previous_query = normalize(
                    item.get("content")
                )

                needs_legal_rag = any(
                    normalize(keyword) in previous_query
                    for keyword in LEGAL_KEYWORDS
                )

                break

    return {
        "needs_legal_rag": needs_legal_rag,

        "routing_time": round(
            time.perf_counter() - start,
            6,
        ),
    }