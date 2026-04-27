package com.dbot.agentmonitor.store

import com.dbot.agentmonitor.domain.RecentIncident
import org.springframework.jdbc.core.JdbcTemplate
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
}
