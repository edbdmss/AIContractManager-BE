
import json
import time
import uuid
from pathlib import Path

from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.responses import FileResponse

from extraction.mllm_client import analyze_contract_pdf
from masking.pdf_masker import mask_pdf

from rag.category_router import resolve_rag_category
from rag.pdf_parser import extract_pdf_spans
from rag.clause_extractor import extract_clauses
from rag.legal_retriever import retrieve_legal_rules
from rag.legal_analyzer import analyze_legal_clauses
from rag.pdf_highlighter import highlight_review_clauses


BASE_DIR = Path(__file__).resolve().parent
OUTPUT_DIR = BASE_DIR / "analysis_outputs"

# 로컬 개발용 결과 저장 디렉터리
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


app = FastAPI(
    title="AI Contract Analysis API",
    description=(
        "개인정보 마스킹, Gemini 필드 추출, "
        "RAG 법률 검색, 법적 검토 및 PDF 하이라이트 API"
    ),
    version="1.4.0",
)



def build_risk_reports(rag_clauses, analysis_results, highlighted_pdf_url):
    """검토 필요 조항을 프론트엔드용 응답으로 조립한다.

    법률 근거는 Gemini 자유 텍스트가 아니라 실제 검색 결과에서 가져온다.
    """
    clause_map = {c["clause_id"]: c for c in rag_clauses}
    reports = []

    for item in analysis_results:
        if item.get("status") != "REVIEW_NEEDED":
            continue

        clause = clause_map.get(item.get("clause_id"))
        if not clause:
            continue

        allowed_rules = {
            rule.get("rule_id"): rule
            for rule in clause.get("legal_rules", [])
            if rule.get("rule_id")
        }
        grounds = []
        for rule_id in item.get("related_rule_ids", []):
            rule = allowed_rules.get(rule_id)
            if not rule:
                continue
            grounds.append({
                "ruleId": rule_id,
                "lawName": rule.get("law_name"),
                "article": rule.get("article"),
                "title": rule.get("title"),
                "content": rule.get("content"),
                "applicability": rule.get("applicability"),
                "sourceType": rule.get("source_type"),
                "sourceUrl": rule.get("source_url"),
                "verificationStatus": rule.get("verification_status"),
            })

        reports.append({
            "clauseId": clause["clause_id"],
            "clauseTitle": clause.get("title", ""),
            "originalText": clause.get("text", ""),
            "status": item["status"],
            "riskLevel": item.get("risk_level", "UNKNOWN"),
            "easyExplanation": item.get("easyExplanation", ""),
            "riskReason": item.get("reason", ""),
            "legalGrounds": grounds,
            "recommendation": item.get("recommendation", ""),
            "highlight": {
                "page": clause.get("page"),
                "pages": clause.get("pages", []),
                "spanIds": clause.get("span_ids", []),
                "bboxes": clause.get("bboxes", []),
                "highlightedPdfUrl": highlighted_pdf_url,
            },
        })

    return reports



@app.get("/health")
def health_check():
    return {"status": "ok"}


