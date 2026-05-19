package com.dbot.agentmonitor.monitoring

import com.dbot.agentmonitor.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneId

@Component
class HistoryRetentionScheduler(
    private val historyRetentionService: HistoryRetentionService,
    private val appProperties: AppProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.monitoring.retention-cron}", zone = "\${app.timezone}")
    fun runRetention() {
        val result = historyRetentionService.prune(
            now = OffsetDateTime.now(ZoneId.of(appProperties.timezone)),
            retentionDays = appProperties.monitoring.retentionDays
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
