# 배포 가이드

## 현재 목표

초기 버전은 OCI에서 DMIB와 비슷한 운영 패턴으로 띄우되,
서비스 책임은 분리된 형태로 가져가는 것이다.

즉:
- `agent-monitor`는 별도 repo
- 별도 Docker Compose
- 별도 PostgreSQL
- DMIB와는 shared Docker network로만 연결

## 권장 운영 구조

```text
/home/ubuntu/ai_project/apps/agent-monitor
  data/
  logs/
  repo/
  runtime/
```

각 디렉터리 역할:
- `repo/`: GitHub에서 내려받은 소스와 공개 문서
- `runtime/`: `.env`, override 파일, 운영 스크립트
- `data/`: 영속 데이터 또는 볼륨 관리 기준점
- `logs/`: 운영 로그 수집 위치

## 현재 권장 배포 형태

### 1. 별도 Compose 프로젝트
`agent-monitor`는 자체 `docker-compose.yml`로 운영한다.

포함 대상:
- `agent-monitor` 애플리케이션 컨테이너
- `agent-monitor` 전용 PostgreSQL 컨테이너

이유:
- 중앙 모니터링 서비스의 배포 단위를 독립적으로 유지할 수 있다
- DMIB 변경과 `agent-monitor` 변경을 분리해서 배포할 수 있다
- 중앙 모니터링 DB와 업무 서비스 DB의 책임을 분리할 수 있다

### 2. shared external network로 DMIB 연결
DMIB와 `agent-monitor`는 서로 다른 Compose 프로젝트로 두되,
외부 Docker network 하나를 공유한다.

예시 network 이름:
- `bot-monitoring`

이 구조에서 `agent-monitor`는 DMIB를 아래처럼 본다.

```text
http://dmib:8080
```

중요한 점:
- 컨테이너 안에서 `127.0.0.1`은 자기 자신이다
- 따라서 `agent-monitor` 컨테이너에서 DMIB를 `127.0.0.1:8080`으로 보면 안 된다
- container-to-container 통신은 shared network hostname 기준으로 봐야 한다

## 서버 준비 순서

### 1. shared network 생성

```bash
docker network create bot-monitoring
```

### 2. DMIB도 같은 network에 연결
방법은 둘 중 하나다.

- DMIB Compose에 external network를 선언해서 재기동
- 또는 임시로 `docker network connect bot-monitoring dmib`

장기적으로는 Compose 파일에 반영하는 쪽이 더 좋다.

### 3. agent-monitor 환경 파일 준비

```bash
cp repo/.env.example runtime/.env
```

권장 주요 값:

```env
DB_URL=jdbc:postgresql://db:5432/agent_monitor
DB_USERNAME=agent_monitor
DB_PASSWORD=agent_monitor
DMIB_BASE_URL=http://dmib:8080
APP_SEED_ENABLED=true
AGENT_MONITOR_PORT=18080
SHARED_MONITORING_NETWORK=bot-monitoring
```

### 4. Compose 기동

```bash
docker compose --env-file runtime/.env up -d --build
```

## 현재 포함된 운영 파일

- `Dockerfile`
- `docker-compose.yml`
- `docker-compose.override.yml.example`
- `.env.example`

## 초기 검증 포인트

배포 후 최소 확인 항목:
- `docker compose ps`
- `curl http://127.0.0.1:18080/actuator/health`
- `curl http://127.0.0.1:18080/internal/monitoring/summary`
- `docker logs agent-monitor`

현재 단계에서 기대하는 것:
- DMIB seed 등록
- 실제 polling 수행 로그
- summary endpoint 정상 응답

아직 없는 것:
- 상태 저장 완성
- incident open/close
- Slack alert

즉, 지금은 운영형 smoke test는 가능하지만, full monitoring lifecycle은 아직 구현 전 단계다.

## 장기 목표

- GitHub Actions에서 테스트와 이미지 빌드 수행
- Registry push
- 서버는 `pull + up -d`
- 여러 봇 서비스가 같은 monitoring network 규칙을 공유
