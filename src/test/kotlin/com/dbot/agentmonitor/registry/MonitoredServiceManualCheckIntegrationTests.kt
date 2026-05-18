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
class MonitoredServiceManualCheckIntegrationTests {

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
    fun checkNowPollsTargetServiceAndReturnsUpdatedDetail() {
        val serviceId = insertMonitoredService(enabled = false)
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
                      "service": "dmib",
                      "environment": "prod",
                      "timezone": "Asia/Seoul",
                      "lastRunDate": "2026-05-18",
                      "status": "SENT",
                      "sentAt": "2026-05-18T08:00:03+09:00",
                      "error": null
                    }
                    """.trimIndent()
                )
        )

        webTestClient.post()
            .uri("/api/monitored-services/{id}/check", serviceId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.service.id").isEqualTo(serviceId.toInt())
            .jsonPath("$.service.enabled").isEqualTo(false)
            .jsonPath("$.service.healthStatus").isEqualTo("UP")
            .jsonPath("$.service.runStatus").isEqualTo("SENT")
            .jsonPath("$.service.lastRunDate").isEqualTo("2026-05-18")
            .jsonPath("$.service.openIncident").isEqualTo(false)
            .jsonPath("$.checks.length()").isEqualTo(1)
            .jsonPath("$.checks[0].healthStatus").isEqualTo("UP")
            .jsonPath("$.checks[0].runStatus").isEqualTo("SENT")

        webTestClient.get()
            .uri("/api/monitored-services/{id}/detail", serviceId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.checks.length()").isEqualTo(1)
            .jsonPath("$.checks[0].lastRunDate").isEqualTo("2026-05-18")

        assertThat(targetServer.takeRequest().path)
            .isEqualTo("/actuator/health")
        assertThat(targetServer.takeRequest().path)
            .isEqualTo("/internal/monitoring/last-run")
    }

    @Test
    fun checkNowReturnsNotFoundForUnknownService() {
        webTestClient.post()
            .uri("/api/monitored-services/{id}/check", 99999)
            .exchange()
            .expectStatus().isNotFound
    }

    private fun insertMonitoredService(enabled: Boolean): Long {
        jdbcTemplate.update(
            """
            INSERT INTO monitored_service(service_name, base_url, environment, enabled)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            "dmib",
            "http://127.0.0.1:${targetServer.port}",
            "prod",
            enabled
        )

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM monitored_service
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            Long::class.java,
            "dmib",
            "prod"
        )!!
    }
}
