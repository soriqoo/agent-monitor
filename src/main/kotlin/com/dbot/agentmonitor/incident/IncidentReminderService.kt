package com.dbot.agentmonitor.incident

import com.dbot.agentmonitor.config.AppProperties
import com.dbot.agentmonitor.store.IncidentStore
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class IncidentReminderService(
    private val incidentStore: IncidentStore,
    private val appProperties: AppProperties,
    private val incidentReminderDispatcher: IncidentReminderDispatcher
) {
    fun sendDueReminders(now: OffsetDateTime): Int {
        val slack = appProperties.slack
        if (!slack.enabled || slack.webhookUrl.isBlank() || !slack.incidentReminderEnabled) {
            return 0
        }

        val cutoff = now.minusMinutes(maxOf(1, slack.incidentReminderIntervalMinutes))
        return incidentStore.findOpenIncidentsDueForReminder(cutoff)
            .count { candidate -> incidentReminderDispatcher.sendReminderIfDue(candidate.id, cutoff, now) }
    }
}
