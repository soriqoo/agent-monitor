package com.dbot.agentmonitor.registry

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
class MonitoredServiceControllerIntegrationTests {

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
    fun listReturnsRegisteredMonitoredServices() {
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

        webTestClient.get()
            .uri("/api/monitored-services")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].serviceName").isEqualTo("dmib")
            .jsonPath("$[0].baseUrl").isEqualTo("http://dmib:8080")
            .jsonPath("$[0].environment").isEqualTo("prod")
            .jsonPath("$[0].enabled").isEqualTo(true)
    }

    @Test
    fun createRegistersNewMonitoredService() {
        webTestClient.post()
            .uri("/api/monitored-services")
            .bodyValue(
                mapOf(
                    "serviceName" to "daily-english-bot",
                    "baseUrl" to "http://daily-english-bot:8080",
                    "environment" to "prod",
                    "enabled" to true
                )
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isNumber
            .jsonPath("$.serviceName").isEqualTo("daily-english-bot")
            .jsonPath("$.baseUrl").isEqualTo("http://daily-english-bot:8080")
            .jsonPath("$.environment").isEqualTo("prod")
            .jsonPath("$.enabled").isEqualTo(true)
    }

    @Test
    fun updateChangesExistingMonitoredService() {
        val id = insertMonitoredService(
            serviceName = "dmib",
            baseUrl = "http://dmib:8080",
            environment = "prod",
            enabled = true
        )

        webTestClient.put()
            .uri("/api/monitored-services/{id}", id)
            .bodyValue(
                mapOf(
                    "serviceName" to "dmib",
                    "baseUrl" to "http://dmib-v2:8080",
                    "environment" to "prod",
                    "enabled" to false
                )
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(id.toInt())
            .jsonPath("$.baseUrl").isEqualTo("http://dmib-v2:8080")
            .jsonPath("$.enabled").isEqualTo(false)
    }

    @Test
    fun deleteRemovesMonitoredService() {
        val id = insertMonitoredService(
            serviceName = "dmib",
            baseUrl = "http://dmib:8080",
            environment = "prod",
            enabled = true
        )

        webTestClient.delete()
            .uri("/api/monitored-services/{id}", id)
            .exchange()
            .expectStatus().isNoContent

        webTestClient.get()
            .uri("/api/monitored-services")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .json("[]")
    }

    @Test
    fun createReturnsConflictForDuplicateServiceNameAndEnvironment() {
        insertMonitoredService(
            serviceName = "dmib",
            baseUrl = "http://dmib:8080",
            environment = "prod",
            enabled = true
        )

        webTestClient.post()
            .uri("/api/monitored-services")
            .bodyValue(
                mapOf(
                    "serviceName" to "dmib",
                    "baseUrl" to "http://dmib-alt:8080",
                    "environment" to "prod",
                    "enabled" to true
                )
            )
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    private fun insertMonitoredService(
        serviceName: String,
        baseUrl: String,
        environment: String,
        enabled: Boolean
    ): Long {
        jdbcTemplate.update(
            """
            INSERT INTO monitored_service(service_name, base_url, environment, enabled)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            serviceName,
            baseUrl,
            environment,
            enabled
        )

        return jdbcTemplate.queryForObject(
            """
            SELECT id
            FROM monitored_service
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            Long::class.java,
            serviceName,
            environment
        )!!
    }
}
