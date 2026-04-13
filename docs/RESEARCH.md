# 설계 및 조사 노트

## 문서 목적

이 문서는 `agent-monitor`를 어떤 방향으로 설계할지, 그리고 왜 그 방향이 실무적으로 적절한지 정리한다.

## 핵심 설계 판단

### 1. 중앙 모니터링은 Slack 메시지를 파싱하지 않는다
각 봇의 Slack 전송 결과를 보고 상태를 추론하면 구조가 불안정해진다.
중앙 서비스는 각 봇이 제공하는 공통 API를 기준으로 상태를 판단해야 한다.

### 2. Polling으로 먼저 시작한다
초기에는 polling이 가장 단순하고 운영 가능성이 높다.
Kafka나 이벤트 기반 구조는 서비스 수가 늘고 비동기 수집이 실제로 필요해질 때 도입한다.

### 3. 상태 저장과 incident lifecycle을 먼저 만든다
대시보드보다 중요한 것은 아래다.
- 현재 상태를 설명할 수 있는가
- 장애가 언제 시작됐는가
- 복구됐는가
- 같은 장애를 너무 자주 알리지 않는가

### 4. 각 봇은 독립 서비스로 유지한다
DMIB나 이후 봇들은 각각 자기 역할과 자기 Slack 전송을 담당한다.
`agent-monitor`는 중앙 감시 서비스이며, 실행 주체가 아니다.

### 5. scheduler와 polling 로직을 분리한다
scheduler는 "언제 누구를 확인할지"만 담당하고,
실제 HTTP 호출, 응답 파싱, 상태 판단은 별도 polling 서비스가 담당하는 편이 이후 확장에 유리하다.

이렇게 분리하면:
- 상태 저장 로직을 붙이기 쉽다
- incident 정책을 독립적으로 바꾸기 쉽다
- 테스트에서 스케줄러 없이 polling 동작만 검증할 수 있다

### 6. health와 last-run은 같은 실패가 아니다
- `health`는 서비스가 살아 있는지에 가깝다
- `last-run`은 서비스가 오늘 해야 할 일을 제대로 했는지에 가깝다

즉, 둘 다 장애 신호이지만 같은 방식으로 incident를 열면 노이즈가 많아질 수 있다.
초기 설계에서는 `availability`와 `execution` 성격을 구분해 다루는 것이 적절하다.

### 7. 관측 실패와 실제 실패를 분리한다
실제 서비스 실패와 모니터링 관측 실패는 다르다.

예를 들어:
- `last-run.status = FAILED`
  - 실제 업무 실패다
  - 즉시 incident 후보로 볼 수 있다
- `last-run endpoint timeout`
  - 관측 실패다
  - 네트워크 일시 장애일 수도 있다
  - 바로 incident를 열기보다 연속 실패 기준을 두는 편이 실무적으로 안정적이다

## 공통 수집 대상

초기 대상 endpoint:
- `GET /actuator/health`
- `GET /internal/monitoring/last-run`

## 현재 incident 정책 초안

### 즉시 incident 후보
- health 요청 실패
- health status가 `UP`이 아님
- `last-run.status`가 명백한 실패 상태

### 바로 incident로 열지 않는 경우
- `last-run endpoint` 일시 실패
- `last-run` 응답 파싱 실패
- 일시적인 관측 계층 오류

이 경우에는 먼저 `DEGRADED` 상태로 저장하고,
같은 문제가 연속될 때 incident로 승격하는 방향이 적절하다.

## 현재 구현 방향 요약

### seed 기반 monitored service 등록
- 첫 대상이 DMIB 하나로 정해져 있는 초기 단계에서는 CRUD보다 seed가 단순하고 빠르다
- 운영 중 동적 관리보다 monitoring pipeline 완성이 우선이다

### polling 결과 정규화
- health 응답과 last-run 응답을 하나의 polling 결과 모델로 묶는다
- 이후 저장/incident/alert는 이 모델을 기준으로 동작하게 한다

### 시간 비교 기준
- `sentAt` 같은 시간값은 문자열 표현보다 같은 시점인지가 더 중요하다
- 테스트와 저장 설계에서는 가능한 한 instant 기준 사고가 안전하다

### 상태 저장 계층의 트랜잭션 경계
- Kotlin + Spring에서 JDBC 저장 계층에 `@Transactional`을 붙일 때는 프록시 방식과 클래스 open 여부를 같이 봐야 한다
- 운영 코드에서는 저장 로직이 스케줄러 안으로 흩어지지 않게 별도 저장 계층으로 모으는 편이 유지보수에 유리하다
- `@Repository` stereotype를 쓰면 트랜잭션, 예외 변환, 테스트 wiring이 더 자연스럽다

### Compose project name은 명시적으로 관리한다
- `apps/<service>/repo` 구조는 흔하지만 Docker Compose 기본 project name은 마지막 디렉터리 이름을 따른다
- 여러 서비스가 모두 `repo` 디렉터리에서 compose를 실행하면 project name 충돌이 생길 수 있다
- 실무에서는 `name:` 또는 wrapper의 `-p`로 project name을 명시하는 편이 안전하다

### incident는 장애 lifecycle이지 이상 징후 저장소가 아니다
- polling 결과는 모두 `history/current_status`에 남기고, incident는 그중 운영 장애로 승격된 경우만 관리하는 편이 좋다
- 이 분리가 있어야 "무슨 일이 있었나"와 "지금 대응 중인 장애가 무엇인가"를 구분할 수 있다

### 1차 incident 정책은 보수적으로 시작한다
- `health != UP`은 availability 장애로 보고 즉시 open
- `runStatus = FAILED/ERROR`는 execution 장애로 보고 즉시 open
- `last-run endpoint timeout` 같은 관측 실패는 우선 상태 저장만 하고, 연속 실패 판단은 다음 단계로 넘긴다
- 실무에서는 이렇게 해야 초반 운영에서 false positive와 alert fatigue를 줄이기 쉽다

### 연속 실패 판단은 history를 함께 봐야 한다
- 관측 실패를 incident로 승격할 때 현재 poll 결과만 보면 일시 장애와 지속 장애를 구분하기 어렵다
- 최근 history를 시간 역순으로 보고, 정상 poll이 끼지 않은 연속 실패 구간인지 확인해야 한다
- 초반엔 3회 정도의 보수적 threshold로 시작하고 운영 데이터를 보면서 조정하는 편이 실무적이다

## 나중에 붙일 수 있는 기술

### Redis
- 알림 dedupe 강화
- 분산락
- 최근 상태 캐시

### Kafka
- 서비스 수 증가
- 상태 변경 이벤트 비동기 수집
- 중앙 집계 성능 요구 증가

### SSE 또는 WebSocket
- Slack 외에 대시보드가 필요할 때
- 초기 우선순위는 WebSocket보다 SSE가 높다

### Testcontainers
- PostgreSQL/H2 차이를 줄이고 싶을 때
- 인프라 의존 테스트를 실전형으로 강화하고 싶을 때
