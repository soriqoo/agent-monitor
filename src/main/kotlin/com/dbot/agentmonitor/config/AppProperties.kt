package com.dbot.agentmonitor.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val timezone: String = "Asia/Seoul",
    val monitoring: Monitoring = Monitoring(),
    val slack: Slack = Slack(),
    val seed: Seed = Seed()
) {
    data class Monitoring(
        val pollCron: String = "0 */5 * * * *",
        val timeoutMs: Long = 3000,
        val connectTimeoutMs: Int = 3000
    )

    data class Slack(
        val enabled: Boolean = false,
        val webhookUrl: String = ""
    )

    data class Seed(
        val enabled: Boolean = true,
        val monitoredServices: List<MonitoredServiceSeed> = emptyList()
    )

    data class MonitoredServiceSeed(
        val serviceName: String,
        val baseUrl: String,
        val environment: String = "prod",
        val enabled: Boolean = true
    )
}
