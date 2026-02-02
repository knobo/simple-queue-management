package com.example.simplequeue.infrastructure.config

import com.example.simplequeue.infrastructure.adapter.notification.NtfyClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

@Configuration
open class NtfyConfig {
    @Bean
    open fun ntfyClient(
        @Value("\${ntfy.url:https://ntfy.sh}") ntfyUrl: String,
        @Value("\${ntfy.username:}") ntfyUsername: String,
        @Value("\${ntfy.password:}") ntfyPassword: String,
    ): NtfyClient {
        val restClientBuilder = RestClient.builder().baseUrl(ntfyUrl)
        if (ntfyUsername.isNotBlank() && ntfyPassword.isNotBlank()) {
            restClientBuilder.defaultHeaders { it.setBasicAuth(ntfyUsername, ntfyPassword) }
        }
        val restClient = restClientBuilder.build()
        val factory =
            HttpServiceProxyFactory
                .builderFor(
                    RestClientAdapter.create(restClient),
                ).build()
        return factory.createClient(NtfyClient::class.java)
    }
}
