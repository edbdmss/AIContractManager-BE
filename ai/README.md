AI 서버 

## 로컬 실행

AI 서버는 Spring Boot와 별도의 프로세스로 실행합니다. Spring Boot가 `http://localhost:8000/analyze`로 PDF를 전달하고, AI 서버가 분석 결과 JSON을 반환합니다.

PowerShell 기준:

```powershell
cd AIContractManager-BE\ai
python -m venv capstone_ai
.\capstone_ai\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
notepad .env
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

`.env`에는 다음처럼 키를 입력합니다.

```text
GEMINI_API_KEY=실제_Gemini_API_키
```

Spring Boot는 별도 터미널에서 실행합니다.

```powershell
cd AIContractManager-BE
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

연결 확인:

```powershell
Invoke-WebRequest http://localhost:8000/health
```

응답에 `{"status":"ok"}`가 나오면 AI 서버가 실행된 상태입니다. 이후 웹에서 계약서 분석을 요청하면 Spring Boot가 업로드된 문서를 AI 서버로 전달합니다.

1. 주요 기능

1.1 개인정보 마스킹

PyMuPDF 기반 텍스트 PDF 처리
주민등록번호, 전화번호, 이메일 등 개인정보 탐지 및 마스킹

1.2 AI 계약서 분석

Google Gemini를 활용한 계약 정보 추출
계약 기간, 납부 금액, 해지 조건 등 11개 필드 반환
확인할 수 없는 선택 항목은 null로 반환
Pydantic을 통한 응답 데이터 형식 검증



2. 지원 범위

현재: 텍스트 기반 PDF 개인정보 마스킹
향후: 스캔 PDF OCR 및 개인정보 마스킹



3. 프로젝트 구조


```text
ai/
├── main.py
├── requirements.txt
├── README.md
├── extraction/
│   ├── mllm_client.py
│   ├── pdf_processor.py
│   └── schema.py
└── masking/
    └── pdf_masker.py
```
    
  
4. API 명세

FastAPI 기반 AI 서버는 계약서 PDF를 입력받아 개인정보를 마스킹한 후, Gemini를 통해 계약 정보를 추출하고 JSON 형식으로 반환

4.1. 서버 상태 확인

GET /health
AI 서버의 정상 실행 여부를 확인

응답 예시
{
  "status": "ok"
}


4.2. 계약서 분석

POST /analyze
텍스트 기반 PDF 계약서를 업로드하면 
개인정보 마스킹 및 AI 분석을 수행하고 
계약 정보 11개 필드를 반환


요청 형식
- HTTP Method : POST
- Endpoint : /analyze
- Content-Type : multipart/form-data
- 파일 필드명 : file 
- 지원 형식 : 텍스트 기반 PDF 


처리 과정
1. 업로드된 PDF 파일 형식 및 빈 파일 여부 확인
2. PyMuPDF 기반 개인정보 탐지 및 마스킹
3. 마스킹 경고 발생 시 분석 중단
4. 마스킹된 PDF를 Gemini에 전달하여 계약 정보 추출
5. Pydantic을 통한 응답 데이터 형식 검증
6. 계약 정보 11개 필드를 JSON으로 반환


응답 예시 

``` json
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

응답 필드 
- `title` : 계약서 제목
- `category` : 계약 종류
- `providerName` : 계약 상대 업체명
- `contractStartDate` : 계약 시작일
- `contractEndDate` : 계약 종료일
- `autoRenewalDate` : 자동 갱신일
- `cancellationDeadline` : 해지 신청 마감일
- `monthlyAmount` : 월 납부 금액
- `penaltyAmount` : 명시된 위약금
- `cancellationTerms` : 해지 및 위약금 관련 조건
- `plainSummary` : 계약 핵심 내용 요약


응답 필드 주의사항
- `title`, `category`는 필수 필드입니다.
- 나머지 선택 필드는 확인할 수 없는 경우 `null`로 반환할 수 있습니다.
- 날짜는 `YYYY-MM-DD` 형식으로 반환합니다.
- 금액은 원 단위 숫자로 반환합니다.


오류 응답
- `400 Bad Request` : PDF가 아닌 파일 또는 빈 파일 업로드
- `422 Unprocessable Entity` : 개인정보 마스킹 검토가 필요한 경우

개발 환경에서는 개인정보 마스킹 경고가 발생해도 경고를 로그에 남긴 후 분석을 계속합니다.
운영 환경에서 마스킹 경고를 분석 중단으로 처리하려면 다음처럼 실행합니다.

```bash
AI_ENV=production python -m uvicorn main:app --port 8000
```
- `502 Bad Gateway` : 계약서 AI 분석 처리 중 오류 발생



4.3. 개발용 결과 확인

개발 및 테스트 과정에서 분석 결과를 확인하기 위해 다음 파일을 로컬에 저장합니다.

- `masked_test.pdf`: 개인정보 마스킹 결과 PDF
- `analysis_result.json`: Gemini 계약서 분석 결과 JSON

해당 파일은 `.gitignore`에 등록되어 Git 관리 대상에서 제외됩니다.

실제 API 응답은 `analysis_result.json` 파일을 전달하는 방식이 아니라, 분석 결과 데이터를 HTTP JSON 응답으로 직접 반환합니다.
