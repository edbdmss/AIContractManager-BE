
import json
import time
from pathlib import Path

from fastapi import FastAPI, UploadFile, File, HTTPException

from extraction.mllm_client import analyze_contract_pdf
from masking.pdf_masker import mask_pdf


app = FastAPI(
    title="AI Contract Analysis API",
    description="개인정보 마스킹 후 Gemini로 계약서를 분석하는 API",
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

        # 1. 로컬 개인정보 마스킹
        masking_result = mask_pdf(pdf_bytes)

        # 개발용: 마스킹 결과 확인
        Path("masked_test.pdf").write_bytes(
            masking_result.pdf_bytes
        )

        print("개인정보 마스킹 처리 완료!", flush=True)
        print(
            f"마스킹 대상 문자열 수: "
            f"{len(masking_result.masked_values)}",
            flush=True
        )

        # 2. 불명확한 항목이나 마스킹 실패 시 외부 전송 차단
        if masking_result.warnings:
            for warning in masking_result.warnings:
                print(f"마스킹 경고: {warning}", flush=True)

            raise HTTPException(
                status_code=422,
                detail={
                    "message": "개인정보 마스킹 검토가 필요합니다.",
                    "warnings": masking_result.warnings,
                },
            )

        # 3. 마스킹된 PDF만 Gemini에 전달
        print("Gemini PDF 분석 시작!", flush=True)

        result = analyze_contract_pdf(
            masking_result.pdf_bytes
        )

        elapsed = time.perf_counter() - start

        print(
            f"전체 분석 소요 시간: {elapsed:.2f}초",
            flush=True
        )

        # 4. Pydantic 모델을 JSON 변환
        data = result.model_dump(mode="json")

        for field in ("monthlyAmount", "penaltyAmount"):
            if data[field] is not None:
                data[field] = int(data[field])

        # 개발용 JSON 저장
        with open(
            "analysis_result.json",
            "w",
            encoding="utf-8"
        ) as f:
            json.dump(
                data,
                f,
                ensure_ascii=False,
                indent=2
            )

        print("분석 결과 저장 완료!", flush=True)

        return data

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
