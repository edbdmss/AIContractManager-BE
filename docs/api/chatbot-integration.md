# 챗봇 AI 연동

BE → AI 요청은 `question`, `history`, `contractContext`를 유지한다.
계약 컨텍스트에는 기존 필드 외에 `contractStartDate`, `penaltyAmount`,
`amountVerified`, `currency`, `riskReports`를 전달한다.
`monthlyAmount`와 `penaltyAmount`는 JSON 숫자이며, 없는 단일 값은 null이다.
날짜는 YYYY-MM-DD이고 `nextPaymentDate`를 사용한다. `autoRenewalDate`는 추가하지 않는다.
위험 조항은 삭제되지 않은 문서의 가장 최근 완료 분석에서 가져오며, 없으면 빈 배열이다.

AI 응답의 `answer`는 필수다. 다음 메타데이터는 chat_messages.response_metadata에 저장하고
질문 전송 응답의 message 및 대화 이력의 각 메시지에 같은 필드로 반환한다.

| 필드 | 타입 |
| --- | --- |
| answerType | CONTRACT / LEGAL / HYBRID 또는 null |
| referencedContractIds | UUID 문자열 배열 |
| legalSources | ruleId, lawName, article, title, sourceUrl을 가진 객체 배열 |
| needsMoreInfo | boolean 또는 null |

참조 계약 ID는 해당 AI 요청에 포함한 계약 범위에 속해야 한다. 잘못된 메타데이터는 502로 처리한다.
기존 메시지와 사용자 메시지는 answerType·needsMoreInfo가 null이고 목록은 빈 배열이다.
`timings`는 숫자인 단계별 처리 시간(초)만 BE 로그에 기록하고 DB 및 FE에는 전달하지 않는다.

DB 적용은 Flyway V14__chat_response_metadata.sql로 수행한다.
