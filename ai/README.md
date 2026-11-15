# AI 서버

계약서 PDF를 분석하여 개인정보를 마스킹하고, Google Gemini를 활용해 계약 정보를 추출하는 FastAPI 기반 AI 서버입니다.

법률 RAG(Retrieval-Augmented Generation)를 활용하여 계약서의 위험 조항을 분석하고, 관련 법률 근거와 위험 조항이 표시된 PDF를 제공합니다.

## 1. 시스템 구성

AI 서버는 Spring Boot와 별도의 프로세스로 실행됩니다.

**서비스 연동 구조**

`React Native → Spring Boot → FastAPI → Spring Boot → React Native`

1. 사용자가 앱에서 계약서 PDF를 업로드합니다.
2. Spring Boot가 AI 서버의 `/analyze` API로 PDF를 전달합니다.
3. AI 서버가 개인정보 마스킹, 계약 정보 추출, 법률 RAG 분석을 수행합니다.
4. 분석 결과를 JSON 형식으로 Spring Boot에 반환합니다.
5. Spring Boot가 필요한 데이터를 저장하고 프론트엔드에 제공합니다.
6. 프론트엔드는 전달받은 데이터를 계약서 요약 및 위험 조항 화면에 표시합니다.

※ 프론트엔드는 AI 서버를 직접 호출하는 것이 아니라 Spring Boot API를 통해 분석 결과를 전달받는 구조입니다.

## 2. 주요 기능

### 2.1 개인정보 마스킹

- PyMuPDF 기반 텍스트 PDF 처리
- 주민등록번호, 전화번호, 이메일 등 개인정보 탐지 및 마스킹
- 개인정보 마스킹 후 Gemini 분석 수행

### 2.2 AI 계약 정보 추출

Google Gemini를 활용하여 계약서의 주요 정보를 구조화된 데이터로 추출합니다.

- 계약서 제목 및 종류
- 계약 상대 업체명
- 계약 시작일 및 종료일
- 자동 갱신일 및 해지 신청 마감일
- 월 납부 금액 및 위약금
- 해지 조건
- 계약서 핵심 요약

총 11개 필드를 반환하며, 확인할 수 없는 선택 항목은 `null`로 반환할 수 있습니다.

Pydantic을 활용하여 응답 데이터 형식을 검증합니다.

### 2.3 법률 RAG 기반 위험 조항 분석

계약서에 포함된 조항을 추출하고, 계약 유형에 맞는 법률 데이터를 검색하여 위험성을 분석합니다.

**분석 과정**

1. 계약서 유형 분류
2. 계약 조항 추출
3. 카테고리별 관련 법률 검색
4. Gemini를 활용한 위험 조항 분석
5. 위험도 및 판단 근거 생성
6. 법률 분석 결과 반환

**지원 계약 카테고리**

| 카테고리 | 설명 |
|---|---|
| `FITNESS` | 헬스장 등 체육시설 이용 계약 |
| `TELECOM` | 통신 서비스 계약 |
| `RENTAL` | 렌털 서비스 계약 |
| `HOUSING` | 주택 임대차 계약 |
| `INSURANCE` | 보험 계약 |

법률 데이터는 `data/legal/` 디렉터리에서 카테고리별 JSON 파일로 관리합니다.

### 2.4 위험 조항 PDF 하이라이트

- PDF 내 계약 조항의 텍스트 위치 탐색
- 위험 조항과 PDF 원문 연결
- 위험 조항 하이라이트 처리
- 분석 결과에 연결된 하이라이트 PDF 제공

## 3. 로컬 실행

### 3.1 가상환경 생성 및 패키지 설치

PowerShell 기준:

```powershell
cd AIContractManager-BE\ai

python -m venv capstone_ai
.\capstone_ai\Scripts\Activate.ps1

pip install -r requirements.txt
```

### 3.2 환경변수 설정

`.env.example` 파일이 있는 경우 복사하여 `.env`를 생성합니다.

```powershell
Copy-Item .env.example .env
notepad .env
```

`.env` 내용:

```text
GEMINI_API_KEY=실제_Gemini_API_키
```

`.env` 파일을 자동으로 로드하지 않는 실행 환경에서는 PowerShell 환경변수로 직접 설정합니다.

```powershell
$env:GEMINI_API_KEY="실제_Gemini_API_키"
```

API 키가 포함된 `.env` 파일은 Git에 업로드하지 않습니다.

### 3.3 AI 서버 실행

```powershell
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

서버 주소:

`http://localhost:8000`

Swagger API 문서:

`http://localhost:8000/docs`

### 3.4 Spring Boot 실행

별도 터미널에서 실행합니다.

