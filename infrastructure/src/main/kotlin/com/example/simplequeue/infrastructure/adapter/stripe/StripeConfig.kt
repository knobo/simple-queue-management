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
    val defaultCountry: String = "NO",
    val supportedCountries: String = "NO,SE,DK,FI,DE,GB,US",
) {
    /**
     * Get the list of supported country codes for Stripe Connect.
     * Stripe requires 2-letter ISO country codes.
     */
    fun getSupportedCountriesList(): List<String> = 
        supportedCountries.split(",").map { it.trim().uppercase() }
    
    /**
     * Check if a country is supported for Stripe Connect.
     */
    fun isCountrySupported(countryCode: String): Boolean =
        countryCode.uppercase() in getSupportedCountriesList()
}

// Alias for backward compatibility
typealias StripeConfig = StripeProperties
