# Contracts API

All endpoints require `Authorization: Bearer <access-token>`. Contract ownership is determined from the token, not the request body.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/aicontract/contracts` | List my non-deleted contracts |
| GET | `/api/aicontract/contracts/{id}` | Read one of my contracts |
| POST | `/api/aicontract/contracts` | Create a manual contract (201) |
| PUT | `/api/aicontract/contracts/{id}` | Replace editable fields |
| POST | `/api/aicontract/contracts/{id}/verify-amount` | Confirm the saved monthly amount |
| DELETE | `/api/aicontract/contracts/{id}` | Soft-delete a contract (204) |

Create and PUT use the same body. `title` and `category` are required. Optional fields are `providerName`, `contractStartDate`, `contractEndDate`, `nextPaymentDate`, `cancellationDeadline`, `monthlyAmount`, `penaltyAmount`, `cancellationTerms`, and `plainSummary`. Date-only values use `YYYY-MM-DD`; amounts are nonnegative decimal values. PUT replaces all editable fields, so omitted optional fields become `null`.

For category `SUBSCRIPTION`, `monthlyAmount` and `nextPaymentDate` are required. User-entered subscriptions are immediately active and included in dashboard expenses; the date creates a `PAYMENT` calendar event and reminder. Other categories use the amount confirmation flow below.

Example create body:

```json
{
  "title": "헬스장 12개월 이용권",
  "category": "FITNESS",
  "providerName": "동네 헬스장",
  "contractStartDate": "2026-11-01",
  "contractEndDate": "2027-10-31",
  "nextPaymentDate": null,
  "monthlyAmount": 50000
}
```

All API date-time values (for example `createdAt`, `scheduledAt`, and `sentAt`) are returned as ISO 8601 timestamps with the Asia/Seoul offset, such as `2026-11-12T10:30:00+09:00`.

New non-subscription contracts have `PENDING_VERIFICATION` status and `amountVerified=false`. To confirm the saved amount, send `{"monthlyAmount":50000}` to the verification endpoint. A changed monthly amount resets verification. Missing or non-owned contracts return 404.
