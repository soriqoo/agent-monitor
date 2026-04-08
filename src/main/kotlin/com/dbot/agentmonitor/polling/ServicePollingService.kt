package com.dbot.agentmonitor.polling

import com.dbot.agentmonitor.config.AppProperties
import com.dbot.agentmonitor.domain.LastRunSnapshot
import com.dbot.agentmonitor.domain.MonitoredService
import com.dbot.agentmonitor.domain.ServiceCheckStatus
import com.dbot.agentmonitor.domain.ServicePollResult
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId

@Component
class ServicePollingService(
    private val webClient: WebClient,
    private val appProperties: AppProperties
) {
    fun poll(service: MonitoredService): ServicePollResult {
        val checkedAt = OffsetDateTime.now(ZoneId.of(appProperties.timezone))
        val startedAt = System.nanoTime()

        val healthResponse = runCatching { fetchHealth(service) }
        if (healthResponse.isFailure) {
            return ServicePollResult(
                serviceName = service.serviceName,
                environment = service.environment,
                healthStatus = ServiceCheckStatus.DOWN,
                runStatus = null,
                lastRunDate = null,
                lastSuccessAt = null,
                checkedAt = checkedAt,
                responseTimeMs = elapsedMillis(startedAt),
                error = "Health request failed: ${healthResponse.exceptionOrNull()?.messageForLog()}"
            )
        }

        val lastRunResponse = runCatching { fetchLastRun(service) }
        if (lastRunResponse.isFailure) {
            return ServicePollResult(
                serviceName = service.serviceName,
                environment = service.environment,
                healthStatus = ServiceCheckStatus.DEGRADED,
                runStatus = null,
                lastRunDate = null,
                lastSuccessAt = null,
                checkedAt = checkedAt,
                responseTimeMs = elapsedMillis(startedAt),
                error = "Last-run request failed: ${lastRunResponse.exceptionOrNull()?.messageForLog()}"
            )
        }

        val health = healthResponse.getOrThrow()
        val lastRun = lastRunResponse.getOrThrow()
        val mappedHealthStatus = mapHealthStatus(health.status)
        val contractError = when {
            mappedHealthStatus != ServiceCheckStatus.UP ->
                "Health endpoint reported status=${health.status ?: "UNKNOWN"}"

            !lastRun.error.isNullOrBlank() ->
                lastRun.error

            else -> null
        }

        return ServicePollResult(
            serviceName = service.serviceName,
            environment = service.environment,
            healthStatus = mappedHealthStatus,
            runStatus = lastRun.status,
            lastRunDate = lastRun.lastRunDate,
            lastSuccessAt = lastRun.sentAt,
            checkedAt = checkedAt,
            responseTimeMs = elapsedMillis(startedAt),
            error = contractError
        )
    }

    private fun fetchHealth(service: MonitoredService): ActuatorHealthResponse {
        return webClient.get()
            .uri("${service.baseUrl.trimEnd('/')}/actuator/health")
            .retrieve()
            .bodyToMono(ActuatorHealthResponse::class.java)
            .block(Duration.ofMillis(appProperties.monitoring.timeoutMs))
            ?: ActuatorHealthResponse(status = null)
    }

    private fun fetchLastRun(service: MonitoredService): LastRunResponse {
        return webClient.get()
            .uri("${service.baseUrl.trimEnd('/')}/internal/monitoring/last-run")
            .retrieve()
            .bodyToMono(LastRunResponse::class.java)
            .block(Duration.ofMillis(appProperties.monitoring.timeoutMs))
            ?: LastRunResponse()
    }

    private fun mapHealthStatus(status: String?): ServiceCheckStatus {
        return when (status?.uppercase()) {
            "UP" -> ServiceCheckStatus.UP
            null -> ServiceCheckStatus.UNKNOWN
            else -> ServiceCheckStatus.DEGRADED
        }
    }

    private fun elapsedMillis(startedAt: Long): Long {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
    }

    private fun Throwable.messageForLog(): String {
        return message?.lineSequence()?.firstOrNull()?.take(200) ?: javaClass.simpleName
    }
}

data class ActuatorHealthResponse(
    val status: String?
)

data class LastRunResponse(
    val service: String? = null,
    val environment: String? = null,
    val timezone: String? = null,
    val lastRunDate: String? = null,
    val status: String? = null,
    val sentAt: OffsetDateTime? = null,
    val error: String? = null
) {
    fun toSnapshot(): LastRunSnapshot {
        return LastRunSnapshot(
            service = service,
            environment = environment,
            timezone = timezone,
            lastRunDate = lastRunDate,
            status = status,
            sentAt = sentAt,
            error = error
        )
    }
}
