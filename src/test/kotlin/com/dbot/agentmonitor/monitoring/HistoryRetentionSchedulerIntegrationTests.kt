package com.dbot.agentmonitor.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime

@SpringBootTest(
    properties = [
        "app.monitoring.retention-days=7"
    ]
)
@ActiveProfiles("test")
class HistoryRetentionSchedulerIntegrationTests {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var historyRetentionScheduler: HistoryRetentionScheduler

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM retention_run_history")
        jdbcTemplate.update("DELETE FROM alert_event")
        jdbcTemplate.update("DELETE FROM incident")
        jdbcTemplate.update("DELETE FROM service_current_status")
        jdbcTemplate.update("DELETE FROM service_check_history")
        jdbcTemplate.update("DELETE FROM monitored_service")
    }

    @Test
    fun runRetentionPrunesRowsUsingConfiguredRetentionDays() {
        val now = OffsetDateTime.now()
        insertServiceCheck("old-check", now.minusDays(8))
        insertServiceCheck("recent-check", now.minusDays(2))
        insertAlert("old-alert", now.minusDays(8))
        insertAlert("recent-alert", now.minusDays(2))
        insertResolvedIncident("old-resolved", now.minusDays(8), now.minusDays(8).plusMinutes(5))
        insertResolvedIncident("recent-resolved", now.minusDays(2), now.minusDays(2).plusMinutes(5))
        insertOpenIncident("old-open", now.minusDays(8))

        historyRetentionScheduler.runRetention()

        assertThat(countRows("service_check_history")).isEqualTo(1)
        assertThat(countRows("alert_event")).isEqualTo(1)
        assertThat(countRows("incident")).isEqualTo(2)
        assertThat(hasIncident("old-open", "OPEN")).isTrue()
        assertThat(hasIncident("recent-resolved", "RESOLVED")).isTrue()
    }

    @Test
    fun runRetentionRecordsOperationalVisibilityResult() {
        val now = OffsetDateTime.now()
        insertServiceCheck("old-check", now.minusDays(8))
        insertAlert("old-alert", now.minusDays(8))
        insertResolvedIncident("old-resolved", now.minusDays(8), now.minusDays(8).plusMinutes(5))

        historyRetentionScheduler.runRetention()

        val row = jdbcTemplate.queryForMap(
            """
            SELECT status,
                   retention_days,
                   deleted_service_checks,
                   deleted_alert_events,
                   deleted_resolved_incidents,
                   error
            FROM retention_run_history
            ORDER BY id DESC
            LIMIT 1
            """.trimIndent()
        )

        assertThat(row["STATUS"]).isEqualTo("SUCCESS")
        assertThat((row["RETENTION_DAYS"] as Number).toLong()).isEqualTo(7)
        assertThat((row["DELETED_SERVICE_CHECKS"] as Number).toInt()).isEqualTo(1)
        assertThat((row["DELETED_ALERT_EVENTS"] as Number).toInt()).isEqualTo(1)
        assertThat((row["DELETED_RESOLVED_INCIDENTS"] as Number).toInt()).isEqualTo(1)
        assertThat(row["ERROR"]).isNull()
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
            VALUES (?, 'prod', 'UP', 'SENT', '2026-05-19', 10, NULL, ?)
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
