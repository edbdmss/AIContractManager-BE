# Backend completion

## Android payment detection
POST /api/aicontract/payment-detections (Bearer token required).
Body: eventId (stable notification identifier, max 100), merchantName, amount, paidDate (`YYYY-MM-DD`), sourcePackage, parserVersion (optional).
Only confirmed purchases should be sent by the Android parser; raw notifications are not stored. Cancellation/refund notifications must be filtered by the client.
Returns detectionId, suggestionId (nullable), duplicate. Reusing eventId for the same user does not create another transaction. The first persisted payload wins.
Two equal-amount payments at the same merchant 25–35 days apart create a MONTHLY suggestion and notification. This is a heuristic requiring user confirmation. Other cycles are not inferred yet.
Previously accepted/rejected/ignored suggestions are not automatically recreated. The existing suggestion acceptance flow leads to subscription registration.

## Contracts
GET /api/aicontract/contracts?sort=created|amount|deadline. Default created. Amount descending, earliest deadline ascending, nulls last.
POST /api/aicontract/contracts/{id}/cancel marks a contract CANCELLED without deleting its history. This only records cancellation in this service and does not cancel the vendor subscription.
Cancellation and deletion dismiss pending schedules and cancel queued notifications. Already delivered pushes cannot be recalled.
Creating/updating contract dates synchronizes automatically managed EXPIRY, RENEWAL and CANCELLATION reminders 3 days before at 09:00 (server local timezone, configure Asia/Seoul). Manual schedules are preserved. Unchanged dates do not recreate completed reminders.

## Migration and remaining integration
Apply V4 with Flyway before running these APIs. Existing rows are preserved; existing schedules remain manual. Previously saved contracts receive managed reminders on the next update.
Device NotificationListenerService, refund filtering, FCM setup/device testing, production database migration and AI legal RAG corpus remain integration tasks. These changes do not establish those external integrations.
