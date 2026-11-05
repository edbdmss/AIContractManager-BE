
import json
import os
import time
from pathlib import Path
from typing import Any

from fastapi import FastAPI, UploadFile, File, HTTPException
from pydantic import BaseModel, Field
from google import genai
from google.genai import types

from extraction.mllm_client import analyze_contract_pdf, gemini_api_key
from masking.pdf_masker import mask_pdf


app = FastAPI(
    title="AI Contract Analysis API",
    description="개인정보 마스킹 후 Gemini로 계약서를 분석하는 API",
    version="1.0.0",
)


AI_ENV = os.getenv("AI_ENV", "development").lower()


class ChatRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    history: list[dict[str, str]] = Field(default_factory=list, max_length=10)
    contractContext: list[dict[str, Any]] = Field(default_factory=list, max_length=20)


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

        # 2. 운영 환경에서는 마스킹 경고가 있으면 외부 전송을 차단한다.
        # 개발 환경에서는 경고를 로그로 남기고, 시연 및 통합 테스트를 위해
        # 마스킹된 PDF로 Gemini 분석을 계속 진행한다.
        if masking_result.warnings:
            for warning in masking_result.warnings:
                print(f"마스킹 경고: {warning}", flush=True)

            if AI_ENV == "production":
                raise HTTPException(
                    status_code=422,
                    detail={
                        "message": "개인정보 마스킹 검토가 필요합니다.",
                        "warnings": masking_result.warnings,
                    },
                )

            print(
                f"개발 환경({AI_ENV}): 마스킹 경고가 있지만 분석을 계속합니다.",
                flush=True,
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


@app.post("/chat")
def chat(request: ChatRequest):
    context = json.dumps(request.contractContext, ensure_ascii=False)
    prompt = f"""
너는 생활 계약 관리 서비스의 한국어 안내 챗봇이다.
사용자가 제공한 계약 정보와 대화 이력에 근거해 간결하고 실행 가능한 답변을 작성한다.

규칙:
- 계약 정보에 없는 금액, 날짜, 계약 조건은 추측하지 말고 정보가 없다고 말한다.
- 해지 절차나 마감일은 제공된 계약 정보에 있는 내용만 사실로 안내한다.
- 법률적 결론을 단정하지 않는다. 위험 여부는 확인이 필요한 가능성으로 설명하고 원문 조항을 함께 안내한다.
- 실제 계약 해지나 변경을 대신 처리했다고 말하지 않는다.
- 앱 사용법 질문에 근거가 없으면 모른다고 밝히고 필요한 상황을 물어본다.
- 개인정보를 요구하거나 계약 정보에 없는 민감정보를 추론하지 않는다.
- 답변은 한국어로 작성한다.

사용자 보유 계약 정보(JSON):
{context}
"""
    turns = []
    for item in request.history:
        role = "model" if item.get("role", "").upper() == "ASSISTANT" else "user"
        content = item.get("content", "")
        if content:
            turns.append(types.Content(role=role, parts=[types.Part.from_text(text=content[:4000])]))
    turns.append(types.Content(role="user", parts=[types.Part.from_text(text=request.question)]))

    try:
        client = genai.Client(
            api_key=gemini_api_key(),
            http_options=types.HttpOptions(
                timeout=60000,
                retry_options=types.HttpRetryOptions(attempts=1),
            ),
        )
        response = client.models.generate_content(
            model=os.getenv("GEMINI_CHAT_MODEL", "gemini-3.5-flash-lite"),
            contents=turns,
            config=types.GenerateContentConfig(system_instruction=prompt, temperature=0.2),
        )
        if not response.text:
            raise ValueError("Gemini가 빈 챗봇 응답을 반환했습니다.")
        return {"answer": response.text.strip()}
    except Exception as e:
        print(f"챗봇 응답 오류: {type(e).__name__}: {e}", flush=True)
        raise HTTPException(status_code=502, detail="챗봇 응답을 생성하지 못했습니다.")
