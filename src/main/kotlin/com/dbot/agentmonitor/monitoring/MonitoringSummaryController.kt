package com.dbot.agentmonitor.monitoring

import com.dbot.agentmonitor.store.MonitoredServiceStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneId

@RestController
class MonitoringSummaryController(
    private val monitoredServiceStore: MonitoredServiceStore,
    @Value("\${spring.application.name:agent-monitor}") private val serviceName: String,
    @Value("\${app.timezone}") private val appTimezone: String
) {
    @GetMapping("/internal/monitoring/summary")
    fun summary(): Map<String, Any> {
        val zone = ZoneId.of(appTimezone)
        return mapOf(
            "service" to serviceName,
            "timezone" to appTimezone,
            "registeredServices" to monitoredServiceStore.countRegisteredServices(),
            "enabledServices" to monitoredServiceStore.countEnabledServices(),
            "openIncidents" to monitoredServiceStore.countOpenIncidents(),
            "status" to "BOOTSTRAPPED",
            "checkedAt" to OffsetDateTime.now(zone).toString()
        )
    }
}
