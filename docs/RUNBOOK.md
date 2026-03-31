# 운영 Runbook

## 문서 목적

이 문서는 `agent-monitor` 운영자가 상태 확인과 장애 대응을 할 때 참고하는 기준 문서다.

## 초기 확인 항목

- 컨테이너가 정상 기동했는가
- `/actuator/health`가 응답하는가
- `/internal/monitoring/summary`가 응답하는가
- 모니터링 대상 서비스 목록이 정상 로드되는가

## 향후 운영 명령 예시

- `agent-monitor deploy`
- `agent-monitor restart`
- `agent-monitor ps`
- `agent-monitor logs`
- `agent-monitor health`

## 장애 시 1차 대응 순서

1. health 확인
2. DB 연결 상태 확인
3. 대상 서비스 응답 실패인지, 중앙 서비스 문제인지 분리
4. 최근 incident와 alert event 확인
5. Slack 알림 중복 여부 확인
