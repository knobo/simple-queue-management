package com.example.simplequeue.infrastructure.adapter.stripe

import com.example.simplequeue.domain.model.SubscriptionTier
import com.example.simplequeue.domain.port.WebhookResult
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StripePaymentGatewayTest {

    private lateinit var config: StripeProperties
    private lateinit var gateway: StripePaymentGateway

    @BeforeEach
    fun setUp() {
        config = StripeProperties(
            secretKey = "sk_test_dummy",
            publishableKey = "pk_test_dummy",
            webhookSecret = "whsec_test_dummy",
            prices = StripeProperties.PriceConfig(
                starter = "price_starter_123",
                pro = "price_pro_456",
                enterprise = "price_enterprise_789",
            ),
        )
        gateway = StripePaymentGateway(config)
    }

    @Nested
    inner class CreateCheckoutSession {

        @Test
        fun `should reject FREE tier`() {
            assertThatThrownBy {
                gateway.createCheckoutSession(
                    userId = "user-1",
                    tier = SubscriptionTier.FREE,
                    successUrl = "https://example.com/success",
                    cancelUrl = "https://example.com/cancel",
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("No Stripe price configured for tier: FREE")
        }

        // Note: Testing actual Stripe API calls would require mocking the Stripe SDK
        // which is complex. In practice, these would be tested with Stripe's test mode
        // in integration tests or with a mock HTTP client.
    }

    @Nested
    inner class WebhookSignatureValidation {

        @Test
        fun `should reject invalid signature`() {
            val invalidPayload = """{"type": "test"}"""
            val invalidSignature = "invalid_signature"

            assertThatThrownBy {
                gateway.handleWebhookEvent(invalidPayload, invalidSignature)
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Invalid webhook signature")
        }
    }

    @Nested
    inner class TierMapping {

        @Test
        fun `config should map tiers to price IDs correctly`() {
            assertThat(config.prices.starter).isEqualTo("price_starter_123")
            assertThat(config.prices.pro).isEqualTo("price_pro_456")
            assertThat(config.prices.enterprise).isEqualTo("price_enterprise_789")
        }
    }
}
