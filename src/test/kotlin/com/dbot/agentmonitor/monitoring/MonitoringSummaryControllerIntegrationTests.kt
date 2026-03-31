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
    }
}
