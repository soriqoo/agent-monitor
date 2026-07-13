package com.dbot.agentmonitor.incident

import com.dbot.agentmonitor.config.AppProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.doAnswer
import org.springframework.scheduling.annotation.Scheduled
import java.time.OffsetDateTime
import java.time.ZoneOffset

class IncidentReminderSchedulerTests {

    @Test
    fun sendDueRemindersDelegatesWithConfiguredTimezoneAndScheduledBindings() {
        val reminderService = mock(IncidentReminderService::class.java)
        var delegatedNow: OffsetDateTime? = null
        doAnswer { invocation ->
            delegatedNow = invocation.getArgument(0)
            0
        }.`when`(reminderService).sendDueReminders(any(OffsetDateTime::class.java) ?: OffsetDateTime.MIN)
        val scheduler = IncidentReminderScheduler(
            incidentReminderService = reminderService,
            appProperties = AppProperties(timezone = "UTC")
        )

        scheduler.sendDueReminders()

        assertThat(delegatedNow).isNotNull
        assertThat(delegatedNow!!.offset).isEqualTo(ZoneOffset.UTC)

        val scheduled = IncidentReminderScheduler::class.java
            .getMethod("sendDueReminders")
            .getAnnotation(Scheduled::class.java)
        assertThat(scheduled.cron).isEqualTo("\${app.slack.incident-reminder-cron}")
        assertThat(scheduled.zone).isEqualTo("\${app.timezone}")
    }
}