@app.post("/analyze")
async def analyze_contract(file: UploadFile = File(...)):
    if file.content_type != "application/pdf":
        raise HTTPException(
            status_code=400,
            detail="PDF 파일만 업로드할 수 있습니다."
        )

    pdf_bytes = await file.read()

    if not pdf_bytes:
        raise HTTPException(
            status_code=400,
            detail="빈 PDF 파일입니다."
        )

    total_start = time.perf_counter()

    analysis_id = str(uuid.uuid4())
    analysis_dir = OUTPUT_DIR / analysis_id

    timings = {
        "masking": 0.0,
        "gemini_extraction": 0.0,
        "category_routing": 0.0,
        "pdf_parsing": 0.0,
        "clause_extraction": 0.0,
        "legal_retrieval": 0.0,
        "gemini_legal_analysis": 0.0,
        "legal_analysis_total": 0.0,
        "pdf_highlighting": 0.0,
        "total": 0.0,
    }

    try:
        # ==========================================
        # 1. 개인정보 마스킹
        # ==========================================
        start = time.perf_counter()

        masking_result = mask_pdf(pdf_bytes)

        timings["masking"] = round(
            time.perf_counter() - start, 6
        )

        print("개인정보 마스킹 완료!", flush=True)

        # 마스킹 경고가 있으면 외부 AI 호출 중단
        if masking_result.warnings:
            raise HTTPException(
                status_code=422,
                detail={
                    "message": "개인정보 마스킹 검토가 필요합니다.",
                    "warnings": masking_result.warnings,
                },
            )

        # ==========================================
        # 2. Gemini 11개 필드 추출
        # ==========================================
        start = time.perf_counter()

        result = analyze_contract_pdf(
            masking_result.pdf_bytes
        )

        data = result.model_dump(mode="json")

        timings["gemini_extraction"] = round(
            time.perf_counter() - start, 6
        )

        for field in ("monthlyAmount", "penaltyAmount"):
            if data.get(field) is not None:
                data[field] = int(data[field])

        print(
            f"Gemini 추출 category: {data.get('category')}",
            flush=True
        )

        # ==========================================
        # 3. 카테고리 라우팅
        # ==========================================
        routing = resolve_rag_category(data)

        timings["category_routing"] = routing["routing_time"]

        print(
            f"RAG category: {routing['category']}, "
            f"status: {routing['status']}",
            flush=True
        )

        rag_result = {
            "category": routing["category"],
            "status": routing["status"],
            "clauses": [],
        }

        legal_analysis_result = {
            "status": "SKIPPED",
            "results": [],
            "timings": {
                "gemini_legal_analysis": 0.0,
                "total": 0.0,
            },
        }

        highlight_result = {
            "highlighted_clause_ids": [],
            "highlight_count": 0,
            "processing_time": 0.0,
        }

        highlighted_pdf_bytes = masking_result.pdf_bytes

        # ==========================================
        # 4. RAG 검색 및 법적 검토
        # ==========================================
        if routing["status"] == "SUCCESS":

            # 4-1. PDF 텍스트 및 좌표 추출
            start = time.perf_counter()

            spans = extract_pdf_spans(
                masking_result.pdf_bytes
            )

            timings["pdf_parsing"] = round(
                time.perf_counter() - start, 6
            )

            # 4-2. 조항 추출
            start = time.perf_counter()

            clauses = extract_clauses(spans)

            timings["clause_extraction"] = round(
                time.perf_counter() - start, 6
            )

            print(
                f"계약 조항 추출 완료: {len(clauses)}개",
                flush=True
            )

            # 4-3. 조항별 법률 검색
            start = time.perf_counter()

            for clause in clauses:
                retrieval = retrieve_legal_rules(
                    query=clause.text,
                    category=routing["category"],
                    top_k=3,
                )

                rag_result["clauses"].append({
                    "clause_id": clause.clause_id,
                    "title": clause.title,
                    "text": clause.text,
                    "page": clause.page,
                    "pages": clause.pages,
                    "span_ids": clause.span_ids,
                    "bboxes": clause.bboxes,
                    "retrieval_status": retrieval["status"],
                    "legal_rules": retrieval["results"],
                    "retrieval_time": retrieval["retrieval_time"],
                })

            timings["legal_retrieval"] = round(
                time.perf_counter() - start, 6
            )

            matched_count = sum(
                1
                for clause in rag_result["clauses"]
                if clause["legal_rules"]
            )

            print(
                f"RAG 법률 검색 완료: "
                f"{matched_count}/{len(clauses)}개 조항 매칭",
                flush=True
            )

            # 4-4. Gemini 법적 검토
            if rag_result["clauses"]:

                legal_analysis_result = analyze_legal_clauses(
                    rag_result["clauses"]
                )

                timings["gemini_legal_analysis"] = (
                    legal_analysis_result["timings"][
                        "gemini_legal_analysis"
                    ]
                )

                timings["legal_analysis_total"] = (
                    legal_analysis_result["timings"]["total"]
                )

                review_count = sum(
                    1
                    for item in legal_analysis_result["results"]
                    if item["status"] == "REVIEW_NEEDED"
                )

                print(
                    f"Gemini 법적 검토 완료: "
                    f"검토 필요 {review_count}개",
                    flush=True
                )

                # ==========================================
                # 5. PDF 하이라이트
                # ==========================================
                highlight_result = highlight_review_clauses(
                    pdf_bytes=masking_result.pdf_bytes,
                    clauses=rag_result["clauses"],
                    legal_analysis_results=(
                        legal_analysis_result["results"]
                    ),
                )

                highlighted_pdf_bytes = (
                    highlight_result["pdf_bytes"]
                )

                timings["pdf_highlighting"] = (
                    highlight_result["processing_time"]
                )

                print(
                    f"PDF 하이라이트 완료: "
                    f"{highlight_result['highlight_count']}개 주석",
                    flush=True
                )

            else:
                legal_analysis_result["status"] = "NO_CLAUSES"

        else:
            print(
                "해당 카테고리 법률 데이터가 없어 "
                "RAG 검색 및 법적 검토를 건너뜁니다.",
                flush=True
            )

            legal_analysis_result["status"] = (
                routing["status"]
            )

        # ==========================================
        # 6. 분석 결과 및 PDF 저장
        # ==========================================
        analysis_dir.mkdir(
            parents=True,
            exist_ok=False
        )

        highlighted_pdf_path = (
            analysis_dir / "highlighted.pdf"
        )

        highlighted_pdf_path.write_bytes(
            highlighted_pdf_bytes
        )

        highlighted_pdf_url = (
            f"/analyze/{analysis_id}/highlighted-pdf"
        )

        response_data = {
            **data,
            "analysisId": analysis_id,
            "rag": rag_result,
            "legal_analysis": legal_analysis_result,
            "riskReports": build_risk_reports(
                rag_result["clauses"],
                legal_analysis_result["results"],
                highlighted_pdf_url,
            ),
            "highlight": {
                "highlightedClauseIds": (
                    highlight_result["highlighted_clause_ids"]
                ),
                "highlightCount": (
                    highlight_result["highlight_count"]
                ),
                "highlightedPdfUrl": highlighted_pdf_url,
            },
            "timings": timings,
        }

        result_path = analysis_dir / "result.json"

        # 저장 직전까지의 시간 측정
        timings["total"] = round(
            time.perf_counter() - total_start, 6
        )

        with open(
            result_path,
            "w",
            encoding="utf-8"
        ) as f:
            json.dump(
                response_data,
                f,
                ensure_ascii=False,
                indent=2,
            )

        # 실제 전체 응답 준비시간 반영
        timings["total"] = round(
            time.perf_counter() - total_start, 6
        )

        # 파일에도 최종 시간 반영
        with open(
            result_path,
            "w",
            encoding="utf-8"
        ) as f:
            json.dump(
                response_data,
                f,
                ensure_ascii=False,
                indent=2,
            )

        print(
            f"전체 분석 소요 시간: "
            f"{timings['total']:.2f}초",
            flush=True
        )

        print(
            f"분석 결과 저장: {analysis_dir}",
            flush=True
        )

        return response_data

    except HTTPException:
        raise

    except Exception as e:
        print(
            f"AI 분석 오류: {type(e).__name__}: {e}",
            flush=True
        )

        raise HTTPException(
            status_code=502,
            detail="계약서 분석에 실패했습니다."
        )


@app.get("/analyze/{analysis_id}/highlighted-pdf")
def download_highlighted_pdf(analysis_id: uuid.UUID):
    """
    분석 ID에 해당하는 하이라이트 PDF 다운로드.
    로컬 개발용 API이며, 운영 환경에서는 인증 및
    사용자별 접근 권한 확인이 필요하다.
    """
    pdf_path = (
        OUTPUT_DIR / str(analysis_id) / "highlighted.pdf"
    )

    if not pdf_path.is_file():
        raise HTTPException(
            status_code=404,
            detail="하이라이트 PDF를 찾을 수 없습니다."
        )

    return FileResponse(
        path=pdf_path,
        media_type="application/pdf",
        filename="highlighted_contract.pdf",
    )
