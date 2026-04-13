package com.dbot.agentmonitor.store

import com.dbot.agentmonitor.domain.ServicePollResult
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ServiceStatusStore(
    private val jdbcTemplate: JdbcTemplate
) {
    data class StoredCheck(
        val healthStatus: String,
        val runStatus: String?,
        val error: String?
    )

    @Transactional
    fun recordPollResult(result: ServicePollResult) {
        appendHistory(result)
        upsertCurrentStatus(result)
    }

    fun countHistory(serviceName: String, environment: String): Long {
        return jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM service_check_history
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            Long::class.java,
            serviceName,
            environment
        ) ?: 0L
    }

    fun findRecentChecks(serviceName: String, environment: String, limit: Int): List<StoredCheck> {
        return jdbcTemplate.query(
            """
            SELECT health_status, run_status, error
            FROM service_check_history
            WHERE service_name = ? AND environment = ?
            ORDER BY checked_at DESC, id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                StoredCheck(
                    healthStatus = rs.getString("health_status"),
                    runStatus = rs.getString("run_status"),
                    error = rs.getString("error")
                )
            },
            serviceName,
            environment,
            limit
        )
    }

    private fun appendHistory(result: ServicePollResult) {
        jdbcTemplate.update(
            """
            INSERT INTO service_check_history(
                service_name,
                environment,
                health_status,
                run_status,
                last_run_date,
                response_time_ms,
                error,
                checked_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            result.serviceName,
            result.environment,
            result.healthStatus.name,
            result.runStatus,
            result.lastRunDate,
            result.responseTimeMs,
            result.error,
            result.checkedAt
        )
    }

    private fun upsertCurrentStatus(result: ServicePollResult) {
        val updated = jdbcTemplate.update(
            """
            UPDATE service_current_status
            SET health_status = ?,
                run_status = ?,
                last_run_date = ?,
                last_success_at = ?,
                last_checked_at = ?,
                error = ?
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            result.healthStatus.name,
            result.runStatus,
            result.lastRunDate,
            result.lastSuccessAt,
            result.checkedAt,
            result.error,
            result.serviceName,
            result.environment
        )

        if (updated == 0) {
            jdbcTemplate.update(
                """
                INSERT INTO service_current_status(
                    service_name,
                    environment,
                    health_status,
                    run_status,
                    last_run_date,
                    last_success_at,
                    last_checked_at,
                    error
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                result.serviceName,
                result.environment,
                result.healthStatus.name,
                result.runStatus,
                result.lastRunDate,
                result.lastSuccessAt,
                result.checkedAt,
                result.error
            )
        }
    }
}
