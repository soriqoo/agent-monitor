package com.dbot.agentmonitor.examples

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class DummyMonitoredServiceExampleTests {

    private val root: Path = Path.of(System.getProperty("user.dir"))

    @Test
    fun dummyMonitoredServiceExampleDocumentsRequiredMonitoringEndpoints() {
        val readme = root.resolve("examples/dummy-monitored-service/README.md")
        val dockerfile = root.resolve("examples/dummy-monitored-service/Dockerfile")
        val source = root.resolve("examples/dummy-monitored-service/src/DummyMonitoredService.java")

        assertThat(readme).exists()
        assertThat(dockerfile).exists()
        assertThat(source).exists()

        val sourceText = Files.readString(source)
        assertThat(sourceText).contains("/actuator/health")
        assertThat(sourceText).contains("/internal/monitoring/last-run")
        assertThat(sourceText).contains("DUMMY_HEALTH_STATUS")
        assertThat(sourceText).contains("DUMMY_RUN_STATUS")
        assertThat(sourceText).contains("DUMMY_LAST_RUN_DATE")
    }

    @Test
    fun demoComposeAndRuntimeShellExposeDummyServiceWorkflow() {
        val demoCompose = root.resolve("docker-compose.demo.yml")
        val runtimeShellExample = root.resolve("ops/agent-monitor.sh.example")

        assertThat(demoCompose).exists()

        val composeText = Files.readString(demoCompose)
        assertThat(composeText).contains("dummy-monitored-service")
        assertThat(composeText).contains("examples/dummy-monitored-service")
        assertThat(composeText).contains("monitoring-shared")

        val shellText = Files.readString(runtimeShellExample)
        assertThat(shellText).contains("deploy-demo")
        assertThat(shellText).contains("ps-demo")
        assertThat(shellText).contains("docker-compose.demo.yml")
    }
}
