package com.dbot.agentmonitor.incident

import com.dbot.agentmonitor.domain.ServiceCheckStatus
import com.dbot.agentmonitor.domain.ServicePollResult
import com.dbot.agentmonitor.store.IncidentStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class IncidentService(
    private val incidentStore: IncidentStore
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
            }

            shouldCloseIncident(result) && hasOpenIncident -> {
                incidentStore.resolveOpenIncident(
                    serviceName = result.serviceName,
                    environment = result.environment,
                    resolvedAt = result.checkedAt,
                    lastError = null
                )
            }
        }
    }

    private fun shouldOpenIncident(result: ServicePollResult): Boolean {
        if (result.healthStatus != ServiceCheckStatus.UP) {
            return true
        }

        return isExecutionFailure(result.runStatus)
    }

    private fun shouldCloseIncident(result: ServicePollResult): Boolean {
        return result.healthStatus == ServiceCheckStatus.UP &&
            result.error == null &&
            !isExecutionFailure(result.runStatus)
    }

    private fun isExecutionFailure(runStatus: String?): Boolean {
        return runStatus?.uppercase() in EXECUTION_FAILURE_STATUSES
    }
}
