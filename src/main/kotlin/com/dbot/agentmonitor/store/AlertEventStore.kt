package com.dbot.agentmonitor.store

import com.dbot.agentmonitor.domain.RecentAlertEvent
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class AlertEventStore(
    private val jdbcTemplate: JdbcTemplate
) {
    fun recordAlertEvent(
        serviceName: String,
        environment: String,
        alertType: String,
        message: String,
        sentAt: OffsetDateTime
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO alert_event(service_name, environment, alert_type, message, sent_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            serviceName,
            environment,
            alertType,
            message,
            sentAt
        )
    }

    fun findRecent(limit: Int): List<RecentAlertEvent> {
        return jdbcTemplate.query(
            """
            SELECT id, service_name, environment, alert_type, message, sent_at
            FROM alert_event
            ORDER BY sent_at DESC, id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                RecentAlertEvent(
                    id = rs.getLong("id"),
                    serviceName = rs.getString("service_name"),
                    environment = rs.getString("environment"),
                    alertType = rs.getString("alert_type"),
                    message = rs.getString("message"),
                    sentAt = rs.getObject("sent_at", OffsetDateTime::class.java)
                )
            },
            limit
        )
    }
}
