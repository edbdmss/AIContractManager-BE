
import re
import time


LEGAL_KEYWORDS = (
    "법률", "법적", "법적으로", "법", "규정",
    "위약금", "환불", "해지", "해약",
    "중도 해지", "불공정", "위법", "권리",
    "분쟁", "손해배상", "보상", "위반",
)

CONTRACT_KEYWORDS = (
    "내 계약", "내가", "나의", "등록한",
    "저장한", "우리", "내 헬스장",
    "내 보험", "내 통신", "내 렌탈",
    "내 임대차", "내 월세", "내 전세",
)

CONTRACT_FIELDS = (
    "언제", "종료", "만료", "시작",
    "기간", "금액", "얼마", "납부",
    "월 납부", "요약", "목록", "갱신",
)


def classify_question(message, has_contract=False):
    start = time.perf_counter()

    text = re.sub(r"\s+", " ", message).strip().lower()

    is_legal = any(
        keyword in text for keyword in LEGAL_KEYWORDS
    )

    is_contract = (
        has_contract
        or any(keyword in text for keyword in CONTRACT_KEYWORDS)
        or (
            any(keyword in text for keyword in CONTRACT_FIELDS)
            and "계약" in text
        )
    )

    if is_legal and is_contract:
        question_type = "HYBRID"
    elif is_legal:
        question_type = "LEGAL"
    else:
        question_type = "CONTRACT"

    return {
        "type": question_type,
        "routing_time": round(
            time.perf_counter() - start, 6
        ),
    }
