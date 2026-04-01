package com.dbot.agentmonitor.domain

import java.time.OffsetDateTime

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

data class LastRunSnapshot(
    val service: String?,
    val environment: String?,
    val timezone: String?,
    val lastRunDate: String?,
    val status: String?,
    val sentAt: OffsetDateTime?,
    val error: String?
)

data class ServicePollResult(
    val serviceName: String,
    val environment: String,
    val healthStatus: ServiceCheckStatus,
    val runStatus: String?,
    val lastRunDate: String?,
    val lastSuccessAt: OffsetDateTime?,
    val checkedAt: OffsetDateTime,
    val responseTimeMs: Long,
    val error: String?
)
