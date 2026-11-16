# AI 서버 (FastAPI)

계약서 PDF의 개인정보를 마스킹하고, Google Gemini로 계약 정보를 추출하며, 법률 RAG를 이용해 위험 조항을 분석하는 서버입니다. 위험 조항이 표시된 PDF도 제공합니다.

## 1. 서비스 연동 구조

```text
React Native → Spring Boot → FastAPI(AI 서버)
             ← 분석 결과 JSON ←
```

1. 사용자가 앱에서 계약서 PDF를 업로드합니다.
2. Spring Boot가 AI 서버의 `POST /analyze`로 PDF를 전달합니다.
3. AI 서버가 개인정보 마스킹, 계약 정보 추출, 법률 RAG 분석을 수행합니다.
4. AI 서버가 분석 결과를 JSON으로 반환합니다.
5. Spring Boot가 결과를 저장하거나 가공하여 프론트엔드에 제공합니다.

> 프론트엔드는 원칙적으로 Spring Boot API와 연동합니다. AI 서버 응답과 프론트엔드가 받는 최종 응답의 구조는 다를 수 있습니다.

## 2. 주요 기능

### 2.1 개인정보 마스킹

- PyMuPDF 기반 텍스트 PDF 처리
- 주민등록번호, 전화번호, 이메일 등 개인정보 탐지 및 마스킹
- 마스킹된 문서를 AI 분석에 사용

### 2.2 계약 정보 추출 및 요약

- Google Gemini로 계약 정보 11개 필드 추출
- `plainSummary`로 계약서 핵심 내용 요약
- Pydantic 기반 응답 데이터 검증
- 확인되지 않은 선택 항목은 `null`로 반환 가능

### 2.3 법률 RAG 기반 위험 조항 분석

- 계약서 카테고리 확인 및 조항 추출
- 카테고리별 법률 데이터 검색
- Gemini를 통한 조항별 위험성 및 판단 근거 분석
- 위험 조항 PDF 하이라이트

지원 카테고리:

- `FITNESS`: 헬스장 등 체육시설 이용 계약
- `TELECOM`: 통신 서비스 계약
- `RENTAL`: 렌털 서비스 계약
- `HOUSING`: 주택 임대차 계약
- `INSURANCE`: 보험 계약

법률 데이터는 `data/legal/<카테고리>/legal_rules.json`에서 관리합니다.

## 3. 로컬 실행

### 3.1 Python 환경 구성

프로젝트 루트에서 PowerShell로 실행합니다.

```powershell
cd AIContractManager-BE\ai
python -m venv capstone_ai
.\capstone_ai\Scripts\Activate.ps1
pip install -r requirements.txt
```

### 3.2 Gemini API 키 설정

`.env.example`이 있다면 다음과 같이 복사합니다.

```powershell
Copy-Item .env.example .env
notepad .env
```

`.env` 내용:

```dotenv
GEMINI_API_KEY=실제_Gemini_API_키
```

현재 실행 환경에서 `.env` 자동 로딩이 설정되어 있지 않다면, 서버 실행 전에 PowerShell 환경변수로 지정합니다.

```powershell
$env:GEMINI_API_KEY="실제_Gemini_API_키"
```


### 3.3 AI 서버 실행

```powershell
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

- API 문서: `http://localhost:8000/docs`
- 상태 확인: `http://localhost:8000/health`

```powershell
Invoke-WebRequest http://localhost:8000/health
```

정상 응답 예시:

```json
{"status":"ok"}
```

### 3.4 Spring Boot 실행

별도 터미널에서 프로젝트 루트로 이동하여 실행합니다.

```powershell
cd AIContractManager-BE
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## 4. 프로젝트 구조

```text
ai/
├── main.py
├── requirements.txt
├── README.md
├── extraction/
│   ├── mllm_client.py
│   ├── pdf_processor.py
│   └── schema.py
├── masking/
│   └── pdf_masker.py
├── rag/
│   ├── category_router.py
│   ├── clause_extractor.py
│   ├── legal_retriever.py
│   ├── legal_analyzer.py
│   ├── pdf_parser.py
│   └── pdf_highlighter.py
└── data/
    └── legal/
        ├── fitness/legal_rules.json
        ├── telecom/legal_rules.json
        ├── rental/legal_rules.json
        ├── housing/legal_rules.json
        └── insurance/legal_rules.json
