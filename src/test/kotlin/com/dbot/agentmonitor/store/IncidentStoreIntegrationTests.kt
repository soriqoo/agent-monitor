package com.dbot.agentmonitor.store

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime

@SpringBootTest
@ActiveProfiles("test")
class IncidentStoreIntegrationTests {

    @Autowired
    lateinit var incidentStore: IncidentStore

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM alert_event")
        jdbcTemplate.update("DELETE FROM incident")
    }

    @Test
    fun findsOpenIncidentsOlderThanCutoffWhenNoEligibleAlertExists() {
        val cutoff = OffsetDateTime.parse("2026-07-13T10:00:00Z")
        insertIncident("oldest-service", "prod", "OPEN", "2026-07-13T08:00:00Z", "oldest failure")
        insertIncident("older-service", "prod", "OPEN", "2026-07-13T09:00:00Z", "older failure")
        insertIncident("recent-service", "prod", "OPEN", "2026-07-13T10:00:00Z", "recent failure")

        val candidates = incidentStore.findOpenIncidentsDueForReminder(cutoff)

        assertThat(candidates.map { it.serviceName }).containsExactly("oldest-service", "older-service")
        assertThat(candidates.map { it.lastError }).containsExactly("oldest failure", "older failure")
    }

    @Test
    fun excludesIncidentWithRecentOpenedOrReminderAlert() {
        val cutoff = OffsetDateTime.parse("2026-07-13T10:00:00Z")
        insertIncident("opened-recently", "prod", "OPEN", "2026-07-13T08:00:00Z", "failure")
        insertIncident("reminded-recently", "prod", "OPEN", "2026-07-13T08:00:00Z", "failure")
        insertIncident("reminded-long-ago", "prod", "OPEN", "2026-07-13T08:00:00Z", "failure")
        insertIncident("unrelated-alert", "prod", "OPEN", "2026-07-13T08:00:00Z", "failure")
        insertAlert("opened-recently", "prod", "INCIDENT_OPENED", "2026-07-13T10:30:00Z")
        insertAlert("reminded-recently", "prod", "INCIDENT_REMINDER", "2026-07-13T10:30:00Z")
        insertAlert("reminded-long-ago", "prod", "INCIDENT_REMINDER", "2026-07-13T09:00:00Z")
        insertAlert("unrelated-alert", "prod", "INCIDENT_RESOLVED", "2026-07-13T09:30:00Z")

        val candidates = incidentStore.findOpenIncidentsDueForReminder(cutoff)

        assertThat(candidates.map { it.serviceName }).containsExactly("unrelated-alert", "reminded-long-ago")
    }

    @Test
    fun excludesResolvedIncidents() {
        val cutoff = OffsetDateTime.parse("2026-07-13T10:00:00Z")
        insertIncident("resolved-service", "prod", "RESOLVED", "2026-07-13T08:00:00Z", "failure")
        insertIncident("open-service", "prod", "OPEN", "2026-07-13T08:00:00Z", "failure")

        val candidates = incidentStore.findOpenIncidentsDueForReminder(cutoff)

        assertThat(candidates.map { it.serviceName }).containsExactly("open-service")
    }

    @Test
    fun onlyUsesAlertsFromTheSameServiceAndEnvironmentAfterTheIncidentOpened() {
        val cutoff = OffsetDateTime.parse("2026-07-13T10:00:00Z")
        insertIncident("target-service", "prod", "OPEN", "2026-07-13T08:00:00Z", "failure")
        insertAlert("target-service", "staging", "INCIDENT_REMINDER", "2026-07-13T09:30:00Z")
        insertAlert("other-service", "prod", "INCIDENT_REMINDER", "2026-07-13T09:30:00Z")
        insertAlert("target-service", "prod", "INCIDENT_REMINDER", "2026-07-13T07:30:00Z")

        val candidates = incidentStore.findOpenIncidentsDueForReminder(cutoff)

        val candidate = candidates.single()
        assertThat(candidate.serviceName).isEqualTo("target-service")
        assertThat(candidate.environment).isEqualTo("prod")
        assertThat(candidate.openedAt).isEqualTo(OffsetDateTime.parse("2026-07-13T08:00:00Z"))
        assertThat(candidate.lastError).isEqualTo("failure")
    }

    @Test
    fun excludesIncidentWhenEligibleAlertIsExactlyAtCutoff() {
        val cutoff = OffsetDateTime.parse("2026-07-13T10:00:00Z")
        insertIncident("target-service", "prod", "OPEN", "2026-07-13T08:00:00Z", "failure")
        insertAlert("target-service", "prod", "INCIDENT_REMINDER", "2026-07-13T10:00:00Z")

        assertThat(incidentStore.findOpenIncidentsDueForReminder(cutoff)).isEmpty()
    }

    @Test
    fun latestEligibleAlertDeterminesWhetherIncidentIsDue() {
        val cutoff = OffsetDateTime.parse("2026-07-13T10:00:00Z")
        insertIncident("target-service", "prod", "OPEN", "2026-07-13T08:00:00Z", "failure")
        insertAlert("target-service", "prod", "INCIDENT_REMINDER", "2026-07-13T09:00:00Z")
        insertAlert("target-service", "prod", "INCIDENT_OPENED", "2026-07-13T10:30:00Z")

        assertThat(incidentStore.findOpenIncidentsDueForReminder(cutoff)).isEmpty()
    }

    @Test
    fun ordersEqualTimeCandidatesByIncidentId() {
        val cutoff = OffsetDateTime.parse("2026-07-13T10:00:00Z")
        insertIncident("first-service", "prod", "OPEN", "2026-07-13T08:00:00Z", "failure")
        insertIncident("second-service", "prod", "OPEN", "2026-07-13T08:00:00Z", "failure")

        val candidates = incidentStore.findOpenIncidentsDueForReminder(cutoff)

        assertThat(candidates.map { it.serviceName }).containsExactly("first-service", "second-service")
        assertThat(candidates.map { it.id }).isSorted()
    }

    private fun insertIncident(
        serviceName: String,
        environment: String,
        status: String,
        openedAt: String,
        lastError: String
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, ?, ?, NULL, ?)
            """.trimIndent(),
            serviceName,
            environment,
            status,
            OffsetDateTime.parse(openedAt),
            lastError
        )
    }

    private fun insertAlert(
        serviceName: String,
        environment: String,
        alertType: String,
        sentAt: String
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO alert_event(service_name, environment, alert_type, message, sent_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            serviceName,
            environment,
            alertType,
            "test alert",
            OffsetDateTime.parse(sentAt)
        )
    }
}
