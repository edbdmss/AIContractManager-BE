# 홈·갱신 주기·금액 확인 연동

## 홈 확인 필요 계약

`GET /api/aicontract/contracts/attention` (인증 필수). 계약 객체 배열을 반환한다.
한국 날짜 기준 오늘부터 오늘+7일까지(양끝 포함) contractEndDate, nextPaymentDate, cancellationDeadline 중 하나라도 해당하는 내 계약을 반환한다.
등록 완료된 ACTIVE/PENDING_VERIFICATION 계약만 포함한다. NULL이나 지난 날짜는 해당 일정의 후보에서 제외하며 CANCELLED/EXPIRED·소프트 삭제·임시 계약은 제외한다.
범위 안의 가장 가까운 일정 오름차순이며 계약당 한 번만 반환한다. 일반 계약 목록 GET /contracts는 전체 목록으로 유지한다.
대시보드의 기존 3일 만료 기준은 별도 기능으로 유지한다. 홈은 새 API에 연결해야 화면이 바뀐다.

## 갱신 주기

계약 POST/PUT 요청 및 계약 응답에 `renewalCycleMonths` 추가. 1~60개월(최대 5년)의 정수, 예: 1개월=1, 3개월=3, 1년=12.
사용자가 1, 2, 3, …, 24, …, 60개월 중 선택한 값을 전송한다.
생성 시 생략/null이면 모든 카테고리에서 주기 미설정(null)이다. 구독도 자동으로 1개월을 넣지 않는다.
주기 미설정 계약은 입력된 날짜의 일회성 일정만 생성하며 반복 갱신하지 않는다. 기존 계약도 사용자가 주기를 선택해야 반복 관리가 시작된다.
수정 시 생략/null이면 기존 주기를 유지한다. 자동 관리를 중단하려면 계약 해지 API를 사용한다.
`nextPaymentDate`는 사용자가 선택한 첫 결제/갱신 예정일이며 주기를 선택했다고 즉시 다시 계산하지 않는다.
ACTIVE이고 주기가 설정된 계약은 매일 한국 시간 00:05에 지난 예정일을 주기만큼 이동하고 새 일정을 만든다.
월말 기준일 보존, 서버 중단으로 여러 주기가 지났다면 오늘 또는 미래의 첫 회차까지 이동.
계약 종료일을 넘는 새 예정일은 생성하지 않는다. 법적 계약 종료일 자체를 자동 연장하지 않는다.
알림은 각 새 예정일 3일 전 09:00으로 예약한다. 해지/삭제/비활성 계약은 자동 갱신 대상에서 제외된다.
주기만 수정하고 다음 예정일이 같으면 기존 알림을 중복 생성하지 않는다. 변경된 주기는 다음 갱신부터 사용한다.
`monthlyAmount`는 기존과 동일하게 월 납부액이며 선택한 주기의 총 청구 금액으로 의미를 바꾸지 않는다.

## 금액 옆 확인 체크

POST/PUT 요청에 `amountVerified` boolean 추가.
- 생성 시 true: 금액 확인 완료, ACTIVE. 생략/null/false: PENDING_VERIFICATION.
- 수정 시 true: 현재 요청 금액 확인. false: 확인 해제.
- 수정 시 생략/null: 같은 금액이면 기존 확인 유지, 금액 변경 시 확인 해제.
- CANCELLED/EXPIRED 상태는 금액 체크로 재활성화되지 않는다.
- 기존 POST /contracts/{id}/verify-amount API도 유지한다.
- AI 분석으로 금액이 바뀌면 확인이 해제되며 AI가 사용자 확인을 대신하지 않는다.

```json
{
  "title": "헬스장 이용 계약",
  "category": "FITNESS",
  "providerName": "예시 헬스장",
  "contractStartDate": "2026-11-18",
  "contractEndDate": null,
  "nextPaymentDate": "2027-02-18",
  "cancellationDeadline": null,
  "monthlyAmount": 50000,
  "penaltyAmount": null,
  "cancellationTerms": null,
  "plainSummary": null,
  "renewalCycleMonths": 3,
  "amountVerified": true
}
```

## FE에서 처리할 항목

금액 확인 버튼 대신 체크 표시, 갱신 주기 선택 UI, 계약 정보 확인 버튼 문구, 저장 성공 후 홈/계약 목록 이동 선택 모달은 FE 작업이다.
저장 성공 응답을 받은 후에만 이동 선택을 표시한다. 추가 푸시 알림이나 서버 리다이렉트 API는 필요하지 않다.
프론트 코드는 이번 변경에 포함하지 않는다.

## DB

Flyway V12: contracts.renewal_cycle_months 추가, 1~60개월 CHECK 제약, 기존 계약은 주기 미설정으로 유지, 홈 만료 조회 인덱스 추가.
