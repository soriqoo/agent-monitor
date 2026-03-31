package com.dbot.agentmonitor

import com.dbot.agentmonitor.config.AppProperties
import com.dbot.agentmonitor.store.MonitoredServiceStore
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@EnableConfigurationProperties(AppProperties::class)
@SpringBootApplication
class AgentMonitorApplication {
    @Bean
    fun monitoredServiceStore(jdbcTemplate: JdbcTemplate) = MonitoredServiceStore(jdbcTemplate)
}

fun main(args: Array<String>) {
    runApplication<AgentMonitorApplication>(*args)
}
