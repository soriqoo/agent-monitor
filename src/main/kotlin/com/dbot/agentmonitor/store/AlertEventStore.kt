package com.dbot.agentmonitor.store

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
}
