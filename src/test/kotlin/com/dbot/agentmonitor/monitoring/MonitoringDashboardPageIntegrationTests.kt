package com.dbot.agentmonitor.monitoring

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MonitoringDashboardPageIntegrationTests {

    @LocalServerPort
    var port: Int = 0

    @Test
    fun rootServesMonitoringDashboardPage() {
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
            .get()
            .uri("/")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith("text/html")
            .expectBody(String::class.java)
            .value { body ->
                require(body.contains("Agent Monitor")) {
                    "Dashboard page should contain the Agent Monitor title."
                }
            }
    }
}
