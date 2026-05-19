package com.dbot.agentmonitor.store

import com.dbot.agentmonitor.monitoring.HistoryRetentionResult
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.OffsetDateTime

data class RetentionRunRecord(
    val id: Long,
    val status: String,
    val retentionDays: Long,
    val deletedServiceChecks: Int,
    val deletedAlertEvents: Int,
    val deletedResolvedIncidents: Int,
    val startedAt: OffsetDateTime,
    val completedAt: OffsetDateTime?,
    val error: String?
)

@Component
class RetentionRunStore(
    private val jdbcTemplate: JdbcTemplate
) {

    fun recordSuccess(
        retentionDays: Long,
        startedAt: OffsetDateTime,
        completedAt: OffsetDateTime,
        result: HistoryRetentionResult
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO retention_run_history(
                status,
                retention_days,
                deleted_service_checks,
                deleted_alert_events,
                deleted_resolved_incidents,
                started_at,
                completed_at,
                error
            )
            VALUES ('SUCCESS', ?, ?, ?, ?, ?, ?, NULL)
            """.trimIndent(),
            retentionDays,
            result.deletedServiceChecks,
            result.deletedAlertEvents,
            result.deletedResolvedIncidents,
            startedAt,
            completedAt
        )
    }

    fun findLatest(): RetentionRunRecord? {
        return jdbcTemplate.query(
            """
            SELECT id,
                   status,
                   retention_days,
                   deleted_service_checks,
                   deleted_alert_events,
                   deleted_resolved_incidents,
                   started_at,
                   completed_at,
                   error
            FROM retention_run_history
            ORDER BY id DESC
            LIMIT 1
            """.trimIndent()
        ) { rs, _ -> rs.toRetentionRunRecord() }
            .firstOrNull()
    }

    private fun ResultSet.toRetentionRunRecord(): RetentionRunRecord {
        return RetentionRunRecord(
            id = getLong("id"),
            status = getString("status"),
            retentionDays = getLong("retention_days"),
            deletedServiceChecks = getInt("deleted_service_checks"),
            deletedAlertEvents = getInt("deleted_alert_events"),
            deletedResolvedIncidents = getInt("deleted_resolved_incidents"),
            startedAt = getObject("started_at", OffsetDateTime::class.java),
            completedAt = getObject("completed_at", OffsetDateTime::class.java),
            error = getString("error")
        )
    }
}
