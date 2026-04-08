package com.dbot.agentmonitor.polling

import com.dbot.agentmonitor.store.MonitoredServiceStore
import com.dbot.agentmonitor.store.ServiceStatusStore
import com.dbot.agentmonitor.incident.IncidentService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ServicePollingScheduler(
    private val monitoredServiceStore: MonitoredServiceStore,
    private val servicePollingService: ServicePollingService,
    private val serviceStatusStore: ServiceStatusStore,
    private val incidentService: IncidentService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.monitoring.poll-cron}", zone = "\${app.timezone}")
    fun pollServices() {
        val services = monitoredServiceStore.findEnabledServices()
        log.info("Polling tick. enabledServices={}", services.size)

        services.forEach { service ->
            val result = servicePollingService.poll(service)
            serviceStatusStore.recordPollResult(result)
            incidentService.applyPollResult(result)
            val logMessage =
                "Polled service. serviceName={}, environment={}, healthStatus={}, runStatus={}, lastRunDate={}, responseTimeMs={}, error={}"

            if (result.healthStatus == com.dbot.agentmonitor.domain.ServiceCheckStatus.UP && result.error == null) {
                log.info(
                    logMessage,
                    result.serviceName,
                    result.environment,
                    result.healthStatus,
                    result.runStatus,
                    result.lastRunDate,
                    result.responseTimeMs,
                    result.error
                )
            } else {
                log.warn(
                    logMessage,
                    result.serviceName,
                    result.environment,
                    result.healthStatus,
                    result.runStatus,
                    result.lastRunDate,
                    result.responseTimeMs,
                    result.error
                )
            }
        }
    }
}
