package com.dbot.agentmonitor.monitoring

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MonitoringSummaryControllerIntegrationTests {

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
    fun summaryReturnsBootstrappedStatusAndCounts() {
        jdbcTemplate.update(
            """
            INSERT INTO monitored_service(service_name, base_url, environment, enabled)
            VALUES (?, ?, ?, TRUE)
            """.trimIndent(),
            "dmib",
            "http://localhost:8080",
            "prod"
        )

        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'OPEN', CURRENT_TIMESTAMP, NULL, ?)
            """.trimIndent(),
            "dmib",
            "prod",
            "sample error"
        )

        webTestClient.get()
            .uri("/internal/monitoring/summary")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.service").isEqualTo("agent-monitor")
            .jsonPath("$.timezone").isEqualTo("Asia/Seoul")
            .jsonPath("$.registeredServices").isEqualTo(1)
            .jsonPath("$.enabledServices").isEqualTo(1)
            .jsonPath("$.openIncidents").isEqualTo(1)
            .jsonPath("$.status").isEqualTo("BOOTSTRAPPED")
            .jsonPath("$.retention.lastRun").doesNotExist()
    }

    @Test
    fun summaryCountsMultipleRegisteredEnabledAndOpenIncidentServices() {
        jdbcTemplate.update(
            """
            INSERT INTO monitored_service(service_name, base_url, environment, enabled)
            VALUES (?, ?, ?, TRUE)
            """.trimIndent(),
            "dmib",
            "http://dmib:8080",
            "prod"
        )
        jdbcTemplate.update(
            """
            INSERT INTO monitored_service(service_name, base_url, environment, enabled)
            VALUES (?, ?, ?, FALSE)
            """.trimIndent(),
            "daily-english-bot",
            "http://daily-english-bot:8080",
            "prod"
        )
        jdbcTemplate.update(
            """
            INSERT INTO monitored_service(service_name, base_url, environment, enabled)
            VALUES (?, ?, ?, TRUE)
            """.trimIndent(),
            "dmib",
            "http://dmib-dev:8080",
            "dev"
        )

        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'OPEN', CURRENT_TIMESTAMP, NULL, ?)
            """.trimIndent(),
            "dmib",
            "prod",
            "dmib prod is down"
        )
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'RESOLVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
            """.trimIndent(),
            "daily-english-bot",
            "prod",
            "resolved issue should not count"
        )

        webTestClient.get()
            .uri("/internal/monitoring/summary")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.registeredServices").isEqualTo(3)
            .jsonPath("$.enabledServices").isEqualTo(2)
            .jsonPath("$.openIncidents").isEqualTo(1)
    }

    @Test
    fun summaryReturnsLatestRetentionRun() {
        jdbcTemplate.update(
            """
            INSERT INTO retention_run_history(
                status,
                retention_days,
                deleted_service_checks,
                deleted_alert_events,
                deleted_resolved_incidents,
                started_at,
                completed_at,
                error
            )
            VALUES ('SUCCESS', 30, 4, 2, 1, ?, ?, NULL)
            """.trimIndent(),
            "2026-05-19T03:30:00+09:00",
            "2026-05-19T03:30:02+09:00"
        )

        webTestClient.get()
            .uri("/internal/monitoring/summary")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.retention.lastRun.status").isEqualTo("SUCCESS")
            .jsonPath("$.retention.lastRun.retentionDays").isEqualTo(30)
            .jsonPath("$.retention.lastRun.deletedServiceChecks").isEqualTo(4)
            .jsonPath("$.retention.lastRun.deletedAlertEvents").isEqualTo(2)
            .jsonPath("$.retention.lastRun.deletedResolvedIncidents").isEqualTo(1)
            .jsonPath("$.retention.lastRun.completedAt").isEqualTo("2026-05-19T03:30:02+09:00")
    }
}
