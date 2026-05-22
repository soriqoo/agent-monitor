package com.dbot.agentmonitor.registry

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
class MonitoredServiceProbeIntegrationTests {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var targetServer: MockWebServer
    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp() {
        targetServer = MockWebServer()
        targetServer.start()

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

    @AfterEach
    fun tearDown() {
        targetServer.shutdown()
    }

    @Test
    fun probePollsTargetWithoutPersistingServiceOrHistory() {
        targetServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"UP"}""")
        )
        targetServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "service": "dummy-monitored-service",
                      "environment": "demo",
                      "timezone": "Asia/Seoul",
                      "lastRunDate": "2026-05-22",
                      "status": "SENT",
                      "sentAt": "2026-05-22T08:00:03+09:00",
                      "error": null
                    }
                    """.trimIndent()
                )
        )

        webTestClient.post()
            .uri("/api/monitored-services/probe")
            .bodyValue(
                mapOf(
                    "serviceName" to "dummy-monitored-service",
                    "baseUrl" to "http://127.0.0.1:${targetServer.port}",
                    "environment" to "demo"
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.serviceName").isEqualTo("dummy-monitored-service")
            .jsonPath("$.environment").isEqualTo("demo")
            .jsonPath("$.healthStatus").isEqualTo("UP")
            .jsonPath("$.runStatus").isEqualTo("SENT")
            .jsonPath("$.lastRunDate").isEqualTo("2026-05-22")
            .jsonPath("$.responseTimeMs").isNumber
            .jsonPath("$.error").doesNotExist()

        assertThat(targetServer.takeRequest().path).isEqualTo("/actuator/health")
        assertThat(targetServer.takeRequest().path).isEqualTo("/internal/monitoring/last-run")
        assertThat(rowCount("monitored_service")).isZero()
        assertThat(rowCount("service_check_history")).isZero()
        assertThat(rowCount("incident")).isZero()
    }

    @Test
    fun probeReturnsDownResultWithoutPersistingWhenHealthRequestFails() {
        targetServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"DOWN"}""")
        )

        webTestClient.post()
            .uri("/api/monitored-services/probe")
            .bodyValue(
                mapOf(
                    "serviceName" to "dummy-monitored-service",
                    "baseUrl" to "http://127.0.0.1:${targetServer.port}",
                    "environment" to "demo"
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.healthStatus").isEqualTo("DOWN")
            .jsonPath("$.runStatus").doesNotExist()
            .jsonPath("$.lastRunDate").doesNotExist()
            .jsonPath("$.error").exists()

        assertThat(targetServer.takeRequest().path).isEqualTo("/actuator/health")
        assertThat(rowCount("monitored_service")).isZero()
        assertThat(rowCount("service_check_history")).isZero()
        assertThat(rowCount("incident")).isZero()
    }

    private fun rowCount(tableName: String): Long {
        return jdbcTemplate.queryForObject("SELECT COUNT(1) FROM $tableName", Long::class.java) ?: 0L
    }
}
