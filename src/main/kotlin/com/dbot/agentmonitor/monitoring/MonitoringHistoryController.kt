package com.dbot.agentmonitor.monitoring

import com.dbot.agentmonitor.domain.MonitoringHistorySnapshot
import com.dbot.agentmonitor.domain.RecentServiceCheckEvent
import com.dbot.agentmonitor.store.AlertEventStore
import com.dbot.agentmonitor.store.IncidentStore
import com.dbot.agentmonitor.store.ServiceStatusStore
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/monitoring")
class MonitoringHistoryController(
    private val incidentStore: IncidentStore,
    private val alertEventStore: AlertEventStore,
    private val serviceStatusStore: ServiceStatusStore
) {
    @GetMapping("/history")
    fun history(
        @RequestParam(defaultValue = "6") limit: Int
    ): MonitoringHistorySnapshot {
        val boundedLimit = limit.coerceIn(1, 20)
        return MonitoringHistorySnapshot(
            incidents = incidentStore.findRecent(boundedLimit),
            alerts = alertEventStore.findRecent(boundedLimit)
        )
    }

    @GetMapping("/checks")
    fun checks(
        @RequestParam(defaultValue = "10") limit: Int
    ): List<RecentServiceCheckEvent> {
        val boundedLimit = limit.coerceIn(1, 10)
        return serviceStatusStore.findRecentCheckEvents(boundedLimit)
    }
}
