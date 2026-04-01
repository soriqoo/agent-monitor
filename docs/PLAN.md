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
- GitHub Actions CI 추가
- DMIB seed 기반 monitored service 자동 등록 구현 완료
- 실제 polling 구현 완료

## 최근 변경 이력

### 1. 저장소 운영 기준선 정리
- `.gitignore` 보강
- `README` 확장
- GitHub Actions CI 추가
- PR-only 운영을 위한 체크 이름 정리

### 2. monitored service seed 등록 도입
- 앱 시작 시 DMIB를 자동 등록하는 seed 구조 추가
- `service_name + environment` 기준 upsert 도입
- 테스트 프로필에서 seed 비활성화, 전용 테스트로 검증

### 3. 실제 polling 연결
- scheduler에서 실제 polling 서비스 호출
- `/actuator/health`, `/internal/monitoring/last-run` 조회 구현
- health와 last-run 결과를 하나의 polling 결과 모델로 정규화
- 성공 / 부분 실패 / health down 시나리오 테스트 추가

## 현재 설계 결정

### Seed를 먼저 도입한 이유
- 첫 monitored service가 DMIB로 이미 확정되어 있다
- CRUD보다 빠르게 end-to-end 흐름을 검증할 수 있다
- 나중에 CRUD를 붙여도 seed 구조를 그대로 초기 기본값으로 재사용할 수 있다

### polling 결과를 먼저 정규화한 이유
- scheduler에 외부 호출과 상태 판단이 같이 들어가면 이후 저장/incident 로직이 금방 복잡해진다
- `ServicePollResult`를 먼저 만들면 다음 단계인 DB 저장이 단순해진다
- health 실패와 last-run 실패를 다른 종류의 문제로 구분할 수 있다

### 1차 incident 정책
- `health` 요청 실패 또는 health status 비정상은 즉시 incident 후보로 본다
- `last-run.status`가 명백한 실패 상태면 즉시 incident 후보로 본다
- `last-run endpoint` 자체의 일시 실패는 바로 incident로 열지 않고 우선 `DEGRADED` 상태로 저장한다
- 같은 `last-run endpoint` 실패가 연속되면 그때 incident로 승격한다

## 다음 구현 우선순위

1. `service_check_history`, `service_current_status` 저장 구현
2. incident open/close 정책 구현
3. `last-run endpoint` 연속 실패 판단 기준 추가
4. Slack 알림 연동
5. DMIB를 첫 서비스로 등록하고 end-to-end 확인
6. 이후 `monitored_service` CRUD 또는 관리 API 확장 검토

## 다음 단계 구현 준비

### 상태 저장 단계에서 할 일
- polling 결과를 이력 테이블과 현재 상태 테이블에 함께 저장
- `service_check_history`에는 매 poll 결과를 append
- `service_current_status`에는 최신 상태를 upsert
- 이후 incident 판단은 현재 상태와 최근 이력을 같이 참고할 수 있게 만든다

### incident 단계에서 할 일
- incident 유형을 최소 2가지로 구분하는지 검토
  - availability 관점
  - execution 관점
- 즉시 open 대상과 연속 실패 후 open 대상을 분리
- recovery 시 close 조건을 명확히 정의
- Slack 알림 dedupe 기준도 incident lifecycle과 맞춘다

## 보류 중인 항목

- Redis 도입
- Kafka 기반 이벤트 수집
- 대시보드 UI
- image pull 기반 배포 자동화
