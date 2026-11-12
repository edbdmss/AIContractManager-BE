# 대시보드 및 챗봇 API

모든 API는 로그인 후 발급받은 Bearer 토큰이 필요합니다.

## 대시보드

`GET /api/aicontract/dashboard?month=2026-11`

`month`는 `yyyy-MM` 형식이며 생략하면 이번 달을 사용합니다. 응답에 캘린더 일정, 오늘부터 3일 이내에 만료되는 활성 계약, 현재 활성 계약의 월 지출 합계와 카테고리별 지출 비율이 포함됩니다.

```json
{
  "month": "2026-11",
  "monthlyExpense": 85000,
  "categoryExpenses": [
    { "category": "FITNESS", "amount": 50000, "percentage": 58.82 },
    { "category": "TELECOM", "amount": 35000, "percentage": 41.18 }
  ],
  "expiringContracts": [],
  "calendarEvents": []
}
```

월 지출은 `ACTIVE` 상태이며 삭제되지 않은 계약 중 `monthly_amount`가 있는 항목의 합계입니다. 계약 만료일 외에 계약 일정, 자동 갱신일, 해지 마감일도 캘린더 이벤트로 반환합니다.

## 챗봇

### 대화 세션 생성

`POST /api/aicontract/chat/sessions`

일반 이용 문의는 본문을 생략하거나 `{}`를 전달합니다. 계약 관련 질문을 특정 계약으로 제한할 때는 계약 ID를 전달합니다.

```json
{ "contractId": "계약 UUID" }
```

### 대화 세션 목록과 메시지

- `GET /api/aicontract/chat/sessions`
- `GET /api/aicontract/chat/sessions/{sessionId}/messages`

### 질문 전송

`POST /api/aicontract/chat/sessions/{sessionId}/messages`

```json
{ "message": "이 계약 해지 신청 마감일이 언제야?" }
```

백엔드는 사용자 메시지와 답변을 DB에 저장하고, 소유 계약의 요약 정보 및 최근 대화 이력을 AI 서버 `/chat`에 전달합니다. AI 서버는 Gemini로 답변을 생성합니다. 계약서 원문 파일이나 문서 전체는 챗봇 요청에 포함하지 않습니다.

현재 응답은 보유 계약 데이터 기반입니다. 외부 약관·법률 자료 수집, 벡터 검색, RAG 근거 인용은 별도 데이터셋과 검색 파이프라인이 준비된 뒤 연결해야 합니다.
