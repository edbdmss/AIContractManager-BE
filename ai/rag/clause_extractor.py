
import re
from dataclasses import dataclass, asdict
from typing import List


ARTICLE_PATTERN = re.compile(
    r"^\s*제\s*(\d+)\s*조(?:\s*의\s*\d+)?(?=\s|[\(（.:：]|$)"
)

# 페이지 머리글/바닥글: 현재 테스트 PDF 기준
PAGE_NOISE_PATTERNS = [
    re.compile(r"^가상 계약서\s*·\s*기능 검증 전용\s*\|\s*\d+$"),
    re.compile(r"^계약 조항\s*\(계속\)$"),
]

# 조항 이후 별도 영역의 시작을 나타내는 제목
END_SECTION_PATTERNS = [
    re.compile(r"^확인\s*및\s*서명$"),
    re.compile(r"^서명란$"),
    re.compile(r"^계약 당사자 서명$"),
]


@dataclass
class ContractClause:
    clause_id: str
    title: str
    text: str
    page: int
    pages: List[int]
    span_ids: List[str]
    bboxes: List[dict]

    def to_dict(self):
        return asdict(self)


def is_page_noise(text):
    return any(
        pattern.fullmatch(text)
        for pattern in PAGE_NOISE_PATTERNS
    )


def is_end_section(text):
    return any(
        pattern.fullmatch(text)
        for pattern in END_SECTION_PATTERNS
    )


def add_span(clause, span, text):
    clause.text += "\n" + text
    clause.span_ids.append(span.span_id)

    if span.page not in clause.pages:
        clause.pages.append(span.page)

    clause.bboxes.append({
        "page": span.page,
        "bbox": span.bbox,
        "span_id": span.span_id,
    })


def extract_clauses(spans):
    clauses = []
    current = None
    clause_count = 0

    for span in spans:
        text = span.text.strip()

        if not text:
            continue

        # 1. 페이지 머리글/바닥글 제외
        if is_page_noise(text):
            continue

        # 2. 서명 영역 시작 시 추출 종료
        if is_end_section(text):
            if current is not None:
                clauses.append(current)
                current = None
            break

        # 3. 새로운 조항 시작
        match = ARTICLE_PATTERN.match(text)

        if match:
            if current is not None:
                clauses.append(current)

            clause_count += 1

            current = ContractClause(
                clause_id=f"clause_{clause_count:03d}",
                title=text,
                text=text,
                page=span.page,
                pages=[span.page],
                span_ids=[span.span_id],
                bboxes=[{
                    "page": span.page,
                    "bbox": span.bbox,
                    "span_id": span.span_id,
                }],
            )

        # 4. 기존 조항에 본문 추가
        elif current is not None:
            add_span(current, span, text)

    if current is not None:
        clauses.append(current)

    return clauses
