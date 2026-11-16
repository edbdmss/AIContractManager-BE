# DB 값 규칙 (V11)

- API `paidDate`에 대응하는 두 결제 테이블의 DB 컬럼을 `paid_date DATE NOT NULL`로 변경한다.
- 계약 월 금액·위약금, 감지 금액, 결제 기록 금액, 제안 예상 금액에 원 단위 정수 CHECK를 추가한다.
- DECIMAL(15,2)는 유지한다. BIGINT나 scale=0으로 변환하면서 기존 소수 금액을 반올림하는 것을 피하기 위해서다.
- CHECK는 저장되는 값의 소수 부분을 금지한다. MySQL 컬럼 변환 단계에서 소수 셋째 자리 이하가 반올림될 수 있으므로 원본 입력의 모든 소수 자릿수는 API Digits(fraction=0) 검증으로 차단한다.
- 기존 소수 금액을 자동 수정하지 않는다. 아래 쿼리로 잘못된 값을 먼저 확인한다. MySQL의 여러 ALTER는 전체가 하나의 트랜잭션으로 롤백되지 않으므로 사전 확인 후 적용한다.
- 일정 DB 기본값과 API 생략/null 기본값은 3일. 명시적 0과 사용자 지정값은 유지한다. 기존 14일 설정은 덮어쓰지 않는다.
- 시각은 이번 변경 대상이 아니다. 자동 일정 09:00, 수동 일정 00:00 정책은 별도로 통일할 필요가 있다.
- 문서 분석 전 임시 계약의 날짜는 null을 허용해야 하므로 V10 조건부 CHECK를 유지한다.

```sql
SELECT id, monthly_amount, penalty_amount FROM contracts
WHERE monthly_amount <> TRUNCATE(monthly_amount, 0)
   OR penalty_amount <> TRUNCATE(penalty_amount, 0) OR penalty_amount < 0;
SELECT id, amount FROM payment_detection_events WHERE amount <> TRUNCATE(amount, 0);
SELECT id, amount FROM payment_transactions WHERE amount <> TRUNCATE(amount, 0);
SELECT id, estimated_amount FROM contract_suggestions WHERE estimated_amount <> TRUNCATE(estimated_amount, 0);
```

기존 V1~V10은 유지하고 V11을 적용한다. 실제 DB 적용 여부는 flyway_schema_history로 확인한다.
RAG 응답 원본은 JSON에 보존하고, 조항별 사용자 확인 상태나 근거별 검색·통계가 필요해지면 관계형 테이블을 추가한다.
