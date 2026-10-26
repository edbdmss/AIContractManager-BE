
import json
import time

from fastapi import FastAPI, UploadFile, File, HTTPException
from extraction.mllm_client import analyze_contract_pdf

app = FastAPI(
    title="AI Contract Analysis API",
    description="Gemini를 활용한 계약서 핵심 정보 추출 API",
    version="1.0.0",
)


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

    try:
        start = time.perf_counter()

        print("Gemini PDF 직접 전송 시작!", flush=True)
        result = analyze_contract_pdf(pdf_bytes)

        elapsed = time.perf_counter() - start
        print(f"Gemini 분석 완료! 소요 시간: {elapsed:.2f}초", flush=True)

    except Exception as e:
        print(f"AI 분석 오류: {type(e).__name__}: {e}", flush=True)
        raise HTTPException(
            status_code=502,
            detail="AI 계약서 분석에 실패했습니다."
        )

    data = result.model_dump(mode="json")

    for field in ("monthlyAmount", "penaltyAmount"):
        if data[field] is not None:
            data[field] = int(data[field])

    with open("analysis_result.json", "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print("분석 결과 저장 완료!", flush=True)

    return data
