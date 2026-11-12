# Android payment detection API

`POST /api/aicontract/payment-detections` requires a Bearer access token.

```json
{
  "eventId": "notification-key-or-generated-uuid",
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
