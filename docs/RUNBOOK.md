# 운영 Runbook

## 문서 목적

이 문서는 `agent-monitor` 운영자가 상태 확인과 장애 대응을 할 때 참고하는 기준 문서다.

## 현재 운영 형태

- `agent-monitor`는 별도 Compose 프로젝트로 운영
- PostgreSQL도 `agent-monitor` 쪽에서 별도 운영
- DMIB와는 shared external Docker network로 통신
- DMIB 대상 polling 주소는 일반적으로 `http://dmib:8080`

중요:
- 컨테이너 안에서 `127.0.0.1`은 자기 자신이다
- 따라서 `agent-monitor` 컨테이너에서 DMIB를 `127.0.0.1:8080`으로 바라보면 안 된다

## 초기 확인 항목

- 컨테이너가 정상 기동했는가
- `/actuator/health`가 응답하는가
- `/internal/monitoring/summary`가 응답하는가
- 모니터링 대상 서비스 목록이 정상 로드되는가
- shared network에서 DMIB hostname이 해석 가능한가

## 표준 운영 명령 예시

- `agent-monitor deploy`
- `agent-monitor restart`
- `agent-monitor ps`
- `agent-monitor logs`
- `agent-monitor health`

예시 명령:

```bash
docker compose --env-file runtime/.env up -d --build
docker compose --env-file runtime/.env ps
docker compose --env-file runtime/.env logs -f agent-monitor
curl http://127.0.0.1:18080/actuator/health
curl http://127.0.0.1:18080/internal/monitoring/summary
```

## 장애 시 1차 대응 순서

1. health 확인
2. DB 연결 상태 확인
3. 대상 서비스 응답 실패인지, 중앙 서비스 문제인지 분리
4. 최근 incident와 alert event 확인
5. Slack 알림 중복 여부 확인

## 네트워크 문제 점검 순서

1. shared network가 존재하는가
2. DMIB 컨테이너가 그 network에 연결돼 있는가
3. `DMIB_BASE_URL`이 `http://dmib:8080` 같은 container hostname 기준으로 설정돼 있는가
4. `agent-monitor` 로그에 health/last-run 호출 실패가 기록되는가

## 현재 단계에서 가능한 운영 확인

- summary endpoint 확인
- polling 로그 확인
- DMIB 연동 smoke test

아직 어려운 것:
- DB에 누적된 status history 분석
- incident lifecycle 확인
- Slack alert 확인
