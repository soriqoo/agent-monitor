package com.dbot.agentmonitor.incident

import com.dbot.agentmonitor.alert.SlackAlertService
import com.dbot.agentmonitor.config.AppProperties
import com.dbot.agentmonitor.domain.MonitoredService
import com.dbot.agentmonitor.domain.PollFailureType
import com.dbot.agentmonitor.domain.ServiceCheckStatus
import com.dbot.agentmonitor.domain.ServicePollResult
import com.dbot.agentmonitor.store.IncidentStore
import com.dbot.agentmonitor.store.ServiceStatusStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IncidentService(
    private val incidentStore: IncidentStore,
    private val serviceStatusStore: ServiceStatusStore,
    private val appProperties: AppProperties,
    private val slackAlertService: SlackAlertService
) {
    @Transactional
    fun applyPollResult(result: ServicePollResult) {
        applyPollResult(result, appProperties.monitoring.observationFailureOpenThreshold)
    }

    @Transactional
    fun applyPollResult(service: MonitoredService, result: ServicePollResult) {
        val threshold = service.observationFailureOpenThreshold
            ?: appProperties.monitoring.observationFailureOpenThreshold
        applyPollResult(result, threshold)
    }

    private fun applyPollResult(result: ServicePollResult, observationFailureThreshold: Int) {
        val hasOpenIncident = incidentStore.hasOpenIncident(result.serviceName, result.environment)

        when {
            shouldOpenIncident(result, observationFailureThreshold) && !hasOpenIncident -> {
                incidentStore.openIncident(
                    serviceName = result.serviceName,
                    environment = result.environment,
                    openedAt = result.checkedAt,
                    lastError = result.error ?: result.runStatus
                )
                slackAlertService.notifyIncidentOpened(result)
            }

            shouldCloseIncident(result) && hasOpenIncident -> {
                incidentStore.resolveOpenIncident(
                    serviceName = result.serviceName,
                    environment = result.environment,
                    resolvedAt = result.checkedAt,
                    lastError = null
                )
                slackAlertService.notifyIncidentResolved(result)
            }
        }
    }

    private fun shouldOpenIncident(result: ServicePollResult, observationFailureThreshold: Int): Boolean {
        return when (result.failureType) {
            PollFailureType.HEALTH_FAILURE,
            PollFailureType.EXECUTION_FAILURE -> true

            PollFailureType.OBSERVATION_FAILURE ->
                hasReachedObservationFailureThreshold(result, observationFailureThreshold)

            PollFailureType.NONE -> false
        }
    }

    private fun shouldCloseIncident(result: ServicePollResult): Boolean {
        return result.healthStatus == ServiceCheckStatus.UP &&
            result.error == null &&
            result.failureType == PollFailureType.NONE
    }

    private fun hasReachedObservationFailureThreshold(
        result: ServicePollResult,
        threshold: Int
    ): Boolean {
        if (result.failureType != PollFailureType.OBSERVATION_FAILURE) {
            return false
        }

        val recentChecks = serviceStatusStore.findRecentChecks(result.serviceName, result.environment, threshold)
        if (recentChecks.size < threshold) {
            return false
        }

        return recentChecks.all { it.failureType == PollFailureType.OBSERVATION_FAILURE }
    }
}
