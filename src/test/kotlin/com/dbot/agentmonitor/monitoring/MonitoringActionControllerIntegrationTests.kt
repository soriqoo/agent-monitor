package com.dbot.agentmonitor.monitoring

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
class MonitoringActionControllerIntegrationTests {

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

        jdbcTemplate.update("DELETE FROM operator_action_event")
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
    fun createProbeAndListOperatorActions() {
        enqueueHealthyTarget()

        webTestClient.post()
            .uri("/api/monitored-services/probe")
            .bodyValue(probePayload())
            .exchange()
            .expectStatus().isOk

        webTestClient.post()
            .uri("/api/monitored-services")
            .bodyValue(createPayload(enabled = true))
            .exchange()
            .expectStatus().isCreated

        webTestClient.get()
            .uri("/api/monitoring/actions?limit=5")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].actionType").isEqualTo("SERVICE_CREATED")
            .jsonPath("$[0].targetServiceName").isEqualTo("dummy-monitored-service")
            .jsonPath("$[0].targetEnvironment").isEqualTo("demo")
            .jsonPath("$[0].status").isEqualTo("SUCCESS")
            .jsonPath("$[1].actionType").isEqualTo("CONNECTION_PROBE")
            .jsonPath("$[1].targetServiceName").isEqualTo("dummy-monitored-service")
            .jsonPath("$[1].status").isEqualTo("SUCCESS")

        assertThat(actionCount()).isEqualTo(2)
    }

    @Test
    fun updateCheckAndDeleteRecordOperatorActions() {
        val serviceId = insertMonitoredService(enabled = true)
        enqueueHealthyTarget()

        webTestClient.put()
            .uri("/api/monitored-services/{id}", serviceId)
            .bodyValue(createPayload(enabled = false))
            .exchange()
            .expectStatus().isOk

        webTestClient.post()
            .uri("/api/monitored-services/{id}/check", serviceId)
            .exchange()
            .expectStatus().isOk

        webTestClient.delete()
            .uri("/api/monitored-services/{id}", serviceId)
            .exchange()
            .expectStatus().isNoContent

        webTestClient.get()
            .uri("/api/monitoring/actions?limit=5")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].actionType").isEqualTo("SERVICE_DELETED")
            .jsonPath("$[1].actionType").isEqualTo("MANUAL_CHECK")
            .jsonPath("$[2].actionType").isEqualTo("SERVICE_DISABLED")

        assertThat(actionCount()).isEqualTo(3)
    }

    private fun enqueueHealthyTarget() {
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
                      "lastRunDate": "2026-06-05",
                      "status": "SENT",
                      "sentAt": "2026-06-05T08:00:03+09:00",
                      "error": null
                    }
                    """.trimIndent()
                )
        )
    }

    private fun probePayload(): Map<String, Any> {
        return mapOf(
            "serviceName" to "dummy-monitored-service",
            "baseUrl" to "http://127.0.0.1:${targetServer.port}",
            "environment" to "demo"
        )
    }

    private fun createPayload(enabled: Boolean): Map<String, Any> {
        return mapOf(
            "serviceName" to "dummy-monitored-service",
            "baseUrl" to "http://127.0.0.1:${targetServer.port}",
            "environment" to "demo",
            "enabled" to enabled
        )
    }

    private fun insertMonitoredService(enabled: Boolean): Long {
        jdbcTemplate.update(
            """
            INSERT INTO monitored_service(service_name, base_url, environment, enabled)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            "dummy-monitored-service",
            "http://127.0.0.1:${targetServer.port}",
            "demo",
            enabled
        )

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM monitored_service
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            Long::class.java,
            "dummy-monitored-service",
            "demo"
        )!!
    }

    private fun actionCount(): Long {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM operator_action_event",
            Long::class.java
        ) ?: 0L
    }
}
