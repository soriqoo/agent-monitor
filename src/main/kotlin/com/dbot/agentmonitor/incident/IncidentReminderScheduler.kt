package com.dbot.agentmonitor.incident

import com.dbot.agentmonitor.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneId

@Component
class IncidentReminderScheduler(
    private val incidentReminderService: IncidentReminderService,
    private val appProperties: AppProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${app.slack.incident-reminder-cron}", zone = "\${app.timezone}")
    fun sendDueReminders() {
        val now = OffsetDateTime.now(ZoneId.of(appProperties.timezone))

        try {
            val reminderCount = incidentReminderService.sendDueReminders(now)
            if (reminderCount > 0) {
                log.info("Sent sustained incident reminders. reminderCount={}", reminderCount)
            }
        } catch (error: Exception) {
            log.error("Failed to send sustained incident reminders. now={}", now, error)
        }
    }
}
