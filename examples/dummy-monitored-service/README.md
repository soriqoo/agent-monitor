# Dummy Monitored Service

This is a small demo target for Agent Monitor. It is not an application feature.
Use it when you want to validate multi-service polling, incident handling, and
dashboard behavior before a real second service is ready.

## Endpoints

- `GET /actuator/health`
- `GET /internal/monitoring/last-run`
- `GET /internal/test-control`
- `PUT /internal/test-control/{scenario}`

## Runtime Controls

Set these environment variables in `docker-compose.demo.yml` or your runtime
environment to simulate different service states.

- `DUMMY_HEALTH_STATUS`: `UP` or `DOWN`
- `DUMMY_RUN_STATUS`: `SENT`, `FAILED`, or any run status string
- `DUMMY_LAST_RUN_DATE`: date string returned as `lastRunDate`
- `DUMMY_ERROR`: optional error string returned by `last-run`
- `DUMMY_SERVICE_NAME`: defaults to `dummy-monitored-service`
- `DUMMY_ENVIRONMENT`: defaults to `demo`
- `DUMMY_TIMEZONE`: defaults to `Asia/Seoul`

The demo compose stack also exposes the control endpoint on
`127.0.0.1:${DUMMY_SERVICE_PORT:-18081}`. Use the runtime shell to change the
scenario without recreating the container:

```bash
./agent-monitor.sh demo-mode status
./agent-monitor.sh demo-mode healthy
./agent-monitor.sh demo-mode last-run-unavailable
./agent-monitor.sh demo-mode health-down
./agent-monitor.sh demo-mode run-failed
./agent-monitor.sh demo-mode reset
```

- `healthy`: health `UP`, run status `SENT`, and today's last-run date
- `last-run-unavailable`: health remains `UP`, but last-run returns HTTP 503
- `health-down`: health returns HTTP 503 with status `DOWN`
- `run-failed`: health remains `UP`, but run status is `FAILED`
- `reset`: return to the environment-variable-driven state

## Agent Monitor Registration

After starting the demo compose stack, register this service in the dashboard:

- Service name: `dummy-monitored-service`
- Base URL: `http://dummy-monitored-service:8080`
- Environment: `demo`
- Enabled: checked
