# Dummy Monitored Service

This is a small demo target for Agent Monitor. It is not an application feature.
Use it when you want to validate multi-service polling, incident handling, and
dashboard behavior before a real second service is ready.

## Endpoints

- `GET /actuator/health`
- `GET /internal/monitoring/last-run`

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

## Agent Monitor Registration

After starting the demo compose stack, register this service in the dashboard:

- Service name: `dummy-monitored-service`
- Base URL: `http://dummy-monitored-service:8080`
- Environment: `demo`
- Enabled: checked
