package com.dbot.agentmonitor.bootstrap

import com.dbot.agentmonitor.config.AppProperties
import com.dbot.agentmonitor.store.MonitoredServiceStore
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class MonitoredServiceSeedInitializer(
    private val appProperties: AppProperties,
    private val monitoredServiceStore: MonitoredServiceStore
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        seedConfiguredServices()
    }

    fun seedConfiguredServices() {
        if (!appProperties.seed.enabled) {
            log.info("Monitored service seed is disabled.")
            return
        }

        if (appProperties.seed.monitoredServices.isEmpty()) {
            log.info("No monitored service seed configuration found.")
            return
        }

        appProperties.seed.monitoredServices.forEach { service ->
            monitoredServiceStore.upsert(
                serviceName = service.serviceName,
                baseUrl = service.baseUrl,
                environment = service.environment,
                enabled = service.enabled
            )
            log.info(
                "Seeded monitored service. serviceName={}, environment={}, baseUrl={}, enabled={}",
                service.serviceName,
                service.environment,
                service.baseUrl,
                service.enabled
            )
        }
    }
}