```powershell
cd AIContractManager-BE
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 3.5 연결 확인

```powershell
Invoke-WebRequest http://localhost:8000/health
```

정상 응답 예시:

```json
{
  "status": "ok"
}
```

## 4. 프로젝트 구조

```text
ai/
├── main.py
├── requirements.txt
├── README.md
├── .gitignore
│
├── extraction/
│   ├── mllm_client.py
│   ├── pdf_processor.py
│   └── schema.py
│
├── masking/
│   └── pdf_masker.py
│
├── rag/
│   ├── category_router.py
│   ├── clause_extractor.py
│   ├── legal_retriever.py
│   ├── legal_analyzer.py
│   ├── pdf_parser.py
│   └── pdf_highlighter.py
│
└── data/
    └── legal/
        ├── fitness/
        │   └── legal_rules.json
        ├── telecom/
        │   └── legal_rules.json
        ├── rental/
        │   └── legal_rules.json
        ├── housing/
        │   └── legal_rules.json
        └── insurance/
            └── legal_rules.json
```

## 5. API 명세

### 5.1 서버 상태 확인

**GET `/health`**

AI 서버의 정상 실행 여부를 확인합니다.

응답 예시:

```json
{
  "status": "ok"
}
```

### 5.2 계약서 분석

**POST `/analyze`**

계약서 PDF를 업로드하면 개인정보 마스킹, 계약 정보 추출, 법률 RAG 기반 위험 분석을 수행합니다.

**요청 형식**

| 항목 | 값 |
|---|---|
| HTTP Method | `POST` |
| Endpoint | `/analyze` |
| Content-Type | `multipart/form-data` |
| 파일 필드명 | `file` |
| 지원 파일 | 텍스트 기반 PDF |

**처리 과정**

1. PDF 파일 형식 및 빈 파일 여부 검증
2. 개인정보 탐지 및 마스킹
3. Gemini 기반 계약 정보 추출
4. 계약 카테고리 확인
5. 계약 조항 추출
6. 관련 법률 RAG 검색
7. Gemini 기반 위험 조항 분석
8. 위험 조항 PDF 하이라이트 생성
9. 분석 결과 JSON 반환

### 5.3 계약 정보 추출 필드

계약서 요약 및 등록에 활용하는 기본 데이터는 다음 11개 필드입니다.

| 필드명 | 자료형 | 설명 |
|---|---|---|
| `title` | string | 계약서 제목 |
| `category` | string | 계약 종류 |
| `providerName` | string / null | 계약 상대 업체명 |
| `contractStartDate` | string / null | 계약 시작일 |
| `contractEndDate` | string / null | 계약 종료일 |
| `autoRenewalDate` | string / null | 자동 갱신일 |
| `cancellationDeadline` | string / null | 해지 신청 마감일 |
| `monthlyAmount` | number / null | 월 납부 금액 |
| `penaltyAmount` | number / null | 위약금 |
| `cancellationTerms` | string / null | 해지 및 위약금 관련 조건 |
| `plainSummary` | string / null | 계약서 핵심 요약 |

**계약 정보 예시**

```json
{
  "title": "헬스장 이용 계약서",
  "category": "FITNESS",
  "providerName": "ABC 피트니스",
  "contractStartDate": "2026-11-01",
  "contractEndDate": "2027-10-31",
  "autoRenewalDate": null,
  "cancellationDeadline": null,
  "monthlyAmount": 50000,
  "penaltyAmount": null,
  "cancellationTerms": "중도 해지 시 위약금 발생",
  "plainSummary": "12개월 헬스장 이용 계약입니다."
}
```

※ 위 JSON은 계약 정보 11개 필드의 예시이며, 전체 `/analyze` 응답 구조를 나타내는 것은 아닙니다.

**필드 처리 기준**

- 날짜: `YYYY-MM-DD`
- 금액: 원 단위 숫자
- 확인할 수 없는 선택 항목: `null`
- `title`, `category`: 필수 필드

## 6. 프론트엔드 연동 가이드

이 섹션은 React Native 프론트엔드에서 계약서 요약, 위험 조항 분석 및 PDF 확인 화면을 구현할 때 참고하기 위한 설명입니다.

### 6.1 응답 데이터별 활용 기능

| 데이터 | 설명 | 프론트엔드 활용 |
|---|---|---|
| `title`, `category`, `providerName` | 계약서 제목, 종류, 업체명 | 계약서 기본 정보 |
| `contractStartDate`, `contractEndDate` | 계약 시작일과 종료일 | 계약 기간 |
| `autoRenewalDate`, `cancellationDeadline` | 자동 갱신일과 해지 신청 마감일 | 계약 일정 및 알림 |
| `monthlyAmount`, `penaltyAmount` | 납부 금액과 위약금 | 계약 비용 |
| `cancellationTerms` | 해지 및 위약금 조건 | 해지 조건 안내 |
| `plainSummary` | AI가 생성한 계약 핵심 요약 | 계약서 요약 화면 |
| `riskReports` | 조항별 위험 분석 결과 | 위험 조항 목록 및 상세 |
| `legal_analysis` | 법률 분석 정보 | 법률 판단 근거 |
| `rag` | 관련 법률 검색 정보 | 참고 법률 및 근거 |
| `analysisId`, `highlight` | 분석 식별자 및 하이라이트 관련 정보 | 하이라이트 PDF 연결 |
| `timings` | 단계별 처리시간 | 개발 및 성능 확인 |

※ 위 표는 데이터의 용도를 설명합니다. 각 항목의 실제 JSON 중첩 위치와 세부 자료형은 FastAPI의 현재 응답 스키마를 기준으로 확인해야 합니다.

### 6.2 계약서 요약 화면

계약서 요약 화면에서는 다음 정보를 표시할 수 있습니다.

**기본 정보**
- 계약서 제목
- 계약 유형
- 계약 상대 업체명

**계약 일정**
- 계약 시작일
- 계약 종료일
- 자동 갱신일
- 해지 신청 마감일

**계약 비용**
- 월 납부 금액
- 위약금

**AI 핵심 요약**
- `plainSummary`를 활용한 계약서 요약 문장
- `cancellationTerms`를 활용한 해지 조건 안내

선택 항목이 `null`인 경우에는 `정보 없음`, `계약서에 명시되지 않음` 등으로 표시할 수 있습니다.

### 6.3 위험 조항 분석 화면

`riskReports` 데이터를 활용하여 계약서의 위험 조항을 목록 형태로 표시할 수 있습니다.

화면 구성 예시:

- 위험 조항 제목 또는 내용
- 위험도
- 위험하다고 판단한 이유
- 관련 법률 근거
- 사용자에게 필요한 확인 사항

실제 표시 항목은 `riskReports` 내부에 반환되는 필드를 기준으로 구성합니다.

`legal_analysis`와 `rag` 데이터는 관련 법률 근거를 표시하는 데 활용할 수 있습니다.

### 6.4 PDF 하이라이트 화면

위험 조항을 확인할 수 있도록 하이라이트된 PDF를 제공합니다.

**GET `/analyze/{analysisId}/highlighted-pdf`**

`analysisId`를 이용하여 분석 결과에 연결된 하이라이트 PDF를 조회합니다.

프론트엔드는 Spring Boot와 연동하여 PDF 조회 기능을 구현합니다.

### 6.5 프론트엔드 연동 시 주의사항

1. React Native는 Spring Boot API를 통해 분석 결과를 전달받습니다.
2. Spring Boot가 반환하는 최종 JSON 구조를 기준으로 데이터를 매핑합니다.
3. `null`이 가능한 필드는 화면에서 예외 처리합니다.
4. 위험 조항이 없는 경우 빈 목록을 처리합니다.
5. 법률 근거가 부족한 경우 이를 확정적인 위법 판단으로 표시하지 않습니다.
6. AI 분석 중에는 로딩 및 오류 상태를 처리합니다.

## 7. 오류 처리

주요 오류 상황은 다음과 같습니다.

| 상황 | 설명 |
|---|---|
| 잘못된 파일 업로드 | PDF가 아닌 파일 또는 빈 파일 |
| 개인정보 마스킹 경고 | 추가 검토가 필요한 개인정보 탐지 |
| Gemini 분석 실패 | 외부 AI 요청 또는 응답 처리 오류 |
| PDF 조회 실패 | 분석 결과 또는 하이라이트 PDF를 찾을 수 없는 경우 |

구체적인 HTTP 상태 코드는 `main.py`의 예외 처리 구현을 기준으로 합니다.

개발 환경에서는 개인정보 마스킹 경고가 발생해도 로그에 남기고 분석을 계속할 수 있습니다.

운영 환경에서 마스킹 경고 발생 시 분석을 중단하려면 다음처럼 실행합니다.

```powershell
$env:AI_ENV="production"
python -m uvicorn main:app --port 8000
```

## 8. 테스트 및 개발 결과물

개발 과정에서 생성되는 테스트 코드와 분석 결과물은 로컬에서 관리합니다.

주요 개발 결과물:

- 개인정보 마스킹 PDF
- 계약 정보 추출 JSON
- 법률 RAG 검색 결과
- 위험 조항 분석 결과
- 하이라이트 PDF
- 분석 단계별 처리시간

테스트 코드, 임시 PDF, 분석 결과 파일, 가상환경 및 API 키는 Git에 포함하지 않습니다.

실제 API는 분석 결과 데이터를 HTTP JSON 응답으로 반환합니다.

## 9. 현재 지원 범위

**현재 구현된 기능**

- 텍스트 기반 PDF 개인정보 마스킹
- Gemini 기반 계약 정보 추출
- 5개 계약 카테고리별 법률 RAG
- 계약 조항 위험성 분석
- 위험 조항 PDF 하이라이트

**향후 개발 예정**

- 스캔 PDF OCR 지원
- 구독 서비스 정책 데이터 구축
- 구독 정책 기반 AI 챗봇

구독 서비스 정책 챗봇은 계약서 PDF의 법률 RAG 분석과 별도 기능으로 개발할 예정입니다.

## 10. 참고 사항

AI가 제공하는 위험 조항 분석은 계약 검토를 보조하기 위한 정보이며, 법적 효력이나 위법 여부를 최종 확정하는 법률 자문을 대체하지 않습니다.