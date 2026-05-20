package com.dbot.agentmonitor.monitoring

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime

@SpringBootTest(
    properties = [
        "app.monitoring.retention-days=7"
    ]
)
@ActiveProfiles("test")
class HistoryRetentionSchedulerFailureIntegrationTests {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    lateinit var historyRetentionScheduler: HistoryRetentionScheduler

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM retention_run_history")
    }

    @Test
    fun runRetentionRecordsFailureWithoutThrowingFromScheduler() {
        assertThatCode { historyRetentionScheduler.runRetention() }
            .doesNotThrowAnyException()

        val row = jdbcTemplate.queryForMap(
            """
            SELECT status,
                   retention_days,
                   deleted_service_checks,
                   deleted_alert_events,
                   deleted_resolved_incidents,
                   error,
                   completed_at
            FROM retention_run_history
            ORDER BY id DESC
            LIMIT 1
            """.trimIndent()
        )

        assertThat(row["STATUS"]).isEqualTo("FAILED")
        assertThat((row["RETENTION_DAYS"] as Number).toLong()).isEqualTo(7)
        assertThat((row["DELETED_SERVICE_CHECKS"] as Number).toInt()).isEqualTo(0)
        assertThat((row["DELETED_ALERT_EVENTS"] as Number).toInt()).isEqualTo(0)
        assertThat((row["DELETED_RESOLVED_INCIDENTS"] as Number).toInt()).isEqualTo(0)
        assertThat(row["ERROR"]).isEqualTo("retention exploded")
        assertThat(row["COMPLETED_AT"]).isNotNull()
    }

    @TestConfiguration
    class FailurePrunerConfig {

        @Bean
        @Primary
        fun failingHistoryRetentionPruner(): HistoryRetentionPruner {
            return object : HistoryRetentionPruner {
                override fun prune(now: OffsetDateTime, retentionDays: Long): HistoryRetentionResult {
                    throw IllegalStateException("retention exploded")
                }
            }
        }
    }
}
