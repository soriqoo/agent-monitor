package com.dbot.agentmonitor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class AgentMonitorApplicationTests {
    @Autowired
    lateinit var taskScheduler: ThreadPoolTaskScheduler

    @Test
    fun contextLoads() {
    }

    @Test
    fun scheduledWorkUsesConfiguredPool() {
        assertThat(taskScheduler.scheduledThreadPoolExecutor.corePoolSize).isEqualTo(3)
    }
}
