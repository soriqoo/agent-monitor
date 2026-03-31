# agent-monitor

Centralized monitoring service for polling multiple bot services, tracking execution status, and sending operational alerts to Slack.

`agent-monitor` is planned as the control-tower service for projects like DMIB. Each bot remains responsible for its own execution and business notifications, while `agent-monitor` focuses on health, last-run status, incident tracking, and centralized alerting.

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

## Getting Started

```bash
./gradlew test
```

## Current Status

This repository is currently scaffolded as an initial project shell.
The next step is to implement the first end-to-end flow with DMIB as the first monitored service.
