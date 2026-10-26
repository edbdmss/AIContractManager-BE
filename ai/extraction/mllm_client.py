
import os
import io
import json

from PIL import Image
from google import genai
from google.genai import types

from extraction.schema import ContractExtraction


PROMPT = """
너는 한국어 계약서의 핵심 정보를 추출하는 AI다.

입력된 계약서 이미지 전체를 읽고 다음 11개 필드를 추출해라.

- title: 계약서 제목
- category: 계약 종류 (예: FITNESS, TELECOM, HOUSING, INSURANCE, RENTAL, OTHER)
- providerName: 계약 상대 업체명
- contractStartDate: 계약 시작일
- contractEndDate: 계약 종료일
- autoRenewalDate: 자동 갱신일
- cancellationDeadline: 해지 신청 마감일
- monthlyAmount: 월 납부 금액 (원)
- penaltyAmount: 명시된 위약금 (원)
- cancellationTerms: 해지 및 위약금 관련 조건
- plainSummary: 계약 핵심 내용을 한국어로 2~3문장 요약

규칙:
1. 계약서에 없는 정보는 추측하지 말고 null로 작성한다.
2. 날짜는 YYYY-MM-DD 형식으로 작성한다.
3. 금액은 숫자로 작성하며 원, 쉼표를 포함하지 않는다.
4. 위약금이 명시되지 않았다면 0이 아니라 null이다.
5. 자동 갱신 조건만 있고 정확한 날짜가 없다면 autoRenewalDate는 null이다.
6. title과 category는 필수이며, 제목이 명확하지 않으면 문서 유형을 바탕으로
   일반적인 제목을 생성한다.
7. JSON 객체만 반환한다.
"""


def analyze_contract_images(images: list[Image.Image]) -> ContractExtraction:
    if not images:
        raise ValueError("분석할 계약서 이미지가 없습니다.")

    client = genai.Client(
        api_key=os.environ["GEMINI_API_KEY"],
        http_options=types.HttpOptions(
            timeout=60000,  # 60초 (밀리초 단위)
            retry_options=types.HttpRetryOptions(attempts=1),
        ),
)

    image_parts = []

    for image in images:
        buffer = io.BytesIO()
        image.convert("RGB").save(
            buffer,
            format="JPEG",
            quality=80
        )

        image_parts.append(
            types.Part.from_bytes(
                data=buffer.getvalue(),
                mime_type="image/jpeg"
            )
        )

    response = client.models.generate_content(
        model="gemini-3.5-flash-lite",
        contents=[
            types.Content(
                role="user",
                parts=[
                    types.Part.from_text(text=PROMPT),
                    *image_parts,
                ],
            )
        ],
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            temperature=0,
        ),
    )

    if not response.text:
        raise ValueError("Gemini가 빈 응답을 반환했습니다.")

    result = json.loads(response.text)

    return ContractExtraction.model_validate(result)

def analyze_contract_pdf(pdf_bytes: bytes) -> ContractExtraction:
    if not pdf_bytes:
        raise ValueError("분석할 PDF가 없습니다.")

    client = genai.Client(
        api_key=os.environ["GEMINI_API_KEY"],
        http_options=types.HttpOptions(
            timeout=60000,
            retry_options=types.HttpRetryOptions(attempts=1),
        ),
    )

    pdf_part = types.Part.from_bytes(
        data=pdf_bytes,
        mime_type="application/pdf",
    )

    print("Gemini PDF 직접 분석 요청 시작!", flush=True)

    response = client.models.generate_content(
        model="gemini-3.5-flash-lite",
        contents=[
            types.Content(
                role="user",
                parts=[
                    types.Part.from_text(text=PROMPT),
                    pdf_part,
                ],
            )
        ],
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            temperature=0,
        ),
    )

    if not response.text:
        raise ValueError("Gemini가 빈 응답을 반환했습니다.")

    result = json.loads(response.text)
    return ContractExtraction.model_validate(result)