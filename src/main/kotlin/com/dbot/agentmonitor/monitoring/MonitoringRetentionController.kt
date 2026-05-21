package com.dbot.agentmonitor.monitoring

import com.dbot.agentmonitor.store.RetentionRunRecord
import com.dbot.agentmonitor.store.RetentionRunStore
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/monitoring/retention")
class MonitoringRetentionController(
    private val historyRetentionScheduler: HistoryRetentionScheduler,
    private val retentionRunStore: RetentionRunStore
) {

    @PostMapping("/run")
    fun runRetention(): RetentionRunRecord {
        historyRetentionScheduler.runRetention()

        return retentionRunStore.findLatest()
            ?: error("Retention cleanup did not produce a run record.")
    }
}
