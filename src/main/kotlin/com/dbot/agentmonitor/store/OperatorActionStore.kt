package com.dbot.agentmonitor.store

import com.dbot.agentmonitor.domain.RecentOperatorAction
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.OffsetDateTime

@Component
class OperatorActionStore(
    private val jdbcTemplate: JdbcTemplate
) {

    fun record(
        actionType: String,
        targetServiceName: String?,
        targetEnvironment: String?,
        status: String,
        message: String?
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO operator_action_event(
                action_type,
                target_service_name,
                target_environment,
                status,
                message,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(),
            actionType,
            targetServiceName,
            targetEnvironment,
            status,
            message
        )
    }

    fun findRecent(limit: Int): List<RecentOperatorAction> {
        return jdbcTemplate.query(
            """
            SELECT id,
                   action_type,
                   target_service_name,
                   target_environment,
                   status,
                   message,
                   created_at
            FROM operator_action_event
            ORDER BY id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.toRecentOperatorAction() },
            limit
        )
    }

    private fun ResultSet.toRecentOperatorAction(): RecentOperatorAction {
        return RecentOperatorAction(
            id = getLong("id"),
            actionType = getString("action_type"),
            targetServiceName = getString("target_service_name"),
            targetEnvironment = getString("target_environment"),
            status = getString("status"),
            message = getString("message"),
            createdAt = getObject("created_at", OffsetDateTime::class.java)
        )
    }
}
