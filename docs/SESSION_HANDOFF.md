# 다음 세션용 handoff

## 프로젝트 정체성

`agent-monitor`는 DMIB와 이후 여러 Slack 봇 프로젝트를 중앙에서 감시하는 통합 모니터링 서비스다.

## 현재 설계 기준

- 별도 repo로 운영
- Kotlin + Spring Boot + JDBC + PostgreSQL 기반
- polling 방식으로 각 서비스의 health / last-run 조회
- 중앙 서비스는 상태 집계와 incident 관리 담당
- 첫 monitored service는 DMIB

## 지금까지 준비된 것

- 프로젝트 골격 생성
- Gradle / Spring Boot 기본 설정
- DB 스키마 초안
- summary endpoint 초안
- polling scheduler 뼈대
- 운영/설계/학습 문서 세트

## 다음 세션에서 바로 할 일

1. `monitored_service` CRUD 또는 seed 구조 추가
2. polling scheduler에서 실제 WebClient 호출 구현
3. `service_check_history`, `service_current_status` 저장 구현
4. incident open/close 로직 구현
5. DMIB를 첫 서비스로 등록해 end-to-end 확인

## 중요한 연계 맥락

- DMIB는 이미 `/actuator/health`, `/internal/monitoring/last-run`를 제공한다.
- DMIB는 첫 monitored service 역할을 한다.
- 장기적으로는 여러 Slack 봇을 같은 계약으로 통합 모니터링할 계획이다.
- 목표는 단순 대시보드보다 운영 가능한 중앙 감시 서비스다.
