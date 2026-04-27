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

## Running With Docker

This repository now includes an operational Docker baseline for OCI-style service deployment.

Recommended structure on the server:

```text
/home/ubuntu/ai_project/apps/agent-monitor
  data/
  logs/
  repo/
  runtime/
```

Recommended deployment shape:
- `agent-monitor` runs in its own Docker Compose project
- PostgreSQL for `agent-monitor` runs with that project
- DMIB remains a separate Compose project
- both services join the same external Docker network so `agent-monitor` can poll `dmib:8080`

Quick start:

```bash
cp .env.example .env
docker network create bot-monitoring
docker compose up -d --build
```

Recommended server workflow:

```bash
cd /home/ubuntu/ai_project/apps/agent-monitor/runtime
cp ../repo/ops/agent-monitor.sh.example ./agent-monitor.sh
chmod +x ./agent-monitor.sh
./agent-monitor.sh sync
./agent-monitor.sh deploy
./agent-monitor.sh status
./agent-monitor.sh logs
```

Default Docker expectation:
- `agent-monitor` host port: `127.0.0.1:18080`
- `agent-monitor` DB host port: `127.0.0.1:15433`
- DMIB base URL inside the shared network: `http://dmib:8080`

Important network note:
- if `agent-monitor` runs as a container, `http://127.0.0.1:8080` points back to itself, not to DMIB
- container-to-container polling should use the shared network hostname such as `http://dmib:8080`

The tracked template lives in [`ops/agent-monitor.sh.example`](D:/Toy_Project/agent-monitor/ops/agent-monitor.sh.example).
The actual runtime script should stay outside Git in `runtime/agent-monitor.sh`, next to `.env`.

Runtime layout:

```text
/home/ubuntu/ai_project/apps/agent-monitor
  runtime/
    agent-monitor.sh
    .env
  repo/
    ops/
      agent-monitor.sh.example
```

Available helper commands:
- `./agent-monitor.sh sync`
- `./agent-monitor.sh deploy`
- `./agent-monitor.sh ps`
- `./agent-monitor.sh logs`
- `./agent-monitor.sh logs-follow`
- `./agent-monitor.sh health`
- `./agent-monitor.sh summary`
- `./agent-monitor.sh status`

Recommended convention:
- keep project-specific runtime scripts in `runtime/<service>.sh`
- keep the tracked sample in the repository as `ops/<service>.sh.example`
- document the shared rule once in your common ops guidance, then keep each repository's sample concrete and service-specific

## Repository Standards

- `main` is protected by convention and should receive reviewed changes
- day-to-day work should happen in `feature/*` branches
- runtime secrets such as real `.env` files and webhook URLs are not committed
- docs are maintained alongside code, not as a separate afterthought

Recommended workflow:

```bash
git config user.name "soriqoo"
git config user.email "107284670+soriqoo@users.noreply.github.com"

git switch main
git pull --ff-only
git switch -c feature/<task-name>

# work...
git add .
git commit -m "..."
git push -u origin feature/<task-name>
```

Author note:
- before the first commit in a personal project, verify local Git author settings for this repository
- recommended for personal work: `soriqoo <107284670+soriqoo@users.noreply.github.com>`
- keeping repo-local author config avoids leaking company email settings from other workspaces

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

Current implemented capabilities:
- monitoring summary endpoint
- seed-based DMIB registration
- monitored service CRUD API
- periodic health and last-run polling
- check history and current status persistence
- incident open and resolve lifecycle
- repeated observation-failure promotion
- Slack alerts for incident open and resolve
- operational Docker baseline for OCI-style deployment
- runtime shell template and helper workflow for repeatable server operations

Next implementation order:
- alert policy refinement and message formatting
- monitoring screen backed by summary and monitored service APIs
- operational smoke-test/runbook expansion
- optional richer dashboard or operator-facing admin surface
