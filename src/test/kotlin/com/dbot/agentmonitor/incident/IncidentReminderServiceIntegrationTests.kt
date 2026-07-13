package com.dbot.agentmonitor.incident

import com.dbot.agentmonitor.alert.SlackAlertService
import com.dbot.agentmonitor.config.AppProperties
import com.dbot.agentmonitor.store.IncidentStore
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
class IncidentReminderServiceIntegrationTests {

    @Autowired
    lateinit var incidentReminderService: IncidentReminderService

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        mockWebServer.takeRequest(50, TimeUnit.MILLISECONDS)
        jdbcTemplate.update("DELETE FROM alert_event")
        jdbcTemplate.update("DELETE FROM incident")
    }

    @Test
    fun sendsDueReminderRecordsEventAndSuppressesImmediateRepeat() {
        val openedAt = OffsetDateTime.parse("2026-07-13T08:00:00Z")
        val remindedAt = OffsetDateTime.parse("2026-07-13T10:00:00Z")
        insertOpenIncident("dmib", "prod", openedAt, "database connection refused")
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        assertThat(incidentReminderService.sendDueReminders(remindedAt)).isEqualTo(1)

        val request = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        assertThat(request).isNotNull
        assertThat(request!!.path).isEqualTo("/slack")
        assertThat(request.body.readUtf8())
            .contains("Incident reminder for dmib (prod)")
            .contains("- openedAt: $openedAt")
            .contains("- elapsedOpenMinutes: 120")
            .contains("- lastError: database connection refused")

        val alertEvent = jdbcTemplate.queryForMap(
            """
            SELECT alert_type, sent_at
            FROM alert_event
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            "dmib",
            "prod"
        )
        assertThat(alertEvent["alert_type"]).isEqualTo("INCIDENT_REMINDER")
        assertThat(alertEvent["sent_at"] as OffsetDateTime).isEqualTo(remindedAt)

        assertThat(incidentReminderService.sendDueReminders(remindedAt)).isZero()
        assertThat(mockWebServer.takeRequest(100, TimeUnit.MILLISECONDS)).isNull()
    }

    @Test
    fun failedSlackReminderDoesNotRecordAlertEvent() {
        insertOpenIncident(
            "dmib",
            "prod",
            OffsetDateTime.parse("2026-07-13T08:00:00Z"),
            null
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        assertThat(incidentReminderService.sendDueReminders(OffsetDateTime.parse("2026-07-13T10:00:00Z")))
            .isZero()

        assertThat(mockWebServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull
        assertThat(alertEventCount()).isZero()
    }

    private fun insertOpenIncident(
        serviceName: String,
        environment: String,
        openedAt: OffsetDateTime,
        lastError: String?
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'OPEN', ?, NULL, ?)
            """.trimIndent(),
            serviceName,
            environment,
            openedAt,
            lastError
        )
    }

    private fun alertEventCount(): Long {
        return jdbcTemplate.queryForObject("SELECT COUNT(1) FROM alert_event", Long::class.java) ?: 0L
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
            registry.add("app.slack.incident-reminder-enabled") { true }
            registry.add("app.slack.incident-reminder-interval-minutes") { 60 }
        }
    }
}

class IncidentReminderServiceDisabledConfigurationTests {

    @Test
    fun genericSlackDisabledExitsWithoutQueryingOrSending() {
        assertNoReminderWork(
            AppProperties.Slack(
                enabled = false,
                webhookUrl = "http://localhost/slack",
                incidentReminderEnabled = true
            )
        )
    }

    @Test
    fun blankWebhookExitsWithoutQueryingOrSending() {
        assertNoReminderWork(
            AppProperties.Slack(
                enabled = true,
                webhookUrl = " ",
                incidentReminderEnabled = true
            )
        )
    }

    @Test
    fun disabledReminderPolicyExitsWithoutQueryingOrSending() {
        assertNoReminderWork(
            AppProperties.Slack(
                enabled = true,
                webhookUrl = "http://localhost/slack",
                incidentReminderEnabled = false
            )
        )
    }

    private fun assertNoReminderWork(slack: AppProperties.Slack) {
        val incidentStore = mock(IncidentStore::class.java)
        val slackAlertService = mock(SlackAlertService::class.java)
        val service = IncidentReminderService(
            incidentStore = incidentStore,
            appProperties = AppProperties(slack = slack),
            slackAlertService = slackAlertService
        )

        assertThat(service.sendDueReminders(OffsetDateTime.parse("2026-07-13T10:00:00Z"))).isZero()

        verifyNoInteractions(incidentStore, slackAlertService)
    }
}
