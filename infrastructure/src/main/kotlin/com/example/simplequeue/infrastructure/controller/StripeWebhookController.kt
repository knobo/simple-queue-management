package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.service.CommissionService
import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.domain.model.CommissionEntry
import com.example.simplequeue.domain.port.PaymentGateway
import com.example.simplequeue.domain.port.WebhookResult
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId

@RestController
@RequestMapping("/api/stripe")
class StripeWebhookController(
    private val paymentGateway: PaymentGateway,
    private val subscriptionService: SubscriptionService,
    private val commissionService: CommissionService,
) {
    private val logger = LoggerFactory.getLogger(StripeWebhookController::class.java)

    /**
     * Handle Stripe webhook events.
     * This endpoint must be configured in the Stripe Dashboard.
     */
    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") signature: String,
    ): ResponseEntity<String> {
        return try {
            val result = paymentGateway.handleWebhookEvent(payload, signature)

            when (result) {
                is WebhookResult.SubscriptionCreated -> {
                    logger.info(
                        "Processing subscription created: customer={}, user={}",
                        result.stripeCustomerId,
                        result.userId
                    )
                    subscriptionService.handleSubscriptionCreated(result)
                }
                is WebhookResult.SubscriptionUpdated -> {
                    logger.info(
                        "Processing subscription updated: customer={}, tier={}, status={}",
                        result.stripeCustomerId,
                        result.tier,
                        result.status
                    )
                    subscriptionService.handleSubscriptionUpdated(result)
                }
                is WebhookResult.SubscriptionDeleted -> {
                    logger.info(
                        "Processing subscription deleted: customer={}",
                        result.stripeCustomerId
                    )
                    subscriptionService.handleSubscriptionDeleted(result)
                }
                is WebhookResult.PaymentFailed -> {
                    logger.warn(
                        "Processing payment failed: customer={}",
                        result.stripeCustomerId
                    )
                    subscriptionService.handlePaymentFailed(result)
                }
                is WebhookResult.InvoicePaid -> {
                    logger.info(
                        "Processing invoice paid: invoice={}, customer={}, amount={}",
                        result.invoiceId,
                        result.stripeCustomerId,
                        result.amount
                    )
                    // Look up internal subscription by Stripe customer ID
                    val subscription = subscriptionService.findByStripeCustomerId(result.stripeCustomerId)
                    if (subscription != null) {
                        commissionService.processPayment(
                            subscriptionId = subscription.id,
                            grossAmount = result.amount,
                            sourceType = CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT,
                            sourceReference = result.invoiceId,
                            periodStart = result.periodStart.atZone(ZoneId.of("UTC")).toLocalDate(),
                            periodEnd = result.periodEnd.atZone(ZoneId.of("UTC")).toLocalDate(),
                        )
                    } else {
                        logger.warn(
                            "No subscription found for Stripe customer {}, skipping commission",
                            result.stripeCustomerId
                        )
                    }
                }
                is WebhookResult.Ignored -> {
                    logger.debug("Event ignored")
                }
            }

            ResponseEntity.ok("OK")
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid webhook: {}", e.message)
            ResponseEntity.badRequest().body("Invalid signature")
        } catch (e: IllegalStateException) {
            logger.error("Error processing webhook: {}", e.message, e)
            ResponseEntity.badRequest().body("Processing error: ${e.message}")
        } catch (e: Exception) {
            logger.error("Unexpected error processing webhook", e)
            ResponseEntity.internalServerError().body("Internal error")
        }
    }
}
