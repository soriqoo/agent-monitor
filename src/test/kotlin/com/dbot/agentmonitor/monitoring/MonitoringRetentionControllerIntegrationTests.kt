package com.dbot.agentmonitor.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.OffsetDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MonitoringRetentionControllerIntegrationTests {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM retention_run_history")
        jdbcTemplate.update("DELETE FROM alert_event")
        jdbcTemplate.update("DELETE FROM incident")
        jdbcTemplate.update("DELETE FROM service_current_status")
        jdbcTemplate.update("DELETE FROM service_check_history")
        jdbcTemplate.update("DELETE FROM monitored_service")

        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
    }

    @Test
    fun runRetentionCleanupPrunesOldRowsAndReturnsLatestRun() {
        val now = OffsetDateTime.now()
        insertServiceCheck("old-check", now.minusDays(31))
        insertServiceCheck("recent-check", now.minusDays(2))
        insertAlert("old-alert", now.minusDays(31))
        insertAlert("recent-alert", now.minusDays(2))
        insertResolvedIncident("old-resolved", now.minusDays(31), now.minusDays(31).plusMinutes(5))
        insertResolvedIncident("recent-resolved", now.minusDays(2), now.minusDays(2).plusMinutes(5))
        insertOpenIncident("old-open", now.minusDays(31))

        webTestClient.post()
            .uri("/api/monitoring/retention/run")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("SUCCESS")
            .jsonPath("$.retentionDays").isEqualTo(30)
            .jsonPath("$.deletedServiceChecks").isEqualTo(1)
            .jsonPath("$.deletedAlertEvents").isEqualTo(1)
            .jsonPath("$.deletedResolvedIncidents").isEqualTo(1)
            .jsonPath("$.completedAt").exists()

        assertThat(countRows("service_check_history")).isEqualTo(1)
        assertThat(countRows("alert_event")).isEqualTo(1)
        assertThat(countRows("incident")).isEqualTo(2)
        assertThat(hasIncident("old-open", "OPEN")).isTrue()
        assertThat(hasIncident("recent-resolved", "RESOLVED")).isTrue()
    }

    private fun insertServiceCheck(serviceName: String, checkedAt: OffsetDateTime) {
        jdbcTemplate.update(
            """
            INSERT INTO service_check_history(
                service_name,
                environment,
                health_status,
                run_status,
                last_run_date,
                response_time_ms,
                error,
                checked_at
            )
            VALUES (?, 'prod', 'UP', 'SENT', '2026-05-21', 10, NULL, ?)
            """.trimIndent(),
            serviceName,
            checkedAt
        )
    }

    private fun insertAlert(serviceName: String, sentAt: OffsetDateTime) {
        jdbcTemplate.update(
            """
            INSERT INTO alert_event(service_name, environment, alert_type, message, sent_at)
            VALUES (?, 'prod', 'INCIDENT_RESOLVED', 'message', ?)
            """.trimIndent(),
            serviceName,
            sentAt
        )
    }

    private fun insertResolvedIncident(
        serviceName: String,
        openedAt: OffsetDateTime,
        resolvedAt: OffsetDateTime
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, 'prod', 'RESOLVED', ?, ?, NULL)
            """.trimIndent(),
            serviceName,
            openedAt,
            resolvedAt
        )
    }

    private fun insertOpenIncident(serviceName: String, openedAt: OffsetDateTime) {
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, 'prod', 'OPEN', ?, NULL, 'still open')
            """.trimIndent(),
            serviceName,
            openedAt
        )
    }

    private fun countRows(tableName: String): Long {
        return jdbcTemplate.queryForObject("SELECT COUNT(1) FROM $tableName", Long::class.java) ?: 0L
    }

    private fun hasIncident(serviceName: String, status: String): Boolean {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM incident
            WHERE service_name = ? AND status = ?
            """.trimIndent(),
            Long::class.java,
            serviceName,
            status
        ) ?: 0L

        return count > 0
    }
}
