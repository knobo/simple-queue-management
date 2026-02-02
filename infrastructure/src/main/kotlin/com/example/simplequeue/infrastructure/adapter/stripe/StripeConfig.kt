package com.example.simplequeue.infrastructure.adapter.stripe

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(StripeProperties::class, StripeConnectProperties::class)
class StripeConfiguration

@ConfigurationProperties(prefix = "stripe")
data class StripeProperties(
    val secretKey: String = "",
    val publishableKey: String = "",
    val webhookSecret: String = "",
    val prices: PriceConfig = PriceConfig(),
) {
    data class PriceConfig(
        val starter: String = "",
        val pro: String = "",
        val enterprise: String = "",
    )
}

@ConfigurationProperties(prefix = "stripe.connect")
data class StripeConnectProperties(
    val webhookSecret: String = "",
)

// Alias for backward compatibility
typealias StripeConfig = StripeProperties
