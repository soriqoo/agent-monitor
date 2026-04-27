package com.dbot.agentmonitor.incident

import com.dbot.agentmonitor.alert.SlackAlertService
import com.dbot.agentmonitor.config.AppProperties
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
    companion object {
        private val EXECUTION_FAILURE_STATUSES = setOf("FAILED", "ERROR")
    }

    @Transactional
    fun applyPollResult(result: ServicePollResult) {
        val hasOpenIncident = incidentStore.hasOpenIncident(result.serviceName, result.environment)

        when {
            shouldOpenIncident(result) && !hasOpenIncident -> {
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

    private fun shouldOpenIncident(result: ServicePollResult): Boolean {
        if (isImmediateHealthFailure(result)) {
            return true
        }

        if (isExecutionFailure(result.runStatus)) {
            return true
        }

        return hasReachedObservationFailureThreshold(result)
    }

    private fun shouldCloseIncident(result: ServicePollResult): Boolean {
        return result.healthStatus == ServiceCheckStatus.UP &&
            result.error == null &&
            !isExecutionFailure(result.runStatus)
    }

    private fun isExecutionFailure(runStatus: String?): Boolean {
        return runStatus?.uppercase() in EXECUTION_FAILURE_STATUSES
    }

    private fun isImmediateHealthFailure(result: ServicePollResult): Boolean {
        if (result.healthStatus == ServiceCheckStatus.DOWN || result.healthStatus == ServiceCheckStatus.UNKNOWN) {
            return true
        }

        return result.error?.startsWith("Health ") == true
    }

    private fun hasReachedObservationFailureThreshold(result: ServicePollResult): Boolean {
        if (!isObservationFailure(result)) {
            return false
        }

        val threshold = appProperties.monitoring.observationFailureOpenThreshold
        val recentChecks = serviceStatusStore.findRecentChecks(result.serviceName, result.environment, threshold)
        if (recentChecks.size < threshold) {
            return false
        }

        return recentChecks.all { isObservationFailure(it.healthStatus, it.runStatus, it.error) }
    }

    private fun isObservationFailure(result: ServicePollResult): Boolean {
        return isObservationFailure(result.healthStatus.name, result.runStatus, result.error)
    }

    private fun isObservationFailure(healthStatus: String, runStatus: String?, error: String?): Boolean {
        return healthStatus == ServiceCheckStatus.DEGRADED.name &&
            runStatus == null &&
            error?.startsWith("Last-run request failed:") == true
    }
}
