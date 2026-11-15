
from dataclasses import dataclass, asdict
import json
import pymupdf


@dataclass
class TextSpan:
    span_id: str
    page: int
    text: str
    bbox: list[float]


def extract_pdf_spans(pdf_bytes: bytes) -> list[TextSpan]:
    """PDF에서 텍스트와 위치를 추출한다."""

    if not pdf_bytes:
        raise ValueError("PDF 파일이 비어 있습니다.")

    spans = []

    with pymupdf.open(
        stream=pdf_bytes,
        filetype="pdf"
    ) as doc:

        for page_index, page in enumerate(doc):
            page_data = page.get_text("dict")
            span_index = 0

            for block in page_data["blocks"]:
                # 이미지 블록 제외
                if block.get("type") != 0:
                    continue

                for line in block.get("lines", []):
                    for span in line.get("spans", []):
                        text = span["text"].strip()

                        if not text:
                            continue

                        span_index += 1

                        spans.append(
                            TextSpan(
                                span_id=(
                                    f"p{page_index + 1}_"
                                    f"s{span_index:04d}"
                                ),
                                page=page_index + 1,
                                text=text,
                                bbox=[
                                    round(v, 2)
                                    for v in span["bbox"]
                                ],
                            )
                        )

    return spans


def save_spans_json(
    spans: list[TextSpan],
    output_path: str
):
    """추출한 텍스트와 좌표를 JSON으로 저장한다."""

    data = [asdict(span) for span in spans]

    with open(
        output_path,
        "w",
        encoding="utf-8"
    ) as f:
        json.dump(
            data,
            f,
            ensure_ascii=False,
            indent=2
        )
