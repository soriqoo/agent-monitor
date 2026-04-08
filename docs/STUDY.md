# 학습 노트

## 이 프로젝트로 익히는 것

- 여러 서비스를 중앙에서 보는 운영 감각
- polling 기반 상태 수집 설계
- incident lifecycle 모델링
- 공통 monitoring contract 설계
- 서비스 간 역할 분리

## 이번 프로젝트에서 특히 중요한 개념

### Liveness와 Correctness 분리
- Liveness: 프로세스가 살아 있는가
- Correctness: 오늘 서비스가 제 역할을 했는가

### 상태와 이벤트 분리
- 현재 상태는 `service_current_status`
- 과거 이력은 `service_check_history`
- 장애 lifecycle은 `incident`

### 중앙 서비스는 최소한으로 시작
- 너무 많은 기술을 넣지 않는다
- 먼저 운영 가능한 구조를 만든다
- 필요가 생길 때 Redis, Kafka를 도입한다

### 관측 실패와 실제 실패 분리
- health endpoint가 안 보이는 것과
- 실제 비즈니스 실행이 실패한 것은 다를 수 있다
- 모니터링 시스템은 "대상 서비스 장애"와 "관측 시스템 한계"를 구분해야 한다

### scheduler와 worker 책임 분리
- scheduler는 타이밍과 orchestration 담당
- 실제 HTTP 호출과 상태 판단은 별도 서비스 담당
- 이렇게 해야 테스트가 쉬워지고 코드가 덜 비대해진다

### 즉시 incident와 누적 판단 incident 분리
- 어떤 문제는 한 번만 발생해도 바로 incident를 열어야 한다
- 어떤 문제는 연속 실패를 보고 판단해야 노이즈가 줄어든다
- 운영 정책은 "민감도"와 "노이즈" 사이의 균형 문제다

### 시간값은 문자열보다 시점으로 이해하기
- `2026-04-01T08:00:03+09:00`와 `2026-03-31T23:00:03Z`는 같은 시점일 수 있다
- time zone / offset / instant 차이를 이해해야 운영 데이터 해석이 안정적이다

### Spring 프록시와 Kotlin final class
- Kotlin 클래스는 기본이 final이라 Spring AOP/트랜잭션 프록시와 충돌할 수 있다
- stereotype annotation(`@Service`, `@Repository`)과 프록시 방식이 실제로 어떻게 붙는지 이해하면 디버깅이 빨라진다
- 실무에서는 "왜 빈 생성은 되는데 트랜잭션 시점에 깨지는가"를 로그로 좁히는 감각이 중요하다

### Docker Compose project name 충돌
- 여러 서비스를 `/apps/<service>/repo` 구조로 두는 것은 흔하다
- 이때 compose 기본 project name이 모두 `repo`가 되면 서비스명, 네트워크, DB 이름이 섞일 수 있다
- `name:`을 명시해 충돌을 막는 것은 작은 설정이지만 운영 안정성에는 큰 영향을 준다

## 추천 학습 순서

1. JDBC 기반 상태 저장
2. polling scheduler 구현
3. incident open/close 조건 설계
4. Slack alert 정책 설계
5. Testcontainers 도입 검토

## 이번 단계에서 특히 복습하면 좋은 질문

- 왜 health와 last-run을 같은 장애로 취급하면 안 되는가
- 왜 scheduler에 모든 로직을 몰아넣지 않는가
- 왜 `DEGRADED`를 항상 즉시 incident로 승격하지 않는가
- `current status`, `history`, `incident`는 각각 어떤 질문에 답하는가
- 같은 시간값을 서로 다른 offset으로 표현할 수 있다는 것이 테스트와 DB에 어떤 영향을 주는가
