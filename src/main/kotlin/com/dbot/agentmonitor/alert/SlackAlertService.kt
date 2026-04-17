package com.dbot.agentmonitor.alert

import com.dbot.agentmonitor.config.AppProperties
import com.dbot.agentmonitor.domain.ServicePollResult
import com.dbot.agentmonitor.store.AlertEventStore
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.time.OffsetDateTime

@Service
class SlackAlertService(
    private val webClientBuilder: WebClient.Builder,
    private val appProperties: AppProperties,
    private val alertEventStore: AlertEventStore
) {
    companion object {
        private const val INCIDENT_OPENED = "INCIDENT_OPENED"
        private const val INCIDENT_RESOLVED = "INCIDENT_RESOLVED"
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    fun notifyIncidentOpened(result: ServicePollResult) {
        sendAlert(
            result = result,
            alertType = INCIDENT_OPENED,
            message = buildOpenedMessage(result)
        )
    }

    fun notifyIncidentResolved(result: ServicePollResult) {
        sendAlert(
            result = result,
            alertType = INCIDENT_RESOLVED,
            message = buildResolvedMessage(result)
        )
    }

    private fun sendAlert(result: ServicePollResult, alertType: String, message: String) {
        if (!appProperties.slack.enabled || appProperties.slack.webhookUrl.isBlank()) {
            return
        }

        try {
            webClientBuilder
                .build()
                .post()
                .uri(appProperties.slack.webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(mapOf("text" to message))
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofMillis(appProperties.monitoring.timeoutMs))

            alertEventStore.recordAlertEvent(
                serviceName = result.serviceName,
                environment = result.environment,
                alertType = alertType,
                message = message,
                sentAt = OffsetDateTime.now()
            )
        } catch (ex: Exception) {
            logger.error(
                "Failed to send Slack alert. serviceName={}, environment={}, alertType={}",
                result.serviceName,
                result.environment,
                alertType,
                ex
            )
        }
    }

    private fun buildOpenedMessage(result: ServicePollResult): String {
        return buildString {
            append(":rotating_light: Incident opened for ")
            append(result.serviceName)
            append(" (")
            append(result.environment)
            appendLine(")")
            append("- healthStatus: ")
            append(result.healthStatus)
            appendLine()
            append("- runStatus: ")
            append(result.runStatus ?: "null")
            appendLine()
            append("- lastRunDate: ")
            append(result.lastRunDate ?: "null")
            appendLine()
            append("- checkedAt: ")
            append(result.checkedAt)
            appendLine()
            append("- error: ")
            append(result.error ?: "null")
        }
    }

    private fun buildResolvedMessage(result: ServicePollResult): String {
        return buildString {
            append(":white_check_mark: Incident resolved for ")
            append(result.serviceName)
            append(" (")
            append(result.environment)
            appendLine(")")
            append("- healthStatus: ")
            append(result.healthStatus)
            appendLine()
            append("- runStatus: ")
            append(result.runStatus ?: "null")
            appendLine()
            append("- lastRunDate: ")
            append(result.lastRunDate ?: "null")
            appendLine()
            append("- checkedAt: ")
            append(result.checkedAt)
        }
    }
}
