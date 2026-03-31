package com.dbot.agentmonitor.domain

enum class ServiceCheckStatus {
    UP,
    DEGRADED,
    DOWN,
    UNKNOWN
}

data class MonitoredService(
    val id: Long,
    val serviceName: String,
    val baseUrl: String,
    val environment: String,
    val enabled: Boolean
)
