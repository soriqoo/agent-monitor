package com.dbot.agentmonitor.polling

import com.dbot.agentmonitor.config.AppProperties
import com.dbot.agentmonitor.domain.MonitoredService
import com.dbot.agentmonitor.domain.ServiceCheckStatus
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.time.OffsetDateTime

class ServicePollingServiceTests {

    private lateinit var server: MockWebServer
    private lateinit var servicePollingService: ServicePollingService

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        servicePollingService = ServicePollingService(
            webClient = WebClient.builder().build(),
            appProperties = AppProperties()
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun pollReturnsUpWhenHealthAndLastRunSucceed() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"UP"}""")
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "service": "dmib",
                      "environment": "prod",
                      "timezone": "Asia/Seoul",
                      "lastRunDate": "2026-04-01",
                      "status": "SENT",
                      "sentAt": "2026-04-01T08:00:03+09:00",
                      "error": null
                    }
                    """.trimIndent()
                )
        )

        val result = servicePollingService.poll(monitoredService())

        assertThat(result.serviceName).isEqualTo("dmib")
        assertThat(result.environment).isEqualTo("prod")
        assertThat(result.healthStatus).isEqualTo(ServiceCheckStatus.UP)
        assertThat(result.runStatus).isEqualTo("SENT")
        assertThat(result.lastRunDate).isEqualTo("2026-04-01")
        assertThat(result.lastSuccessAt?.toInstant())
            .isEqualTo(OffsetDateTime.parse("2026-04-01T08:00:03+09:00").toInstant())
        assertThat(result.error).isNull()
        assertThat(server.takeRequest().path).isEqualTo("/actuator/health")
        assertThat(server.takeRequest().path).isEqualTo("/internal/monitoring/last-run")
    }

    @Test
    fun pollReturnsDegradedWhenHealthSucceedsButLastRunContractFails() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"UP"}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"boom"}""")
        )

        val result = servicePollingService.poll(monitoredService())

        assertThat(result.healthStatus).isEqualTo(ServiceCheckStatus.DEGRADED)
        assertThat(result.runStatus).isNull()
        assertThat(result.lastRunDate).isNull()
        assertThat(result.lastSuccessAt).isNull()
        assertThat(result.error).contains("Last-run request failed")
    }

    @Test
    fun pollReturnsDownWhenHealthRequestFails() {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"DOWN"}""")
        )

        val result = servicePollingService.poll(monitoredService())

        assertThat(result.healthStatus).isEqualTo(ServiceCheckStatus.DOWN)
        assertThat(result.runStatus).isNull()
        assertThat(result.lastRunDate).isNull()
        assertThat(result.lastSuccessAt).isNull()
        assertThat(result.error).contains("Health request failed")
    }

    private fun monitoredService(): MonitoredService {
        return MonitoredService(
            id = 1L,
            serviceName = "dmib",
            baseUrl = server.url("/").toString(),
            environment = "prod",
            enabled = true
        )
    }
}
