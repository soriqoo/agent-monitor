package com.dbot.agentmonitor.store

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator

class SchemaMigrationTests {

    @Test
    fun schemaAddsFailureTypeColumnsToExistingStatusTables() {
        val dataSource = DriverManagerDataSource("jdbc:h2:mem:schema-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        val jdbcTemplate = JdbcTemplate(dataSource)
        jdbcTemplate.execute(
            """
            CREATE TABLE service_check_history (
                id BIGSERIAL PRIMARY KEY,
                service_name VARCHAR(100) NOT NULL,
                environment VARCHAR(50) NOT NULL,
                health_status VARCHAR(50) NOT NULL,
                checked_at TIMESTAMP WITH TIME ZONE NOT NULL
            )
            """.trimIndent()
        )
        jdbcTemplate.execute(
            """
            CREATE TABLE service_current_status (
                service_name VARCHAR(100) NOT NULL,
                environment VARCHAR(50) NOT NULL,
                health_status VARCHAR(50) NOT NULL,
                last_checked_at TIMESTAMP WITH TIME ZONE NOT NULL,
                PRIMARY KEY (service_name, environment)
            )
            """.trimIndent()
        )

        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(dataSource)

        val historyColumns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'SERVICE_CHECK_HISTORY'",
            String::class.java
        )
        val currentColumns = jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'SERVICE_CURRENT_STATUS'",
            String::class.java
        )
        assertThat(historyColumns).contains("FAILURE_TYPE")
        assertThat(currentColumns).contains("FAILURE_TYPE")
    }
}
