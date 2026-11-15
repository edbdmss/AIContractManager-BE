# 프로필 및 알림 설정 API

모든 API는 `Authorization: Bearer <access-token>`이 필요합니다.

## 내 프로필

`GET /api/aicontract/auth/me`

응답에는 `userId`, `email`, `name`, `provider`가 포함됩니다. 탈퇴 또는 비활성 계정은 반환하지 않습니다.

## 알림 목록 조작

- `DELETE /api/aicontract/notifications/{notificationId}`: 내 알림을 숨깁니다. 이후 목록 응답에서 제외됩니다.
- `POST /api/aicontract/notifications/dismiss-all`: 현재까지 도달한 내 알림을 모두 숨깁니다. 응답의 `dismissedCount`로 처리 건수를 반환합니다.

기존 읽음 처리 API는 유지되며, 읽음과 숨김은 서로 다른 상태입니다.

## 푸시 수신 설정

`GET /api/aicontract/notifications/preferences`

`PUT /api/aicontract/notifications/preferences`

```json
{ "enabled": false }
```

최초 설정은 `enabled: true`입니다. `false`이면 예약 푸시 발송 대상에서 제외되지만, 앱 내 알림함 데이터는 유지됩니다. 푸시 발송을 재개하면 미발송 예약분이 다시 대상이 될 수 있으므로 앱은 수신 설정과 기기 권한을 함께 안내해야 합니다.
