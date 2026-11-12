# Android payment detection API

`POST /api/aicontract/payment-detections` requires a Bearer access token.

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "merchantName": "넷플릭스",
  "amount": 17000,
  "paidDate": "2026-11-12",
  "sourcePackage": "com.example.bank",
  "parserVersion": "1.0.0"
}
```

`paidDate` is the payment calendar date only (`YYYY-MM-DD`). The API does not accept a payment time. `amount` is required and must be greater than zero. `eventId` identifies a single parsed notification and makes retries idempotent for the same user.

Response:

```json
{
  "detectionId": "uuid",
  "suggestionId": "uuid-or-null",
  "duplicate": false
}
```

Two same-merchant, same-amount payments detected 25–35 days apart may create a subscription suggestion. This is a heuristic; the user confirms the suggestion before registration.

`eventId` must be a canonical UUID string. Reuse it for retries. `amount` is positive integer KRW. `sourcePackage` and `parserVersion` are optional. The acceptance response includes `nextPaymentDate`, calculated from the latest detected payment plus one calendar month (month-end clamps to the last day).
