package com.dbot.agentmonitor.store

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
}
