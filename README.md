# agent-monitor

Centralized monitoring service for polling multiple bot services, tracking execution status, and sending operational alerts to Slack.

`agent-monitor` is being built as the control-tower service for projects like DMIB. Each bot remains responsible for its own execution and business notifications, while `agent-monitor` focuses on health, last-run status, incident tracking, and centralized alerting.

## Overview

`agent-monitor` is intended to be a small but operationally minded backend service.
The goal is not just to expose status data, but to keep a reliable central view of whether monitored services are alive, whether their scheduled work succeeded, and whether an incident is still unresolved.

The first monitored service is DMIB, which already exposes:
- `GET /actuator/health`
- `GET /internal/monitoring/last-run`

## Scope

Initial goals:
- Register monitored services
- Poll each service's `health` and `last-run` endpoints
- Store current status and check history in PostgreSQL
- Open and close incidents based on unresolved failures
- Send Slack alerts for failure, recovery, and sustained issues

Initial monitored service:
- DMIB

## Planned Architecture

1. `service registry`
   - monitored target metadata
2. `polling scheduler`
   - periodic health and status checks
3. `status store`
   - current status and history persistence
4. `incident manager`
   - unresolved failure lifecycle
5. `slack alert`
   - operator-facing notifications

## Design Principles

- Start with polling before introducing event-driven infrastructure
- Separate service execution from central operational monitoring
- Store both current status and historical checks
- Keep repository, docs, tests, and runtime behavior aligned
- Grow toward a reusable monitoring contract for multiple bot services

## Tech Stack

- Kotlin
- Spring Boot
- JDBC
- PostgreSQL
- WebClient
- Actuator

Planned later, only if needed:
- Testcontainers
- Redis
- Kafka
- SSE or WebSocket dashboard

## Current Monitoring Contract

Required target endpoints:
- `GET /actuator/health`
- `GET /internal/monitoring/last-run`

Example `last-run` response:

```json
{
  "service": "dmib",
  "environment": "prod",
  "timezone": "Asia/Seoul",
  "lastRunDate": "2026-03-31",
  "status": "SENT",
  "sentAt": "2026-03-31T08:00:03+09:00",
  "error": null
}
```

## Running Locally

Recommended first check:

```bash
./gradlew test
```

Key environment variables:
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SLACK_ENABLED`
- `SLACK_WEBHOOK_URL`
- `APP_SEED_ENABLED`
- `DMIB_BASE_URL`
- `DMIB_ENVIRONMENT`
- `DMIB_ENABLED`

Notes:
- PostgreSQL is the default runtime database
- The test profile uses in-memory H2 in PostgreSQL compatibility mode
- DMIB can be auto-registered on startup through seed configuration

## Repository Standards

- `main` is protected by convention and should receive reviewed changes
- day-to-day work should happen in `feature/*` branches
- runtime secrets such as real `.env` files and webhook URLs are not committed
- docs are maintained alongside code, not as a separate afterthought

Recommended workflow:

```bash
git switch main
git pull --ff-only
git switch -c feature/<task-name>

# work...
git add .
git commit -m "..."
git push -u origin feature/<task-name>
```

## Documentation

- [`docs/PLAN.md`](D:/Toy_Project/agent-monitor/docs/PLAN.md)
- [`docs/RESEARCH.md`](D:/Toy_Project/agent-monitor/docs/RESEARCH.md)
- [`docs/STUDY.md`](D:/Toy_Project/agent-monitor/docs/STUDY.md)
- [`docs/RUNBOOK.md`](D:/Toy_Project/agent-monitor/docs/RUNBOOK.md)
- [`docs/DEPLOYMENT.md`](D:/Toy_Project/agent-monitor/docs/DEPLOYMENT.md)
- [`docs/AGENT_CONTRACT.md`](D:/Toy_Project/agent-monitor/docs/AGENT_CONTRACT.md)
- [`docs/CODEX_COLLABORATION.md`](D:/Toy_Project/agent-monitor/docs/CODEX_COLLABORATION.md)
- [`docs/CONTRIBUTING.md`](D:/Toy_Project/agent-monitor/docs/CONTRIBUTING.md)
- [`docs/SESSION_HANDOFF.md`](D:/Toy_Project/agent-monitor/docs/SESSION_HANDOFF.md)

## Current Status

Current scaffolded capabilities:
- monitoring summary endpoint
- monitored service store
- polling scheduler skeleton
- database schema draft
- documentation set for planning, research, study, runbook, and handoff

Next implementation order:
- seed-based DMIB registration
- real HTTP polling
- status/history persistence
- incident open/close flow
- Slack alert integration
