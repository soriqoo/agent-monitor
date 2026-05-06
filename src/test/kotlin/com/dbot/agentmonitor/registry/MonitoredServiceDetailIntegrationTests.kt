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
class MonitoredServiceDetailIntegrationTests {

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
    fun detailReturnsFocusedServiceOverviewIncidentsAndAlerts() {
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
        val serviceId = jdbcTemplate.queryForObject(
            "SELECT id FROM monitored_service WHERE service_name = 'dmib' AND environment = 'prod'",
            Long::class.java
        )!!

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
                error
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            "dmib",
            "prod",
            "UP",
            "SENT",
            "2026-05-06",
            OffsetDateTime.parse("2026-05-06T08:00:00+09:00"),
            OffsetDateTime.parse("2026-05-06T08:05:00+09:00"),
            null
        )

        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'RESOLVED', ?, ?, ?)
            """.trimIndent(),
            "dmib",
            "prod",
            OffsetDateTime.parse("2026-05-06T08:00:00+09:00"),
            OffsetDateTime.parse("2026-05-06T08:10:00+09:00"),
            "Health request failed: timeout"
        )
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'OPEN', ?, NULL, ?)
            """.trimIndent(),
            "other-bot",
            "prod",
            OffsetDateTime.parse("2026-05-06T09:00:00+09:00"),
            "Last-run failed"
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
            OffsetDateTime.parse("2026-05-06T08:10:05+09:00")
        )
        jdbcTemplate.update(
            """
            INSERT INTO alert_event(service_name, environment, alert_type, message, sent_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            "other-bot",
            "prod",
            "INCIDENT_OPENED",
            "Incident opened for other-bot (prod)",
            OffsetDateTime.parse("2026-05-06T09:00:05+09:00")
        )

        webTestClient.get()
            .uri("/api/monitored-services/$serviceId/detail?limit=3")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.service.serviceName").isEqualTo("dmib")
            .jsonPath("$.service.runStatus").isEqualTo("SENT")
            .jsonPath("$.incidents.length()").isEqualTo(1)
            .jsonPath("$.incidents[0].serviceName").isEqualTo("dmib")
            .jsonPath("$.alerts.length()").isEqualTo(1)
            .jsonPath("$.alerts[0].serviceName").isEqualTo("dmib")
            .jsonPath("$.alerts[0].alertType").isEqualTo("INCIDENT_RESOLVED")
    }
}
