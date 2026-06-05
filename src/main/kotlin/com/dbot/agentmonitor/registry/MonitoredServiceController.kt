package com.dbot.agentmonitor.registry

import com.dbot.agentmonitor.domain.MonitoredService
import com.dbot.agentmonitor.domain.MonitoredServiceDetailSnapshot
import com.dbot.agentmonitor.domain.MonitoredServiceOverview
import com.dbot.agentmonitor.domain.ServicePollResult
import com.dbot.agentmonitor.store.AlertEventStore
import com.dbot.agentmonitor.store.IncidentStore
import com.dbot.agentmonitor.store.MonitoredServiceStore
import com.dbot.agentmonitor.store.OperatorActionStore
import com.dbot.agentmonitor.store.ServiceStatusStore
import com.dbot.agentmonitor.polling.ServicePollingCommand
import com.dbot.agentmonitor.polling.ServicePollingService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
@Validated
@RequestMapping("/api/monitored-services")
class MonitoredServiceController(
    private val monitoredServiceStore: MonitoredServiceStore,
    private val incidentStore: IncidentStore,
    private val alertEventStore: AlertEventStore,
    private val serviceStatusStore: ServiceStatusStore,
    private val servicePollingCommand: ServicePollingCommand,
    private val servicePollingService: ServicePollingService,
    private val operatorActionStore: OperatorActionStore
) {
    @GetMapping
    fun list(): List<MonitoredService> {
        return monitoredServiceStore.findAll()
    }

    @GetMapping("/overview")
    fun overview(): List<MonitoredServiceOverview> {
        return monitoredServiceStore.findAllOverviews()
    }

    @GetMapping("/{id}/detail")
    fun detail(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "4") limit: Int
    ): MonitoredServiceDetailSnapshot {
        return detailSnapshot(id, limit)
    }

    @PostMapping("/{id}/check")
    fun checkNow(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "4") limit: Int
    ): Mono<MonitoredServiceDetailSnapshot> =
        Mono.fromCallable {
            val service = monitoredServiceStore.findById(id)
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Monitored service not found. id=$id"
                )

            servicePollingCommand.pollAndRecord(service)
                .also { result ->
                    operatorActionStore.record(
                        actionType = "MANUAL_CHECK",
                        targetServiceName = service.serviceName,
                        targetEnvironment = service.environment,
                        status = if (result.error == null) "SUCCESS" else "WARNING",
                        message = "Manual check completed. healthStatus=${result.healthStatus}, runStatus=${result.runStatus}"
                    )
                }
            detailSnapshot(id, limit)
        }.subscribeOn(Schedulers.boundedElastic())

    @PostMapping("/probe")
    fun probe(@Valid @RequestBody request: ProbeMonitoredServiceRequest): Mono<ServicePollResult> =
        Mono.fromCallable {
            servicePollingService.poll(
                MonitoredService(
                    id = 0,
                    serviceName = request.serviceName,
                    baseUrl = request.baseUrl,
                    environment = request.environment,
                    enabled = true
                )
            ).also { result ->
                operatorActionStore.record(
                    actionType = "CONNECTION_PROBE",
                    targetServiceName = request.serviceName,
                    targetEnvironment = request.environment,
                    status = if (result.error == null) "SUCCESS" else "WARNING",
                    message = "Connection probe completed. healthStatus=${result.healthStatus}, runStatus=${result.runStatus}"
                )
            }
        }.subscribeOn(Schedulers.boundedElastic())

    private fun detailSnapshot(id: Long, limit: Int): MonitoredServiceDetailSnapshot {
        val boundedLimit = limit.coerceIn(1, 10)
        val service = monitoredServiceStore.findOverviewById(id)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Monitored service not found. id=$id"
            )

        return MonitoredServiceDetailSnapshot(
            service = service,
            checks = serviceStatusStore.findRecentCheckSnapshots(service.serviceName, service.environment, boundedLimit),
            incidents = incidentStore.findRecentForService(service.serviceName, service.environment, boundedLimit),
            alerts = alertEventStore.findRecentForService(service.serviceName, service.environment, boundedLimit)
        )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: UpsertMonitoredServiceRequest): MonitoredService {
        return try {
            monitoredServiceStore.create(
                serviceName = request.serviceName,
                baseUrl = request.baseUrl,
                environment = request.environment,
                enabled = request.enabled
            ).also { created ->
                operatorActionStore.record(
                    actionType = "SERVICE_CREATED",
                    targetServiceName = created.serviceName,
                    targetEnvironment = created.environment,
                    status = "SUCCESS",
                    message = "Monitored service created. enabled=${created.enabled}"
                )
            }
        } catch (_: DataIntegrityViolationException) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "A monitored service with the same serviceName and environment already exists."
            )
        }
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpsertMonitoredServiceRequest
    ): MonitoredService {
        return try {
            val before = monitoredServiceStore.findById(id)
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Monitored service not found. id=$id"
                )

            val updated = monitoredServiceStore.update(
                id = id,
                serviceName = request.serviceName,
                baseUrl = request.baseUrl,
                environment = request.environment,
                enabled = request.enabled
            ) ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Monitored service not found. id=$id"
            )

            val actionType = when {
                before.enabled && !updated.enabled -> "SERVICE_DISABLED"
                !before.enabled && updated.enabled -> "SERVICE_ENABLED"
                else -> "SERVICE_UPDATED"
            }

            operatorActionStore.record(
                actionType = actionType,
                targetServiceName = updated.serviceName,
                targetEnvironment = updated.environment,
                status = "SUCCESS",
                message = "Monitored service updated. enabled=${updated.enabled}"
            )

            updated
        } catch (_: DataIntegrityViolationException) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "A monitored service with the same serviceName and environment already exists."
            )
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) {
        val service = monitoredServiceStore.findById(id)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Monitored service not found. id=$id"
            )
        val deleted = monitoredServiceStore.delete(id)
        if (!deleted) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Monitored service not found. id=$id"
            )
        }

        operatorActionStore.record(
            actionType = "SERVICE_DELETED",
            targetServiceName = service.serviceName,
            targetEnvironment = service.environment,
            status = "SUCCESS",
            message = "Monitored service deleted."
        )
    }
}

data class UpsertMonitoredServiceRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val serviceName: String,
    @field:NotBlank
    @field:Size(max = 255)
    val baseUrl: String,
    @field:NotBlank
    @field:Size(max = 50)
    val environment: String,
    val enabled: Boolean = true
)

data class ProbeMonitoredServiceRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val serviceName: String,
    @field:NotBlank
    @field:Size(max = 255)
    val baseUrl: String,
    @field:NotBlank
    @field:Size(max = 50)
    val environment: String
)
