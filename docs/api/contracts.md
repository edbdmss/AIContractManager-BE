# Contracts API

All endpoints require `Authorization: Bearer <access-token>`. Contract ownership is determined from the token, not the request body.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/contracts` | List my non-deleted contracts |
| GET | `/api/v1/contracts/{id}` | Read one of my contracts |
| POST | `/api/v1/contracts` | Create a manual contract (201) |
| PUT | `/api/v1/contracts/{id}` | Replace editable fields |
| POST | `/api/v1/contracts/{id}/verify-amount` | Confirm the saved monthly amount |
| DELETE | `/api/v1/contracts/{id}` | Soft-delete a contract (204) |

Create and PUT use the same body. `title` and `category` are required. Optional fields are `providerName`, `contractStartDate`, `contractEndDate`, `autoRenewalDate`, `cancellationDeadline`, `monthlyAmount`, `penaltyAmount`, `cancellationTerms`, and `plainSummary`. Dates use `YYYY-MM-DD`; amounts are nonnegative decimal values. PUT replaces all editable fields, so omitted optional fields become `null`.

Example create body:

```json
{
  "title": "헬스장 12개월 이용권",
  "category": "FITNESS",
  "providerName": "동네 헬스장",
  "contractStartDate": "2026-11-01",
  "contractEndDate": "2027-10-31",
  "monthlyAmount": 50000
}
```

New contracts have `PENDING_VERIFICATION` status and `amountVerified=false`. To confirm the saved amount, send `{"monthlyAmount":50000}` to the verification endpoint. A changed monthly amount resets verification. Missing or non-owned contracts return 404.
