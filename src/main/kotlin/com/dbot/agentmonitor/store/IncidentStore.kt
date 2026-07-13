package com.dbot.agentmonitor.store

import com.dbot.agentmonitor.domain.OpenIncidentReminderCandidate
import com.dbot.agentmonitor.domain.RecentIncident
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class IncidentStore(
    private val jdbcTemplate: JdbcTemplate
) {
    fun hasOpenIncident(serviceName: String, environment: String): Boolean {
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM incident
            WHERE service_name = ? AND environment = ? AND status = 'OPEN'
            """.trimIndent(),
            Long::class.java,
            serviceName,
            environment
        ) ?: 0L

        return count > 0
    }

    fun openIncident(
        serviceName: String,
        environment: String,
        openedAt: OffsetDateTime,
        lastError: String?
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO incident(service_name, environment, status, opened_at, resolved_at, last_error)
            VALUES (?, ?, 'OPEN', ?, NULL, ?)
            """.trimIndent(),
            serviceName,
            environment,
            openedAt,
            lastError
        )
    }

    fun resolveOpenIncident(
        serviceName: String,
        environment: String,
        resolvedAt: OffsetDateTime,
        lastError: String?
    ) {
        jdbcTemplate.update(
            """
            UPDATE incident
            SET status = 'RESOLVED',
                resolved_at = ?,
                last_error = ?
            WHERE service_name = ? AND environment = ? AND status = 'OPEN'
            """.trimIndent(),
            resolvedAt,
            lastError,
            serviceName,
            environment
        )
    }

    fun findOpenIncidentsDueForReminder(cutoff: OffsetDateTime): List<OpenIncidentReminderCandidate> {
        return jdbcTemplate.query(
            """
            SELECT $REMINDER_CANDIDATE_COLUMNS
            FROM incident i
            WHERE $REMINDER_DUE_PREDICATE
            ORDER BY $LATEST_REMINDER_REFERENCE_AT ASC,
                     i.id ASC
            """.trimIndent(),
            reminderCandidateRowMapper,
            cutoff
        )
    }

    fun lockIncident(incidentId: Long): Boolean {
        return jdbcTemplate.query(
            "SELECT id FROM incident WHERE id = ? FOR UPDATE",
            { rs, _ -> rs.getLong("id") },
            incidentId
        ).isNotEmpty()
    }

    fun findOpenIncidentDueForReminder(
        incidentId: Long,
        cutoff: OffsetDateTime
    ): OpenIncidentReminderCandidate? {
        return jdbcTemplate.query(
            """
            SELECT $REMINDER_CANDIDATE_COLUMNS
            FROM incident i
            WHERE i.id = ?
              AND $REMINDER_DUE_PREDICATE
            """.trimIndent(),
            reminderCandidateRowMapper,
            incidentId,
            cutoff
        ).singleOrNull()
    }

    fun findRecent(limit: Int): List<RecentIncident> {
        return jdbcTemplate.query(
            """
            SELECT id,
                   service_name,
                   environment,
                   status,
                   opened_at,
                   resolved_at,
                   last_error
            FROM incident
            ORDER BY COALESCE(resolved_at, opened_at) DESC, id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                RecentIncident(
                    id = rs.getLong("id"),
                    serviceName = rs.getString("service_name"),
                    environment = rs.getString("environment"),
                    status = rs.getString("status"),
                    openedAt = rs.getObject("opened_at", OffsetDateTime::class.java),
                    resolvedAt = rs.getObject("resolved_at", OffsetDateTime::class.java),
                    lastError = rs.getString("last_error")
                )
            },
            limit
        )
    }

    fun findRecentForService(serviceName: String, environment: String, limit: Int): List<RecentIncident> {
        return jdbcTemplate.query(
            """
            SELECT id,
                   service_name,
                   environment,
                   status,
                   opened_at,
                   resolved_at,
                   last_error
            FROM incident
            WHERE service_name = ? AND environment = ?
            ORDER BY COALESCE(resolved_at, opened_at) DESC, id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                RecentIncident(
                    id = rs.getLong("id"),
                    serviceName = rs.getString("service_name"),
                    environment = rs.getString("environment"),
                    status = rs.getString("status"),
                    openedAt = rs.getObject("opened_at", OffsetDateTime::class.java),
                    resolvedAt = rs.getObject("resolved_at", OffsetDateTime::class.java),
                    lastError = rs.getString("last_error")
                )
            },
            serviceName,
            environment,
            limit
        )
    }

    private val reminderCandidateRowMapper = RowMapper { rs, _ ->
        OpenIncidentReminderCandidate(
            id = rs.getLong("id"),
            serviceName = rs.getString("service_name"),
            environment = rs.getString("environment"),
            openedAt = rs.getObject("opened_at", OffsetDateTime::class.java),
            lastError = rs.getString("last_error")
        )
    }

    private companion object {
        const val REMINDER_CANDIDATE_COLUMNS = """
            i.id,
            i.service_name,
            i.environment,
            i.opened_at,
            i.last_error
        """

        const val LATEST_REMINDER_REFERENCE_AT = """
            COALESCE(
                (
                    SELECT MAX(a.sent_at)
                    FROM alert_event a
                    WHERE a.service_name = i.service_name
                      AND a.environment = i.environment
                      AND a.alert_type IN ('INCIDENT_OPENED', 'INCIDENT_REMINDER')
                      AND a.sent_at >= i.opened_at
                ),
                i.opened_at
            )
        """

        const val REMINDER_DUE_PREDICATE = """
            i.status = 'OPEN'
            AND $LATEST_REMINDER_REFERENCE_AT < ?
        """
    }
}
