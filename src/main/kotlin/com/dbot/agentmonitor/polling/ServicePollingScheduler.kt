package com.dbot.agentmonitor.polling

import com.dbot.agentmonitor.store.MonitoredServiceStore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ServicePollingScheduler(
    private val monitoredServiceStore: MonitoredServiceStore
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.monitoring.poll-cron}", zone = "\${app.timezone}")
    fun pollServices() {
        val services = monitoredServiceStore.findEnabledServices()
        log.info("Polling tick. enabledServices={}", services.size)

        // Initial scaffold only.
        // Actual HTTP polling, persistence, and incident handling will be implemented next.
    }
}
