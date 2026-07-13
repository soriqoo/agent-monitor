# Incident Reminder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send configurable Slack reminders for incidents that remain open beyond the reminder interval without producing duplicate alerts.

**Architecture:** Query open incidents whose latest opened/reminder alert, or incident open time when no alert exists, is older than a cutoff. A reminder service sends Slack through the existing alert service and records `INCIDENT_REMINDER` only after successful delivery. A lightweight scheduler invokes the service on a configurable cron and exits before querying when reminders are disabled.

**Tech Stack:** Kotlin, Spring Boot scheduling, JDBC, WebClient, PostgreSQL/H2, MockWebServer

## Global Constraints

- Reminders are disabled by default.
- Default reminder interval is 60 minutes.
- Default scheduler cadence is every 5 minutes.
- Existing incident open/resolve behavior and alert history remain compatible.
- No new database table is introduced.

---

### Task 1: Due Incident Query

**Files:**
- Modify: `src/main/kotlin/com/dbot/agentmonitor/domain/MonitoringModels.kt`
- Modify: `src/main/kotlin/com/dbot/agentmonitor/store/IncidentStore.kt`
- Test: `src/test/kotlin/com/dbot/agentmonitor/store/IncidentStoreIntegrationTests.kt`

**Interfaces:**
- Produces: `OpenIncidentReminderCandidate`
- Produces: `IncidentStore.findOpenIncidentsDueForReminder(cutoff: OffsetDateTime)`

- [ ] Add tests covering incident age, recent opened/reminder alerts, resolved incidents, and service/environment isolation.
- [ ] Run the focused store test and verify RED.
- [ ] Implement the correlated latest-alert query.
- [ ] Run the focused store test and verify GREEN.

### Task 2: Reminder Delivery Policy

**Files:**
- Modify: `src/main/kotlin/com/dbot/agentmonitor/config/AppProperties.kt`
- Modify: `src/main/kotlin/com/dbot/agentmonitor/alert/SlackAlertService.kt`
- Create: `src/main/kotlin/com/dbot/agentmonitor/incident/IncidentReminderService.kt`
- Test: `src/test/kotlin/com/dbot/agentmonitor/incident/IncidentReminderServiceIntegrationTests.kt`

**Interfaces:**
- Produces: `IncidentReminderService.sendDueReminders(now: OffsetDateTime): Int`
- Produces: `SlackAlertService.notifyIncidentReminder(candidate, remindedAt): Boolean`

- [ ] Add tests for disabled policy, successful delivery and event persistence, cadence suppression, and failed Slack delivery.
- [ ] Run the focused reminder tests and verify RED.
- [ ] Implement configuration, message formatting, delivery result, and reminder orchestration.
- [ ] Run focused reminder tests and verify GREEN.

### Task 3: Scheduler and Runtime Configuration

**Files:**
- Create: `src/main/kotlin/com/dbot/agentmonitor/incident/IncidentReminderScheduler.kt`
- Modify: `src/main/resources/application.yml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Test: `src/test/kotlin/com/dbot/agentmonitor/incident/IncidentReminderSchedulerTests.kt`

**Interfaces:**
- Consumes: `IncidentReminderService.sendDueReminders(now)`
- Produces: scheduled invocation controlled by `app.slack.incident-reminder-cron`

- [ ] Add a scheduler delegation test and verify RED.
- [ ] Implement scheduler and environment bindings.
- [ ] Run scheduler test and verify GREEN.
- [ ] Run `./gradlew --no-daemon clean test` and verify the full suite.
