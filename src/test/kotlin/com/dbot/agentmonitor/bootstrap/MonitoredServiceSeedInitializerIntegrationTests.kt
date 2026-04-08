package com.dbot.agentmonitor.bootstrap

import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
    properties = [
        "app.seed.enabled=true",
        "app.seed.monitored-services[0].service-name=dmib",
        "app.seed.monitored-services[0].base-url=http://localhost:8081",
        "app.seed.monitored-services[0].environment=prod",
        "app.seed.monitored-services[0].enabled=true"
    ]
)
@ActiveProfiles("test")
class MonitoredServiceSeedInitializerIntegrationTests {

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun applicationStartupSeedsDmibService() {
        val row = jdbcTemplate.queryForMap(
            """
            SELECT service_name, base_url, environment, enabled
            FROM monitored_service
            WHERE service_name = ? AND environment = ?
            """.trimIndent(),
            "dmib",
            "prod"
        )

        assertThat(row["service_name"]).isEqualTo("dmib")
        assertThat(row["base_url"]).isEqualTo("http://localhost:8081")
        assertThat(row["environment"]).isEqualTo("prod")
        assertThat(row["enabled"]).isEqualTo(true)
    }
}
