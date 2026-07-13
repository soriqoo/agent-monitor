package com.dbot.agentmonitor.store

import com.dbot.agentmonitor.domain.ServiceCheckStatus
import com.dbot.agentmonitor.domain.ServicePollResult
import com.dbot.agentmonitor.domain.PollFailureType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime

@SpringBootTest
@ActiveProfiles("test")
class ServiceStatusStoreIntegrationTests {

    @Autowired
    lateinit var serviceStatusStore: ServiceStatusStore

    @Autowired
    lateinit var jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM alert_event")
        jdbcTemplate.update("DELETE FROM incident")
        jdbcTemplate.update("DELETE FROM service_current_status")
        jdbcTemplate.update("DELETE FROM service_check_history")
        jdbcTemplate.update("DELETE FROM monitored_service")
    }

    @Test
    fun recordPollResultAppendsHistoryAndCreatesCurrentStatus() {
        val checkedAt = OffsetDateTime.parse("2026-04-08T09:00:00+09:00")
        val result = ServicePollResult(
            serviceName = "dmib",
            environment = "prod",
            healthStatus = ServiceCheckStatus.UP,
            runStatus = "SENT",
            lastRunDate = "2026-04-08",
            lastSuccessAt = OffsetDateTime.parse("2026-04-08T08:00:03+09:00"),
            checkedAt = checkedAt,
            responseTimeMs = 42,
            error = null,
            failureType = PollFailureType.NONE
        )

        serviceStatusStore.recordPollResult(result)

        assertThat(serviceStatusStore.countHistory("dmib", "prod")).isEqualTo(1)

        val current = jdbcTemplate.queryForMap(
            """
            SELECT service_name, environment, health_status, run_status, last_run_date, error, failure_type
            FROM service_current_status
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            "dmib",
            "prod"
        )

        assertThat(current["service_name"]).isEqualTo("dmib")
        assertThat(current["environment"]).isEqualTo("prod")
        assertThat(current["health_status"]).isEqualTo("UP")
        assertThat(current["run_status"]).isEqualTo("SENT")
        assertThat(current["last_run_date"]).isEqualTo("2026-04-08")
        assertThat(current["error"]).isNull()
        assertThat(current["failure_type"]).isEqualTo("NONE")

        val historyFailureType = jdbcTemplate.queryForObject(
            "SELECT failure_type FROM service_check_history WHERE service_name = ? AND environment = ?",
            String::class.java,
            "dmib",
            "prod"
        )
        assertThat(historyFailureType).isEqualTo("NONE")
    }

    @Test
    fun recordPollResultUpdatesCurrentStatusAndKeepsHistory() {
        serviceStatusStore.recordPollResult(
            ServicePollResult(
                serviceName = "dmib",
                environment = "prod",
                healthStatus = ServiceCheckStatus.UP,
                runStatus = "SENT",
                lastRunDate = "2026-04-08",
                lastSuccessAt = OffsetDateTime.parse("2026-04-08T08:00:03+09:00"),
                checkedAt = OffsetDateTime.parse("2026-04-08T09:00:00+09:00"),
                responseTimeMs = 20,
                error = null
            )
        )

        serviceStatusStore.recordPollResult(
            ServicePollResult(
                serviceName = "dmib",
                environment = "prod",
                healthStatus = ServiceCheckStatus.DEGRADED,
                runStatus = null,
                lastRunDate = null,
                lastSuccessAt = null,
                checkedAt = OffsetDateTime.parse("2026-04-08T09:05:00+09:00"),
                responseTimeMs = 3100,
                error = "Last-run request failed: timeout",
                failureType = PollFailureType.OBSERVATION_FAILURE
            )
        )

        assertThat(serviceStatusStore.countHistory("dmib", "prod")).isEqualTo(2)

        val current = jdbcTemplate.queryForMap(
            """
            SELECT health_status, run_status, last_run_date, error, failure_type
            FROM service_current_status
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            "dmib",
            "prod"
        )

        assertThat(current["health_status"]).isEqualTo("DEGRADED")
        assertThat(current["run_status"]).isNull()
        assertThat(current["last_run_date"]).isNull()
        assertThat(current["error"]).isEqualTo("Last-run request failed: timeout")
        assertThat(current["failure_type"]).isEqualTo("OBSERVATION_FAILURE")
    }

    @Test
    fun unknownStoredFailureTypeIsIgnoredWhenReadingRecentChecks() {
        jdbcTemplate.update(
            """
            INSERT INTO service_check_history(
                service_name, environment, health_status, failure_type, checked_at
            )
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            "legacy-service",
            "prod",
            "DEGRADED",
            "LEGACY_FAILURE",
            OffsetDateTime.parse("2026-04-08T09:00:00+09:00")
        )

        val checks = serviceStatusStore.findRecentChecks("legacy-service", "prod", 1)

        assertThat(checks).hasSize(1)
        assertThat(checks.single().failureType).isNull()
    }

    @Test
    fun nullLegacyFailureTypeDoesNotCountAsTypedFailure() {
        jdbcTemplate.update(
            """
            INSERT INTO service_check_history(
                service_name, environment, health_status, failure_type, checked_at
            )
            VALUES (?, ?, ?, NULL, ?)
            """.trimIndent(),
            "legacy-service",
            "prod",
            "DEGRADED",
            OffsetDateTime.parse("2026-04-08T09:00:00+09:00")
        )

        val checks = serviceStatusStore.findRecentChecks("legacy-service", "prod", 1)

        assertThat(checks.single().failureType).isNull()
    }
}
