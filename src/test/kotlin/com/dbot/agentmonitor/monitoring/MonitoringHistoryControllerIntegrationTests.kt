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
}
