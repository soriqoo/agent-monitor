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
    private val historyRetentionPruner: HistoryRetentionPruner,
    private val retentionRunStore: RetentionRunStore,
    private val appProperties: AppProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.monitoring.retention-cron}", zone = "\${app.timezone}")
    fun runRetention() {
        val zone = ZoneId.of(appProperties.timezone)
        val startedAt = OffsetDateTime.now(zone)
        val retentionDays = appProperties.monitoring.retentionDays

        try {
            val result = historyRetentionPruner.prune(
                now = startedAt,
                retentionDays = retentionDays
            )
            val completedAt = OffsetDateTime.now(zone)

            retentionRunStore.recordSuccess(
                retentionDays = retentionDays,
                startedAt = startedAt,
                completedAt = completedAt,
                result = result
            )

            log.info(
                "Pruned monitoring history. retentionDays={}, deletedServiceChecks={}, deletedAlertEvents={}, deletedResolvedIncidents={}",
                retentionDays,
                result.deletedServiceChecks,
                result.deletedAlertEvents,
                result.deletedResolvedIncidents
            )
        } catch (error: Exception) {
            val completedAt = OffsetDateTime.now(zone)
            val message = error.message ?: error.javaClass.simpleName

            retentionRunStore.recordFailure(
                retentionDays = retentionDays,
                startedAt = startedAt,
                completedAt = completedAt,
                error = message
            )

            log.error("Failed to prune monitoring history. retentionDays={}", retentionDays, error)
        }
    }
}
