package com.dbot.agentmonitor.registry

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
class MonitoredServiceOverviewIntegrationTests {

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
    fun overviewReturnsJoinedCurrentStatusAndIncidentState() {
        jdbcTemplate.update(
            """
            INSERT INTO monitored_service(service_name, base_url, environment, enabled)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            "dmib",
            "http://dmib:8080",
            "prod",
            true
        )
        jdbcTemplate.update(
            """
            INSERT INTO service_current_status(
                service_name,
                environment,
                health_status,
                run_status,
                last_run_date,
                last_success_at,
                last_checked_at,
                error,
                failure_type
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            "dmib",
            "prod",
            "UP",
            "SENT",
            "2026-04-27",
            OffsetDateTime.parse("2026-04-27T08:00:03+09:00"),
            OffsetDateTime.parse("2026-04-27T09:00:00+09:00"),
            null,
            "NONE"
        )
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'OPEN', ?, NULL, ?)
            """.trimIndent(),
            "dmib",
            "prod",
            OffsetDateTime.parse("2026-04-27T09:05:00+09:00"),
            "Health request failed: timeout"
        )

        webTestClient.get()
            .uri("/api/monitored-services/overview")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].serviceName").isEqualTo("dmib")
            .jsonPath("$[0].baseUrl").isEqualTo("http://dmib:8080")
            .jsonPath("$[0].healthStatus").isEqualTo("UP")
            .jsonPath("$[0].runStatus").isEqualTo("SENT")
            .jsonPath("$[0].lastRunDate").isEqualTo("2026-04-27")
            .jsonPath("$[0].failureType").isEqualTo("NONE")
            .jsonPath("$[0].openIncident").isEqualTo(true)
    }

    @Test
    fun overviewReturnsMultipleServicesWithIndependentStatusAndIncidentState() {
        jdbcTemplate.update(
            """
            INSERT INTO monitored_service(service_name, base_url, environment, enabled)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            "dmib",
            "http://dmib:8080",
            "prod",
            true
        )
        jdbcTemplate.update(
            """
            INSERT INTO monitored_service(service_name, base_url, environment, enabled)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            "daily-english-bot",
            "http://daily-english-bot:8080",
            "prod",
            false
        )

        jdbcTemplate.update(
            """
            INSERT INTO service_current_status(
                service_name,
                environment,
                health_status,
                run_status,
                last_run_date,
                last_success_at,
                last_checked_at,
                error,
                failure_type
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            "dmib",
            "prod",
            "UP",
            "SENT",
            "2026-05-15",
            OffsetDateTime.parse("2026-05-15T08:00:03+09:00"),
            OffsetDateTime.parse("2026-05-15T08:05:00+09:00"),
            null,
            "NONE"
        )
        jdbcTemplate.update(
            """
            INSERT INTO service_current_status(
                service_name,
                environment,
                health_status,
                run_status,
                last_run_date,
                last_success_at,
                last_checked_at,
                error,
                failure_type
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            "daily-english-bot",
            "prod",
            "DOWN",
            null,
            null,
            null,
            OffsetDateTime.parse("2026-05-15T08:10:00+09:00"),
            "Health request failed",
            "HEALTH_FAILURE"
        )

        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'OPEN', ?, NULL, ?)
            """.trimIndent(),
            "daily-english-bot",
            "prod",
            OffsetDateTime.parse("2026-05-15T08:10:00+09:00"),
            "Health request failed"
        )

        webTestClient.get()
            .uri("/api/monitored-services/overview")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].serviceName").isEqualTo("dmib")
            .jsonPath("$[0].enabled").isEqualTo(true)
            .jsonPath("$[0].healthStatus").isEqualTo("UP")
            .jsonPath("$[0].failureType").isEqualTo("NONE")
            .jsonPath("$[0].openIncident").isEqualTo(false)
            .jsonPath("$[1].serviceName").isEqualTo("daily-english-bot")
            .jsonPath("$[1].enabled").isEqualTo(false)
            .jsonPath("$[1].healthStatus").isEqualTo("DOWN")
            .jsonPath("$[1].failureType").isEqualTo("HEALTH_FAILURE")
            .jsonPath("$[1].openIncident").isEqualTo(true)
    }

    @Test
    fun overviewReturnsNullForUnknownStoredFailureType() {
        jdbcTemplate.update(
            """
            INSERT INTO monitored_service(service_name, base_url, environment, enabled)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            "legacy-service",
            "http://legacy-service:8080",
            "prod",
            true
        )
        jdbcTemplate.update(
            """
            INSERT INTO service_current_status(
                service_name, environment, health_status, last_checked_at, failure_type
            )
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            "legacy-service",
            "prod",
            "UP",
            OffsetDateTime.parse("2026-05-15T08:05:00+09:00"),
            "LEGACY_FAILURE"
        )

        webTestClient.get()
            .uri("/api/monitored-services/overview")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].serviceName").isEqualTo("legacy-service")
            .jsonPath("$[0].failureType").isEmpty()
    }
}
