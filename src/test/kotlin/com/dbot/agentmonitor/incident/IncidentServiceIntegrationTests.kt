package com.dbot.agentmonitor.incident

import com.dbot.agentmonitor.domain.ServiceCheckStatus
import com.dbot.agentmonitor.domain.ServicePollResult
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
class IncidentServiceIntegrationTests {

    @Autowired
    lateinit var incidentService: IncidentService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM alert_event")
        jdbcTemplate.update("DELETE FROM incident")
        jdbcTemplate.update("DELETE FROM service_current_status")
        jdbcTemplate.update("DELETE FROM service_check_history")
        jdbcTemplate.update("DELETE FROM monitored_service")
    }

    @Test
    fun healthDownOpensIncident() {
        incidentService.applyPollResult(
            ServicePollResult(
                serviceName = "dmib",
                environment = "prod",
                healthStatus = ServiceCheckStatus.DOWN,
                runStatus = null,
                lastRunDate = null,
                lastSuccessAt = null,
                checkedAt = OffsetDateTime.parse("2026-04-08T10:00:00+09:00"),
                responseTimeMs = 3000,
                error = "Health request failed: timeout"
            )
        )

        assertThat(openIncidentCount("dmib", "prod")).isEqualTo(1)
    }

    @Test
    fun explicitRunFailureOpensIncident() {
        incidentService.applyPollResult(
            ServicePollResult(
                serviceName = "dmib",
                environment = "prod",
                healthStatus = ServiceCheckStatus.UP,
                runStatus = "FAILED",
                lastRunDate = "2026-04-08",
                lastSuccessAt = null,
                checkedAt = OffsetDateTime.parse("2026-04-08T10:00:00+09:00"),
                responseTimeMs = 25,
                error = "Slack send failed"
            )
        )

        assertThat(openIncidentCount("dmib", "prod")).isEqualTo(1)
    }

    @Test
    fun degradedObservationFailureDoesNotOpenIncident() {
        incidentService.applyPollResult(
            ServicePollResult(
                serviceName = "dmib",
                environment = "prod",
                healthStatus = ServiceCheckStatus.UP,
                runStatus = null,
                lastRunDate = null,
                lastSuccessAt = null,
                checkedAt = OffsetDateTime.parse("2026-04-08T10:00:00+09:00"),
                responseTimeMs = 3100,
                error = "Last-run request failed: timeout"
            )
        )

        assertThat(openIncidentCount("dmib", "prod")).isZero()
    }

    @Test
    fun healthyRecoveryClosesOpenIncident() {
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'OPEN', ?, NULL, ?)
            """.trimIndent(),
            "dmib",
            "prod",
            OffsetDateTime.parse("2026-04-08T09:00:00+09:00"),
            "Health request failed: timeout"
        )

        incidentService.applyPollResult(
            ServicePollResult(
                serviceName = "dmib",
                environment = "prod",
                healthStatus = ServiceCheckStatus.UP,
                runStatus = "SENT",
                lastRunDate = "2026-04-08",
                lastSuccessAt = OffsetDateTime.parse("2026-04-08T08:00:03+09:00"),
                checkedAt = OffsetDateTime.parse("2026-04-08T10:00:00+09:00"),
                responseTimeMs = 18,
                error = null
            )
        )

        val resolved = jdbcTemplate.queryForMap(
            """
            SELECT status, resolved_at
            FROM incident
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            "dmib",
            "prod"
        )

        assertThat(resolved["status"]).isEqualTo("RESOLVED")
        assertThat(resolved["resolved_at"]).isNotNull()
    }

    @Test
    fun repeatedFailureDoesNotCreateDuplicateOpenIncidents() {
        val firstFailure = ServicePollResult(
            serviceName = "dmib",
            environment = "prod",
            healthStatus = ServiceCheckStatus.DOWN,
            runStatus = null,
            lastRunDate = null,
            lastSuccessAt = null,
            checkedAt = OffsetDateTime.parse("2026-04-08T10:00:00+09:00"),
            responseTimeMs = 3000,
            error = "Health request failed: timeout"
        )

        incidentService.applyPollResult(firstFailure)
        incidentService.applyPollResult(firstFailure.copy(checkedAt = OffsetDateTime.parse("2026-04-08T10:05:00+09:00")))

        assertThat(openIncidentCount("dmib", "prod")).isEqualTo(1)
    }

    private fun openIncidentCount(serviceName: String, environment: String): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM incident
            WHERE service_name = ? AND environment = ? AND status = 'OPEN'
            """.trimIndent(),
            Long::class.java,
            serviceName,
            environment
        ) ?: 0L
    }
}
