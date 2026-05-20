package com.dbot.agentmonitor.monitoring

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

data class HistoryRetentionResult(
    val deletedServiceChecks: Int,
    val deletedAlertEvents: Int,
    val deletedResolvedIncidents: Int
)

interface HistoryRetentionPruner {
    fun prune(now: OffsetDateTime, retentionDays: Long = HistoryRetentionService.DEFAULT_RETENTION_DAYS): HistoryRetentionResult
}

@Service
class HistoryRetentionService(
    private val jdbcTemplate: JdbcTemplate
) : HistoryRetentionPruner {

    @Transactional
    override fun prune(now: OffsetDateTime, retentionDays: Long): HistoryRetentionResult {
        val cutoff = now.minusDays(retentionDays)

        val deletedServiceChecks = jdbcTemplate.update(
            "DELETE FROM service_check_history WHERE checked_at < ?",
            cutoff
        )
        val deletedAlertEvents = jdbcTemplate.update(
            "DELETE FROM alert_event WHERE sent_at < ?",
            cutoff
        )
        val deletedResolvedIncidents = jdbcTemplate.update(
            "DELETE FROM incident WHERE status = 'RESOLVED' AND resolved_at < ?",
            cutoff
        )

        return HistoryRetentionResult(
            deletedServiceChecks = deletedServiceChecks,
            deletedAlertEvents = deletedAlertEvents,
            deletedResolvedIncidents = deletedResolvedIncidents
        )
    }

    companion object {
        const val DEFAULT_RETENTION_DAYS = 30L
    }
}
