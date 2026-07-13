package com.dbot.agentmonitor.polling

import com.dbot.agentmonitor.domain.MonitoredService
import com.dbot.agentmonitor.domain.ServicePollResult
import com.dbot.agentmonitor.incident.IncidentService
import com.dbot.agentmonitor.store.ServiceStatusStore
import org.springframework.stereotype.Service

@Service
class ServicePollingCommand(
    private val servicePollingService: ServicePollingService,
    private val serviceStatusStore: ServiceStatusStore,
    private val incidentService: IncidentService
) {
    fun pollAndRecord(service: MonitoredService): ServicePollResult {
        val result = servicePollingService.poll(service)
        serviceStatusStore.recordPollResult(result)
        incidentService.applyPollResult(service, result)
        return result
    }
}
