
import io
import time
import pymupdf as fitz


def highlight_review_clauses(
    pdf_bytes: bytes,
    clauses: list,
    legal_analysis_results: list,
):
    """
    REVIEW_NEEDED 조항을 PDF에 하이라이트한다.

    Parameters
    ----------
    pdf_bytes:
        개인정보 마스킹이 완료된 PDF 바이트

    clauses:
        rag_result["clauses"]

    legal_analysis_results:
        legal_analysis_result["results"]

    Returns
    -------
    {
        "pdf_bytes": bytes,
        "highlighted_clause_ids": list,
        "highlight_count": int,
        "processing_time": float
    }
    """
    start = time.perf_counter()

    review_ids = {
        item["clause_id"]
        for item in legal_analysis_results
        if item.get("status") == "REVIEW_NEEDED"
    }

    highlighted_clause_ids = set()
    highlight_count = 0

    with fitz.open(stream=pdf_bytes, filetype="pdf") as doc:

        for clause in clauses:
            clause_id = clause.get("clause_id")

            if clause_id not in review_ids:
                continue

            # bboxes는 PDF 텍스트 추출 과정에서 저장한 좌표
            bboxes = clause.get("bboxes", [])

            # 기존 clause 구조에서 페이지별 좌표가
            # 어떻게 저장되는지에 따라 처리
            for bbox_item in bboxes:

                # 형태 1:
                # {"page": 0, "bbox": [x0, y0, x1, y1]}
                if isinstance(bbox_item, dict):
                    page_num = bbox_item.get(
                        "page",
                        clause.get("page", 0)
                    )
                    coords = bbox_item.get("bbox")

                # 형태 2:
                # [x0, y0, x1, y1]
                else:
                    page_num = clause.get("page", 0)
                    coords = bbox_item

                if coords is None:
                    continue


                try:
                    # PDF 파서에서 저장한 페이지는 1부터 시작
                    # PyMuPDF 페이지 인덱스는 0부터 시작
                    page_num = int(page_num) - 1

                    rect = fitz.Rect(coords)

                except (TypeError, ValueError):
                    continue


                if page_num < 0 or page_num >= len(doc):
                    continue

                if rect.is_empty or rect.is_infinite:
                    continue

                page = doc[page_num]

                # 페이지 범위를 벗어난 좌표 제외
                if not page.rect.intersects(rect):
                    continue

                annot = page.add_highlight_annot(rect)

                if annot is None:
                    continue

                annot.set_colors(
                    stroke=(1.0, 0.85, 0.2)
                )
                annot.set_opacity(0.35)
                annot.update()

                highlighted_clause_ids.add(clause_id)
                highlight_count += 1

        output_bytes = doc.tobytes(
            garbage=4,
            deflate=True
        )

    processing_time = round(
        time.perf_counter() - start, 6
    )

    return {
        "pdf_bytes": output_bytes,
        "highlighted_clause_ids": sorted(
            highlighted_clause_ids
        ),
        "highlight_count": highlight_count,
        "processing_time": processing_time,
    }
