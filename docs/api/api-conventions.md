# API 전달 규칙

POST /api/aicontract/payment-detections: eventId(UUID), merchantName, amount(양의 정수 원), paidDate(YYYY-MM-DD, 미래 불가). sourcePackage와 parserVersion은 선택.

POST /api/aicontract/subscription-suggestions/{id}/accept: 본문 없음. 응답은 suggestionId, subscriptionName, estimatedAmount, estimatedCycle, nextPaymentDate. 다음 결제일은 감지된 마지막 결제일 + 1개월이며 월말은 다음 달 말일로 보정. 과거 데이터 중 결제 근거가 없는 제안의 nextPaymentDate는 null일 수 있음.

POST /api/aicontract/contracts: title, category, providerName, monthlyAmount, contractStartDate, contractEndDate, nextPaymentDate, cancellationDeadline, penaltyAmount. monthlyAmount는 필수 정수 원. 구독은 category=SUBSCRIPTION, nextPaymentDate 필수. 저장 후 POST /api/aicontract/subscription-suggestions/{id}/complete에 {"contractId":"계약 UUID"} 전송.

오류 응답은 code, message, fieldErrors 배열. 필드 검증 오류는 VALIDATION_ERROR/400. 인증 없음은 UNAUTHORIZED/401, 권한 부족은 FORBIDDEN/403. 일반 HTTP 오류는 HTTP_상태코드, 예상치 못한 오류는 INTERNAL_ERROR/500.

날짜는 YYYY-MM-DD, 시각은 오프셋 포함 ISO 8601. 목록은 배열, 단건/대시보드는 객체, 204는 본문 없음.
