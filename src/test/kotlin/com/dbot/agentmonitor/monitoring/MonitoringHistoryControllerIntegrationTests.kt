package com.dbot.agentmonitor.monitoring

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
class MonitoringHistoryControllerIntegrationTests {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
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
    fun historyReturnsRecentIncidentsAndAlerts() {
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'RESOLVED', ?, ?, ?)
            """.trimIndent(),
            "dmib",
            "prod",
            OffsetDateTime.parse("2026-04-27T15:00:00+09:00"),
            OffsetDateTime.parse("2026-04-27T15:05:00+09:00"),
            "Health request failed: timeout"
        )
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'OPEN', ?, NULL, ?)
            """.trimIndent(),
            "daily-english-bot",
            "prod",
            OffsetDateTime.parse("2026-04-27T16:00:00+09:00"),
            "Last-run endpoint failed"
        )

        jdbcTemplate.update(
            """
            INSERT INTO alert_event(service_name, environment, alert_type, message, sent_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            "dmib",
            "prod",
            "INCIDENT_RESOLVED",
            "Incident resolved for dmib (prod)",
            OffsetDateTime.parse("2026-04-27T15:05:05+09:00")
        )
        jdbcTemplate.update(
            """
            INSERT INTO alert_event(service_name, environment, alert_type, message, sent_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            "daily-english-bot",
            "prod",
            "INCIDENT_OPENED",
            "Incident opened for daily-english-bot (prod)",
            OffsetDateTime.parse("2026-04-27T16:00:05+09:00")
        )

        webTestClient.get()
            .uri("/api/monitoring/history?limit=5")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.incidents.length()").isEqualTo(2)
            .jsonPath("$.incidents[0].serviceName").isEqualTo("daily-english-bot")
            .jsonPath("$.incidents[0].status").isEqualTo("OPEN")
            .jsonPath("$.incidents[1].serviceName").isEqualTo("dmib")
            .jsonPath("$.alerts.length()").isEqualTo(2)
            .jsonPath("$.alerts[0].alertType").isEqualTo("INCIDENT_OPENED")
            .jsonPath("$.alerts[0].serviceName").isEqualTo("daily-english-bot")
            .jsonPath("$.alerts[1].alertType").isEqualTo("INCIDENT_RESOLVED")
    }

    @Test
    fun historyAppliesLimitPerCollectionAndReturnsNewestItemsFirst() {
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'RESOLVED', ?, ?, ?)
            """.trimIndent(),
            "old-bot",
            "prod",
            OffsetDateTime.parse("2026-04-27T13:00:00+09:00"),
            OffsetDateTime.parse("2026-04-27T13:05:00+09:00"),
            "Oldest incident"
        )
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'RESOLVED', ?, ?, ?)
            """.trimIndent(),
            "middle-bot",
            "prod",
            OffsetDateTime.parse("2026-04-27T14:00:00+09:00"),
            OffsetDateTime.parse("2026-04-27T14:10:00+09:00"),
            "Middle incident"
        )
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'OPEN', ?, NULL, ?)
            """.trimIndent(),
            "latest-bot",
            "prod",
            OffsetDateTime.parse("2026-04-27T15:00:00+09:00"),
            "Latest incident"
        )

        jdbcTemplate.update(
            """
            INSERT INTO alert_event(service_name, environment, alert_type, message, sent_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            "old-bot",
            "prod",
            "INCIDENT_RESOLVED",
            "Oldest alert",
            OffsetDateTime.parse("2026-04-27T13:05:05+09:00")
        )
        jdbcTemplate.update(
            """
            INSERT INTO alert_event(service_name, environment, alert_type, message, sent_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            "middle-bot",
            "prod",
            "INCIDENT_RESOLVED",
            "Middle alert",
            OffsetDateTime.parse("2026-04-27T14:10:05+09:00")
        )
        jdbcTemplate.update(
            """
            INSERT INTO alert_event(service_name, environment, alert_type, message, sent_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            "latest-bot",
            "prod",
            "INCIDENT_OPENED",
            "Latest alert",
            OffsetDateTime.parse("2026-04-27T15:00:05+09:00")
        )

        webTestClient.get()
            .uri("/api/monitoring/history?limit=2")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.incidents.length()").isEqualTo(2)
            .jsonPath("$.incidents[0].serviceName").isEqualTo("latest-bot")
            .jsonPath("$.incidents[1].serviceName").isEqualTo("middle-bot")
            .jsonPath("$.alerts.length()").isEqualTo(2)
            .jsonPath("$.alerts[0].serviceName").isEqualTo("latest-bot")
            .jsonPath("$.alerts[1].serviceName").isEqualTo("middle-bot")
    }

    @Test
    fun checksReturnsRecentServiceChecksAcrossServicesNewestFirst() {
        insertServiceCheck(
            serviceName = "old-bot",
            environment = "prod",
            healthStatus = "UP",
            runStatus = "SENT",
            lastRunDate = "2026-05-18",
            responseTimeMs = 42L,
            error = null,
            failureType = "NONE",
            checkedAt = OffsetDateTime.parse("2026-05-18T10:00:00+09:00")
        )
        insertServiceCheck(
            serviceName = "latest-bot",
            environment = "prod",
            healthStatus = "DOWN",
            runStatus = null,
            lastRunDate = null,
            responseTimeMs = 99L,
            error = "Health request failed: timeout",
            failureType = "HEALTH_FAILURE",
            checkedAt = OffsetDateTime.parse("2026-05-18T10:05:00+09:00")
        )

        webTestClient.get()
            .uri("/api/monitoring/checks?limit=5")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].serviceName").isEqualTo("latest-bot")
            .jsonPath("$[0].environment").isEqualTo("prod")
            .jsonPath("$[0].healthStatus").isEqualTo("DOWN")
            .jsonPath("$[0].failureType").isEqualTo("HEALTH_FAILURE")
            .jsonPath("$[0].error").isEqualTo("Health request failed: timeout")
            .jsonPath("$[1].serviceName").isEqualTo("old-bot")
            .jsonPath("$[1].runStatus").isEqualTo("SENT")
            .jsonPath("$[1].failureType").isEqualTo("NONE")
    }

    @Test
    fun checksCoercesLimitToSafeRange() {
        repeat(12) { index ->
            insertServiceCheck(
                serviceName = "bot-$index",
                environment = "prod",
                healthStatus = "UP",
                runStatus = "SENT",
                lastRunDate = "2026-05-18",
                responseTimeMs = index.toLong(),
                error = null,
                failureType = "NONE",
                checkedAt = OffsetDateTime.parse("2026-05-18T10:${index.toString().padStart(2, '0')}:00+09:00")
            )
        }

        webTestClient.get()
            .uri("/api/monitoring/checks?limit=999")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(10)
            .jsonPath("$[0].serviceName").isEqualTo("bot-11")
            .jsonPath("$[9].serviceName").isEqualTo("bot-2")
    }

    private fun insertServiceCheck(
        serviceName: String,
        environment: String,
        healthStatus: String,
        runStatus: String?,
        lastRunDate: String?,
        responseTimeMs: Long,
        error: String?,
        failureType: String,
        checkedAt: OffsetDateTime
    ) {
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
                failure_type,
                checked_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            serviceName,
            environment,
            healthStatus,
            runStatus,
            lastRunDate,
            responseTimeMs,
            error,
            failureType,
            checkedAt
        )
    }
}
