
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


def resolve_rag_category(extracted_fields):
    """
    Gemini가 추출한 11개 필드 중 category를 읽어
    RAG 검색 카테고리를 결정한다.

    extracted_fields 예시:
        {
            "contractName": "헬스장 이용 계약서",
            "category": "FITNESS"
        }
    """
    start = time.perf_counter()

    category = None
    status = "INVALID_CATEGORY"

    if isinstance(extracted_fields, dict):
        raw_category = extracted_fields.get("category")

        if isinstance(raw_category, str):
            normalized = raw_category.strip().upper()

            if normalized in SUPPORTED_CATEGORIES:
                category = normalized

                path = (
                    LEGAL_DATA_DIR
                    / category.lower()
                    / "legal_rules.json"
                )

                if path.is_file():
                    status = "SUCCESS"
                else:
                    status = "LEGAL_DATA_NOT_READY"

    return {
        "category": category,
        "status": status,
        "routing_time": round(
            time.perf_counter() - start, 6
        ),
    }
