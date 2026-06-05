package com.dbot.agentmonitor.monitoring

import com.dbot.agentmonitor.store.RetentionRunRecord
import com.dbot.agentmonitor.store.RetentionRunStore
import com.dbot.agentmonitor.store.OperatorActionStore
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/monitoring/retention")
class MonitoringRetentionController(
    private val historyRetentionScheduler: HistoryRetentionScheduler,
    private val retentionRunStore: RetentionRunStore,
    private val operatorActionStore: OperatorActionStore
) {

    @PostMapping("/run")
    fun runRetention(): RetentionRunRecord {
        historyRetentionScheduler.runRetention()

        val run = retentionRunStore.findLatest()
            ?: error("Retention cleanup did not produce a run record.")

        val deletedTotal = run.deletedServiceChecks +
            run.deletedAlertEvents +
            run.deletedResolvedIncidents

        operatorActionStore.record(
            actionType = "RETENTION_CLEANUP",
            targetServiceName = null,
            targetEnvironment = null,
            status = run.status,
            message = "Retention cleanup completed. deletedRows=$deletedTotal"
        )

        return run
    }
}
