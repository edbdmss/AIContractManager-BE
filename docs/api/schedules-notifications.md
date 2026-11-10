# 일정 및 알림 API

모든 API는 로그인 후 발급받은 Bearer 토큰이 필요합니다.

## 일정

### 계약 일정 목록

`GET /api/v1/contracts/{contractId}/schedules`

로그인 사용자가 소유한 계약의 일정을 dueDate 오름차순으로 반환합니다.

### 일정 등록

`POST /api/v1/contracts/{contractId}/schedules`

```json
{
  "scheduleType": "CANCELLATION",
  "dueDate": "2026-12-01",
  "actionTitle": "해지 신청 마감",
  "actionDescription": "서비스 고객센터에서 해지 신청을 완료하세요.",
  "reminderDaysBefore": 14
}
```

지원 일정 유형은 `RENEWAL`, `CANCELLATION`, `PAYMENT`, `EXPIRY`, `CUSTOM`입니다. 일정이 생성되면 `dueDate - reminderDaysBefore` 날짜에 대응하는 알림 레코드도 생성됩니다.

### 전체 예정 일정

`GET /api/v1/schedules/upcoming`

로그인 사용자가 소유한 계약 중 아직 처리되지 않았고 오늘 이후인 일정을 반환합니다.

### 일정 완료 또는 숨김

- `POST /api/v1/schedules/{scheduleId}/complete`
- `POST /api/v1/schedules/{scheduleId}/dismiss`

완료는 `COMPLETED`, 숨김은 `DISMISSED` 상태로 변경합니다. 이미 처리한 일정은 `409 Conflict`를 반환합니다.

## 알림

### 알림 목록

`GET /api/v1/notifications?unreadOnly=true`

`unreadOnly`는 생략하거나 `false`로 설정하면 현재 시각까지 도달한 전체 알림, `true`이면 읽지 않은 알림만 반환합니다.

### 알림 읽음 처리

- `PATCH /api/v1/notifications/{notificationId}/read`
- `POST /api/v1/notifications/read-all`

일정 알림의 생성·조회·읽음 처리와 FCM 푸시 발송을 지원합니다.

### 기기 푸시 토큰 등록 및 해제

앱 로그인 후 FCM 토큰을 등록합니다. 사용자가 로그아웃하거나 알림 수신을 끄면 해당 토큰을 해제합니다.

`POST /api/v1/notifications/devices`

```json
{
  "token": "FCM_DEVICE_TOKEN",
  "platform": "ANDROID",
  "deviceName": "Pixel 8"
}
```

`platform`은 `ANDROID` 또는 `IOS`입니다. 해제는 `DELETE /api/v1/notifications/devices?token=FCM_DEVICE_TOKEN`을 호출합니다.

### 예약 발송 동작

`app.push.fcm.enabled=true`일 때 Spring 스케줄러가 30초 간격으로 발송 시각이 지난 알림을 조회해 Firebase Cloud Messaging으로 전송합니다. 실패는 최대 5회까지 지수 간격으로 재시도하고, 등록되지 않은 기기 토큰은 비활성화합니다. 활성 토큰이 없는 사용자의 알림은 예약 상태로 남겨두고 다음 주기에 다시 확인합니다.

로컬 실행 시 Firebase 서비스 계정 JSON 파일 경로를 `FCM_CREDENTIALS_FILE`로 지정하거나 Google Application Default Credentials를 설정합니다. `FCM_PROJECT_ID`는 필요한 경우 지정합니다. 서비스 계정 파일은 저장소에 추가하지 않습니다. 설정 기본값은 FCM 비활성화이므로 Firebase 프로젝트와 안드로이드 앱 설정 전에는 실제 푸시가 전송되지 않습니다.
