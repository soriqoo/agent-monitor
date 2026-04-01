package com.dbot.agentmonitor.store

import com.dbot.agentmonitor.domain.MonitoredService
import org.springframework.jdbc.core.JdbcTemplate

class MonitoredServiceStore(
    private val jdbcTemplate: JdbcTemplate
) {
    fun upsert(
        serviceName: String,
        baseUrl: String,
        environment: String,
        enabled: Boolean
    ) {
        val updated = jdbcTemplate.update(
            """
            UPDATE monitored_service
            SET base_url = ?, enabled = ?, updated_at = CURRENT_TIMESTAMP
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            baseUrl,
            enabled,
            serviceName,
            environment
        )

        if (updated == 0) {
            jdbcTemplate.update(
                """
                INSERT INTO monitored_service(service_name, base_url, environment, enabled)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
                serviceName,
                baseUrl,
                environment,
                enabled
            )
        }
    }

    fun findEnabledServices(): List<MonitoredService> {
        return jdbcTemplate.query(
            """
            SELECT id, service_name, base_url, environment, enabled
            FROM monitored_service
            WHERE enabled = TRUE
            ORDER BY id ASC
            """.trimIndent()
        ) { rs, _ ->
            MonitoredService(
                id = rs.getLong("id"),
                serviceName = rs.getString("service_name"),
                baseUrl = rs.getString("base_url"),
                environment = rs.getString("environment"),
                enabled = rs.getBoolean("enabled")
            )
        }
    }

    fun countRegisteredServices(): Long {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM monitored_service",
            Long::class.java
        ) ?: 0L
    }

    fun countEnabledServices(): Long {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM monitored_service WHERE enabled = TRUE",
            Long::class.java
        ) ?: 0L
    }

    fun countOpenIncidents(): Long {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM incident WHERE status = 'OPEN'",
            Long::class.java
        ) ?: 0L
    }
}
