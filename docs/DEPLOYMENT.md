# 배포 가이드

## 현재 목표

초기 버전은 로컬 개발과 구조 확정이 우선이다.
배포는 나중에 DMIB와 유사한 운영 패턴을 따라가되, 장기적으로는 image pull 기반 배포를 목표로 한다.

## 권장 운영 구조

```text
/home/ubuntu/ai_project/apps/agent-monitor
  data/
  logs/
  repo/
  runtime/
```

## 초기 배포 방향

- source 기반 운영으로 시작 가능
- `.env`와 운영 스크립트는 `runtime/` 분리
- 이후 GitHub Actions + Registry 기반 배포로 전환

## 장기 목표

- CI에서 이미지 빌드
- Registry push
- 서버는 `pull + up -d`
