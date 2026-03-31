# 실행 계획

## 프로젝트 목표

`agent-monitor`는 여러 봇 서비스를 중앙에서 감시하는 운영형 백엔드 서비스다.
초기 버전은 DMIB를 첫 monitored service로 등록하고, 상태 조회와 장애 알림 흐름을 완성하는 것을 목표로 한다.

## 1차 범위

- 모니터링 대상 서비스 등록 구조 정의
- `GET /actuator/health` 조회
- `GET /internal/monitoring/last-run` 조회
- 상태 저장
- incident open/close 기본 구조
- Slack 알림 기본 구조
- 운영 문서와 handoff 문서 준비

## 현재 상태

- 프로젝트 골격 생성 완료
- Gradle / Spring Boot / JDBC / Actuator / WebClient 기반 구성 완료
- 기본 DB 스키마 초안 작성 완료
- summary endpoint 초안 작성 완료

## 다음 구현 우선순위

1. `monitored_service` 등록/조회 기능 구현
2. polling scheduler에서 실제 HTTP 호출 연결
3. `service_check_history`, `service_current_status` 저장 구현
4. incident open/close 정책 구현
5. Slack 알림 연동
6. DMIB를 첫 서비스로 등록하고 end-to-end 확인

## 보류 중인 항목

- Redis 도입
- Kafka 기반 이벤트 수집
- 대시보드 UI
- image pull 기반 배포 자동화
