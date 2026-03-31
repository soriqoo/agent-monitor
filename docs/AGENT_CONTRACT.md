# 모니터링 대상 서비스 계약

## 목적

이 문서는 `agent-monitor`가 읽어야 하는 공통 서비스 계약을 정의한다.

## 필수 endpoint

### 1. Health
- `GET /actuator/health`

### 2. Last Run
- `GET /internal/monitoring/last-run`

예시 응답:

```json
{
  "service": "dmib",
  "environment": "prod",
  "timezone": "Asia/Seoul",
  "lastRunDate": "2026-03-31",
  "status": "SENT",
  "sentAt": "2026-03-31T08:00:03+09:00",
  "error": null
}
```

## 필드 의미

- `service`: 서비스 식별자
- `environment`: 실행 환경
- `timezone`: 기준 시간대
- `lastRunDate`: 마지막 실행 대상 날짜
- `status`: 마지막 실행 상태
- `sentAt`: 마지막 성공 시각
- `error`: 마지막 실패 원인
