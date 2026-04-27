package com.dbot.agentmonitor.registry

import com.dbot.agentmonitor.domain.MonitoredService
import com.dbot.agentmonitor.store.MonitoredServiceStore
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@Validated
@RequestMapping("/api/monitored-services")
class MonitoredServiceController(
    private val monitoredServiceStore: MonitoredServiceStore
) {
    @GetMapping
    fun list(): List<MonitoredService> {
        return monitoredServiceStore.findAll()
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
            )
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
            monitoredServiceStore.update(
                id = id,
                serviceName = request.serviceName,
                baseUrl = request.baseUrl,
                environment = request.environment,
                enabled = request.enabled
            ) ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Monitored service not found. id=$id"
            )
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
        val deleted = monitoredServiceStore.delete(id)
        if (!deleted) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Monitored service not found. id=$id"
            )
        }
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
