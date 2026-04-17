package com.dbot.agentmonitor.incident

import com.dbot.agentmonitor.domain.ServiceCheckStatus
import com.dbot.agentmonitor.domain.ServicePollResult
import com.dbot.agentmonitor.store.ServiceStatusStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
class SlackIncidentAlertIntegrationTests {

    @Autowired
    lateinit var incidentService: IncidentService

    @Autowired
    lateinit var serviceStatusStore: ServiceStatusStore

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        mockWebServer.takeRequest(50, TimeUnit.MILLISECONDS)
        jdbcTemplate.update("DELETE FROM alert_event")
        jdbcTemplate.update("DELETE FROM incident")
        jdbcTemplate.update("DELETE FROM service_current_status")
        jdbcTemplate.update("DELETE FROM service_check_history")
        jdbcTemplate.update("DELETE FROM monitored_service")
    }

    @Test
    fun incidentOpenSendsSlackAlertAndRecordsAlertEvent() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        recordAndApply(
            ServicePollResult(
                serviceName = "dmib",
                environment = "prod",
                healthStatus = ServiceCheckStatus.DOWN,
                runStatus = null,
                lastRunDate = null,
                lastSuccessAt = null,
                checkedAt = OffsetDateTime.parse("2026-04-17T10:00:00+09:00"),
                responseTimeMs = 3100,
                error = "Health request failed: timeout"
            )
        )

        val request = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        assertThat(request).isNotNull
        assertThat(request!!.path).isEqualTo("/slack")
        assertThat(request.body.readUtf8()).contains("Incident opened for dmib (prod)")

        val alertEvent = jdbcTemplate.queryForMap(
            """
            SELECT alert_type, message
            FROM alert_event
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            "dmib",
            "prod"
        )

        assertThat(alertEvent["alert_type"]).isEqualTo("INCIDENT_OPENED")
        assertThat(alertEvent["message"] as String).contains("Incident opened for dmib (prod)")
    }

    @Test
    fun incidentRecoverySendsSlackAlertAndRecordsAlertEvent() {
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'OPEN', ?, NULL, ?)
            """.trimIndent(),
            "dmib",
            "prod",
            OffsetDateTime.parse("2026-04-17T09:55:00+09:00"),
            "Health request failed: timeout"
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        recordAndApply(
            ServicePollResult(
                serviceName = "dmib",
                environment = "prod",
                healthStatus = ServiceCheckStatus.UP,
                runStatus = "SENT",
                lastRunDate = "2026-04-17",
                lastSuccessAt = OffsetDateTime.parse("2026-04-17T09:00:03+09:00"),
                checkedAt = OffsetDateTime.parse("2026-04-17T10:00:00+09:00"),
                responseTimeMs = 20,
                error = null
            )
        )

        val request = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        assertThat(request).isNotNull
        assertThat(request!!.body.readUtf8()).contains("Incident resolved for dmib (prod)")

        val alertEvent = jdbcTemplate.queryForMap(
            """
            SELECT alert_type, message
            FROM alert_event
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            "dmib",
            "prod"
        )

        assertThat(alertEvent["alert_type"]).isEqualTo("INCIDENT_RESOLVED")
        assertThat(alertEvent["message"] as String).contains("Incident resolved for dmib (prod)")
    }

    private fun recordAndApply(result: ServicePollResult) {
        serviceStatusStore.recordPollResult(result)
        incidentService.applyPollResult(result)
    }

    companion object {
        private val mockWebServer = MockWebServer().apply { start() }

        @JvmStatic
        @BeforeAll
        fun startServer() {
        }

        @JvmStatic
        @AfterAll
        fun stopServer() {
            mockWebServer.shutdown()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.slack.enabled") { true }
            registry.add("app.slack.webhook-url") { mockWebServer.url("/slack").toString() }
        }
    }
}