```

## 5. API

### 5.1 서버 상태 확인

- **Method:** `GET`
- **Endpoint:** `/health`
- **응답 예시:** `{"status":"ok"}`

### 5.2 계약서 분석

- **Method:** `POST`
- **Endpoint:** `/analyze`
- **Content-Type:** `multipart/form-data`
- **파일 필드:** `file`
- **지원 파일:** 텍스트 기반 PDF

처리 흐름:

1. PDF 형식 및 빈 파일 검증
2. 개인정보 탐지 및 마스킹
3. Gemini 계약 정보 추출
4. 계약 유형 및 조항 확인
5. 관련 법률 RAG 검색
6. 위험 조항 분석 및 PDF 하이라이트
7. 분석 결과 JSON 반환

### 5.3 계약 정보 11개 필드

**계약서 기본 정보**

- `title`: 계약서 제목
- `category`: 계약 종류
- `providerName`: 계약 상대 업체명

**계약 일정**

- `contractStartDate`: 계약 시작일
- `contractEndDate`: 계약 종료일
- `nextPaymentDate`: 구독 다음 결제일 또는 계약 자동 갱신일
- `cancellationDeadline`: 해지 신청 마감일

**계약 금액 및 요약**

- `monthlyAmount`: 월 납부 금액(원)
- `penaltyAmount`: 위약금(원)
- `cancellationTerms`: 해지 및 위약금 관련 조건
- `plainSummary`: 계약 핵심 내용 요약

계약 정보 예시(전체 `/analyze` 응답이 아닌 **11개 추출 필드만** 표시):

```json
{
  "title": "헬스장 이용 계약서",
  "category": "FITNESS",
  "providerName": "ABC 피트니스",
  "contractStartDate": "2026-11-01",
  "contractEndDate": "2027-10-31",
  "nextPaymentDate": null,
  "cancellationDeadline": null,
  "monthlyAmount": 50000,
  "penaltyAmount": null,
  "cancellationTerms": "중도 해지 시 위약금 발생",
  "plainSummary": "12개월 헬스장 이용 계약입니다."
}
```

- 날짜는 `YYYY-MM-DD` 형식을 사용합니다.
- 금액은 원 단위 숫자입니다.
- 확인할 수 없는 선택 항목은 `null`일 수 있습니다.

### 5.4 하이라이트 PDF 조회

- **Method:** `GET`
- **Endpoint:** `/analyze/{analysisId}/highlighted-pdf`
- **설명:** 분석 식별자에 연결된 하이라이트 PDF 조회

실제 응답 코드와 파일 제공 방식은 `main.py` 구현을 기준으로 확인합니다.

## 6. 프론트엔드 연동 가이드

### 6.1 데이터별 활용 기능

**계약서 요약 화면**

- `title`, `category`, `providerName` → 계약서 기본 정보
- `contractStartDate`, `contractEndDate` → 계약 기간
- `nextPaymentDate`, `cancellationDeadline` → 결제·갱신·해지 일정
- `monthlyAmount`, `penaltyAmount` → 납부 금액·위약금
- `cancellationTerms` → 해지 조건
- `plainSummary` → **AI 계약서 핵심 요약**

**위험 조항 분석 화면**

- `riskReports` → 조항별 위험 분석 결과
- `legal_analysis` → 법률 분석 정보
- `rag` → 관련 법률 검색 근거

**PDF 확인 및 개발용 정보**

- `analysisId`, `highlight` → 하이라이트 PDF 연결 관련 정보
- `timings` → 단계별 처리시간(개발·성능 확인용)

> 위 항목은 데이터의 용도 설명입니다. `riskReports`, `legal_analysis`, `rag`, `highlight` 등의 실제 중첩 위치와 세부 필드명은 현재 FastAPI 응답 및 Spring Boot API 명세를 기준으로 확인해야 합니다.

### 6.2 화면 구현 시 주의사항

- 프론트엔드는 **Spring Boot가 반환하는 최종 JSON 구조**를 기준으로 연동합니다.
- `null`인 항목은 `정보 없음` 등으로 처리합니다.
- 위험 조항이 없는 경우 빈 목록 상태를 표시합니다.
- 법률 근거가 부족한 결과를 확정적인 위법 판단으로 표현하지 않습니다.
- 분석 중 로딩 상태와 오류 상태를 처리합니다.

## 7. 오류 및 실행 환경

- 잘못된 PDF 또는 빈 파일 업로드 시 오류가 발생할 수 있습니다.
- 개인정보 마스킹 검토가 필요하면 경고 또는 오류가 발생할 수 있습니다.
- Gemini 요청 실패 및 PDF 조회 실패에 대한 예외 처리가 필요합니다.
- 정확한 HTTP 상태 코드는 `main.py` 구현을 따릅니다.

운영 환경에서 마스킹 경고 시 분석을 중단하도록 설정하는 경우:

```powershell
$env:AI_ENV="production"
python -m uvicorn main:app --port 8000
```

## 8. 지원 범위 및 향후 계획

**현재 기능**

- 텍스트 PDF 개인정보 마스킹
- Gemini 계약 정보 추출 및 요약
- 5개 카테고리 법률 RAG
- 위험 조항 분석 및 PDF 하이라이트

**향후 계획**

- 스캔 PDF OCR
- 구독 서비스 정책 데이터 구축
- 구독 정책 기반 챗봇(계약서 법률 RAG와 별도 기능)

## 9. 참고

AI 위험 조항 분석은 계약 검토를 돕기 위한 참고 정보이며, 법률 전문가의 최종 판단을 대체하지 않습니다.
