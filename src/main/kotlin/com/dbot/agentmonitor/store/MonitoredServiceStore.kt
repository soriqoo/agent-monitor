package com.dbot.agentmonitor.store

import com.dbot.agentmonitor.domain.MonitoredService
import com.dbot.agentmonitor.domain.MonitoredServiceOverview
import org.springframework.jdbc.core.JdbcTemplate

class MonitoredServiceStore(
    private val jdbcTemplate: JdbcTemplate
) {
    fun create(
        serviceName: String,
        baseUrl: String,
        environment: String,
        enabled: Boolean
    ): MonitoredService {
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

        return findByServiceNameAndEnvironment(serviceName, environment)
            ?: error(
                "Created monitored_service could not be reloaded. " +
                    "serviceName=$serviceName, environment=$environment"
            )
    }

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

    fun findAll(): List<MonitoredService> {
        return jdbcTemplate.query(
            """
            SELECT id, service_name, base_url, environment, enabled
            FROM monitored_service
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

    fun findAllOverviews(): List<MonitoredServiceOverview> {
        return jdbcTemplate.query(
            """
            SELECT ms.id,
                   ms.service_name,
                   ms.base_url,
                   ms.environment,
                   ms.enabled,
                   scs.health_status,
                   scs.run_status,
                   scs.last_run_date,
                   scs.last_checked_at,
                   scs.error,
                   CASE
                     WHEN EXISTS (
                       SELECT 1
                       FROM incident i
                       WHERE i.service_name = ms.service_name
                         AND i.environment = ms.environment
                         AND i.status = 'OPEN'
                     ) THEN TRUE
                     ELSE FALSE
                   END AS open_incident
            FROM monitored_service ms
            LEFT JOIN service_current_status scs
              ON scs.service_name = ms.service_name
             AND scs.environment = ms.environment
            ORDER BY ms.id ASC
            """.trimIndent()
        ) { rs, _ ->
            MonitoredServiceOverview(
                id = rs.getLong("id"),
                serviceName = rs.getString("service_name"),
                baseUrl = rs.getString("base_url"),
                environment = rs.getString("environment"),
                enabled = rs.getBoolean("enabled"),
                healthStatus = rs.getString("health_status"),
                runStatus = rs.getString("run_status"),
                lastRunDate = rs.getString("last_run_date"),
                lastCheckedAt = rs.getObject("last_checked_at", java.time.OffsetDateTime::class.java),
                error = rs.getString("error"),
                openIncident = rs.getBoolean("open_incident")
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

    fun findById(id: Long): MonitoredService? {
        return jdbcTemplate.query(
            """
            SELECT id, service_name, base_url, environment, enabled
            FROM monitored_service
            WHERE id = ?
            """.trimIndent(),
            { rs, _ -> mapMonitoredService(rs) },
            id
        ).firstOrNull()
    }

    private fun findByServiceNameAndEnvironment(serviceName: String, environment: String): MonitoredService? {
        return jdbcTemplate.query(
            """
            SELECT id, service_name, base_url, environment, enabled
            FROM monitored_service
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            { rs, _ -> mapMonitoredService(rs) },
            serviceName,
            environment
        ).firstOrNull()
    }

    fun update(
        id: Long,
        serviceName: String,
        baseUrl: String,
        environment: String,
        enabled: Boolean
    ): MonitoredService? {
        val updated = jdbcTemplate.update(
            """
            UPDATE monitored_service
            SET service_name = ?,
                base_url = ?,
                environment = ?,
                enabled = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """.trimIndent(),
            serviceName,
            baseUrl,
            environment,
            enabled,
            id
        )

        if (updated == 0) {
            return null
        }

        return findById(id)
    }

    fun delete(id: Long): Boolean {
        return jdbcTemplate.update(
            "DELETE FROM monitored_service WHERE id = ?",
            id
        ) > 0
    }

    private fun mapMonitoredService(rs: java.sql.ResultSet): MonitoredService {
        return MonitoredService(
            id = rs.getLong("id"),
            serviceName = rs.getString("service_name"),
            baseUrl = rs.getString("base_url"),
            environment = rs.getString("environment"),
            enabled = rs.getBoolean("enabled")
        )
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
