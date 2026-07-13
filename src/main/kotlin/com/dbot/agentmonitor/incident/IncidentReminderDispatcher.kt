package com.dbot.agentmonitor.incident

import com.dbot.agentmonitor.alert.SlackAlertService
import com.dbot.agentmonitor.store.IncidentStore
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class IncidentReminderDispatcher(
    private val incidentStore: IncidentStore,
    private val slackAlertService: SlackAlertService
) {
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.READ_COMMITTED
    )
    fun sendReminderIfDue(
        incidentId: Long,
        cutoff: OffsetDateTime,
        remindedAt: OffsetDateTime
    ): Boolean {
        if (!incidentStore.lockIncident(incidentId)) {
            return false
        }

        val candidate = incidentStore.findOpenIncidentDueForReminder(incidentId, cutoff) ?: return false
        return slackAlertService.notifyIncidentReminder(candidate, remindedAt)
    }
}
