package com.dbot.agentmonitor.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val timezone: String = "Asia/Seoul",
    val monitoring: Monitoring = Monitoring(),
    val slack: Slack = Slack()
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
}
