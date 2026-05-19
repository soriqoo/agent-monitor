package com.dbot.agentmonitor.monitoring

import com.dbot.agentmonitor.config.AppProperties
import com.dbot.agentmonitor.store.RetentionRunStore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneId

@Component
class HistoryRetentionScheduler(
    private val historyRetentionService: HistoryRetentionService,
    private val retentionRunStore: RetentionRunStore,
    private val appProperties: AppProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.monitoring.retention-cron}", zone = "\${app.timezone}")
    fun runRetention() {
        val zone = ZoneId.of(appProperties.timezone)
        val startedAt = OffsetDateTime.now(zone)
        val result = historyRetentionService.prune(
            now = startedAt,
            retentionDays = appProperties.monitoring.retentionDays
        )
        val completedAt = OffsetDateTime.now(zone)

        retentionRunStore.recordSuccess(
            retentionDays = appProperties.monitoring.retentionDays,
            startedAt = startedAt,
            completedAt = completedAt,
            result = result
        )

        log.info(
            "Pruned monitoring history. retentionDays={}, deletedServiceChecks={}, deletedAlertEvents={}, deletedResolvedIncidents={}",
            appProperties.monitoring.retentionDays,
            result.deletedServiceChecks,
            result.deletedAlertEvents,
            result.deletedResolvedIncidents
        )
    }
}
