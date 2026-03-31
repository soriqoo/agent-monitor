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

## 추천 학습 순서

1. JDBC 기반 상태 저장
2. polling scheduler 구현
3. incident open/close 조건 설계
4. Slack alert 정책 설계
5. Testcontainers 도입 검토
