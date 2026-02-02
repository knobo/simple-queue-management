package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.service.CommissionService
import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.domain.model.CommissionEntry
import com.example.simplequeue.domain.model.Subscription
import com.example.simplequeue.domain.model.SubscriptionTier
import com.example.simplequeue.domain.port.PaymentGateway
import com.example.simplequeue.domain.port.WebhookResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat

class StripeWebhookControllerTest {

    private lateinit var paymentGateway: PaymentGateway
    private lateinit var subscriptionService: SubscriptionService
    private lateinit var commissionService: CommissionService
    private lateinit var controller: StripeWebhookController

    @BeforeEach
    fun setUp() {
        paymentGateway = mock()
        subscriptionService = mock()
        commissionService = mock()
        controller = StripeWebhookController(
            paymentGateway = paymentGateway,
            subscriptionService = subscriptionService,
            commissionService = commissionService,
        )
    }

    @Nested
    inner class HandleInvoicePaid {

        @Test
        fun `should process commission when invoice paid and subscription exists`() {
            // Given
            val now = Instant.now()
            val subscriptionId = UUID.randomUUID()
            val subscription = Subscription(
                id = subscriptionId,
                userId = "user-123",
                tier = SubscriptionTier.PRO,
                status = Subscription.SubscriptionStatus.ACTIVE,
                stripeCustomerId = "cus_123",
                stripeSubscriptionId = "sub_456",
                currentPeriodStart = now,
                currentPeriodEnd = now.plusSeconds(30 * 24 * 60 * 60),
                cancelAtPeriodEnd = false,
                createdAt = now,
                updatedAt = now,
            )

            val invoicePaidResult = WebhookResult.InvoicePaid(
                invoiceId = "in_test123",
                stripeSubscriptionId = "sub_456",
                stripeCustomerId = "cus_123",
                amount = BigDecimal("99.00"),
                periodStart = now,
                periodEnd = now.plusSeconds(30 * 24 * 60 * 60),
            )

            whenever(paymentGateway.handleWebhookEvent(any(), any())).thenReturn(invoicePaidResult)
            whenever(subscriptionService.findByStripeCustomerId("cus_123")).thenReturn(subscription)

            // When
            val response = controller.handleWebhook("payload", "signature")

            // Then
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            verify(commissionService).processPayment(
                subscriptionId = eq(subscriptionId),
                grossAmount = eq(BigDecimal("99.00")),
                sourceType = eq(CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT),
                sourceReference = eq("in_test123"),
                periodStart = any<LocalDate>(),
                periodEnd = any<LocalDate>(),
            )
        }

        @Test
        fun `should skip commission when subscription not found`() {
            // Given
            val now = Instant.now()
            val invoicePaidResult = WebhookResult.InvoicePaid(
                invoiceId = "in_test123",
                stripeSubscriptionId = "sub_456",
                stripeCustomerId = "cus_unknown",
                amount = BigDecimal("99.00"),
                periodStart = now,
                periodEnd = now.plusSeconds(30 * 24 * 60 * 60),
            )

            whenever(paymentGateway.handleWebhookEvent(any(), any())).thenReturn(invoicePaidResult)
            whenever(subscriptionService.findByStripeCustomerId("cus_unknown")).thenReturn(null)

            // When
            val response = controller.handleWebhook("payload", "signature")

            // Then
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            verify(commissionService, never()).processPayment(
                subscriptionId = any(),
                grossAmount = any(),
                sourceType = any(),
                sourceReference = any(),
                periodStart = any<LocalDate>(),
                periodEnd = any<LocalDate>(),
            )
        }
    }

    @Nested
    inner class HandleIgnored {

        @Test
        fun `should return OK for ignored events`() {
            // Given
            whenever(paymentGateway.handleWebhookEvent(any(), any())).thenReturn(WebhookResult.Ignored)

            // When
            val response = controller.handleWebhook("payload", "signature")

            // Then
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }
    }

    @Nested
    inner class HandleErrors {

        @Test
        fun `should return bad request for invalid signature`() {
            // Given
            whenever(paymentGateway.handleWebhookEvent(any(), any()))
                .thenThrow(IllegalArgumentException("Invalid webhook signature"))

            // When
            val response = controller.handleWebhook("payload", "invalid_signature")

            // Then
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(response.body).isEqualTo("Invalid signature")
        }
    }
}
