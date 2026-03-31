package com.dbot.agentmonitor.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.util.concurrent.TimeUnit

@Configuration
class WebClientConfig(
    private val appProperties: AppProperties
) {
    @Bean
    fun webClient(): WebClient {
        val client = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, appProperties.monitoring.connectTimeoutMs)
            .doOnConnected {
                it.addHandlerLast(ReadTimeoutHandler(appProperties.monitoring.timeoutMs, TimeUnit.MILLISECONDS))
            }

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(client))
            .build()
    }
}
