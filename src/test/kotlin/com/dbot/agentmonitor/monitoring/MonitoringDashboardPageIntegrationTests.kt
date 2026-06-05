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
                require(body.contains("Recent Activity")) {
                    "Dashboard page should contain the recent activity section."
                }
                require(body.contains("Recent Incidents")) {
                    "Dashboard page should contain the recent incidents heading."
                }
                require(body.contains("Recent Checks")) {
                    "Dashboard page should contain the recent service checks heading."
                }
                require(body.contains("Recent Actions")) {
                    "Dashboard page should contain the operator action history heading."
                }
                require(body.contains("Service Detail")) {
                    "Dashboard page should contain the service detail panel."
                }
            }
    }

    @Test
    fun dashboardScriptIncludesCheckNowFeedbackState() {
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .build()
            .get()
            .uri("/app.js")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith("application/javascript")
            .expectBody(String::class.java)
            .value { body ->
                require(body.contains("Checking...")) {
                    "Dashboard script should show a visible in-progress state for manual checks."
                }
                require(body.contains("Check failed")) {
                    "Dashboard script should clearly label manual check failures."
                }
                require(body.contains("/api/monitoring/checks?limit=4")) {
                    "Dashboard script should load recent service checks."
                }
                require(body.contains("/api/monitoring/actions?limit=6")) {
                    "Dashboard script should load recent operator actions."
                }
                require(body.contains("renderCheckHistory")) {
                    "Dashboard script should render recent service checks."
                }
                require(body.contains("renderActionHistory")) {
                    "Dashboard script should render recent operator actions."
                }
                require(body.contains("renderRetentionSummary")) {
                    "Dashboard script should render retention cleanup visibility."
                }
                require(body.contains("/api/monitoring/retention/run")) {
                    "Dashboard script should call the manual retention cleanup endpoint."
                }
                require(body.contains("Run cleanup")) {
                    "Dashboard script should expose a manual retention cleanup action."
                }
                require(body.contains("/api/monitored-services/probe")) {
                    "Dashboard script should call the monitored service probe endpoint."
                }
                require(body.contains("Test connection")) {
                    "Dashboard script should expose a test connection action."
                }
            }
    }
}
