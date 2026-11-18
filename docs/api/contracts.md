# Contracts API

All endpoints require `Authorization: Bearer <access-token>`. Contract ownership is determined from the token, not the request body.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/aicontract/contracts` | List my non-deleted contracts |
| GET | `/api/aicontract/contracts/attention` | Home: contracts ending today through 7 days from today (Korea time) |
| GET | `/api/aicontract/contracts/{id}` | Read one of my contracts |
| POST | `/api/aicontract/contracts` | Create a manual contract (201) |
| PUT | `/api/aicontract/contracts/{id}` | Replace editable fields |
| POST | `/api/aicontract/contracts/{id}/verify-amount` | Confirm the saved monthly amount |
| DELETE | `/api/aicontract/contracts/{id}` | Soft-delete a contract (204) |

Create and PUT use the same body. `title` and `category` are required. For manually registered non-subscription contracts, `contractStartDate` and `nextPaymentDate` are required. Subscriptions require `nextPaymentDate` but do not require `contractStartDate`. `contractEndDate` and `cancellationDeadline` are optional. Other optional fields are `providerName`, `monthlyAmount`, `penaltyAmount`, `cancellationTerms`, and `plainSummary`. Date-only values use `YYYY-MM-DD`; amounts are nonnegative decimal values. PUT replaces all editable fields, so omitted optional fields become `null`.

For category `SUBSCRIPTION`, `monthlyAmount` and `nextPaymentDate` are required. New contracts, including subscriptions, become ACTIVE only when amountVerified=true is submitted or the existing verification endpoint is called. The backend schedules a reminder for 09:00 three days before `nextPaymentDate`. After the payment date passes, a daily Korea-time job advances it by renewalCycleMonths months and creates the next reminder while the contract remains active. Cancelling or deleting the subscription stops future reminders. The original day of month is retained when a month is shorter (for example, a day-31 subscription moves to the last day of February and returns to day 31 in March). Other manually registered categories require both `contractStartDate` and `nextPaymentDate` and use the amount confirmation flow below. Incomplete document-import records are exempt while they await AI analysis.

Example create body:

```json
{
  "title": "헬스장 12개월 이용권",
  "category": "FITNESS",
  "providerName": "동네 헬스장",
  "contractStartDate": "2026-11-01",
  "contractEndDate": "2027-10-31",
  "nextPaymentDate": "2026-12-01",
  "monthlyAmount": 50000
}
```

All API date-time values (for example `createdAt`, `scheduledAt`, and `sentAt`) are returned as ISO 8601 timestamps with the Asia/Seoul offset, such as `2026-11-12T10:30:00+09:00`.

New contracts without explicit amount confirmation have `PENDING_VERIFICATION` status and `amountVerified=false`. To confirm the saved amount, send `{"monthlyAmount":50000}` to the verification endpoint. A changed monthly amount resets verification. Missing or non-owned contracts return 404.

`monthlyAmount` is required for creation/update and must be a nonnegative integer KRW value. DB columns: `contracts.next_payment_date` (V8), `contracts.payment_day_of_month` (V9 recurrence anchor), and `contracts.registration_mode` (V10 manual/document date constraint).

Request/response extension: `renewalCycleMonths` (1–60 months (up to 5 years)), request `amountVerified` (optional boolean). See [home and renewal integration](home-renewal-integration.md) for defaults and update semantics. V12 stores the user-selected cycle with a 1–60 month CHECK constraint. Existing contracts remain unset; subscriptions have no automatic one-month default. Unset cycles do not repeat.
