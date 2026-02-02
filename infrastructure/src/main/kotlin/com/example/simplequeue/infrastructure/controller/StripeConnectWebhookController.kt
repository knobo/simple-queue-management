package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.domain.port.ConnectWebhookResult
import com.example.simplequeue.domain.port.SellerPaymentGateway
import com.example.simplequeue.domain.port.SellerRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Webhook endpoint for Stripe Connect events.
 * This handles account.updated and transfer.created events.
 * 
 * IMPORTANT: This endpoint must be permitAll in SecurityConfig
 * as Stripe webhooks don't authenticate via Keycloak.
 */
@RestController
@RequestMapping("/webhook")
class StripeConnectWebhookController(
    private val sellerPaymentGateway: SellerPaymentGateway,
    private val sellerRepository: SellerRepository,
) {
    private val logger = LoggerFactory.getLogger(StripeConnectWebhookController::class.java)

    /**
     * Handle Stripe Connect webhook events.
     * Configure this endpoint in Stripe Dashboard under Connect webhook settings.
     */
    @PostMapping("/connect")
    fun handleConnectWebhook(
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") signature: String,
    ): ResponseEntity<String> {
        return try {
            val result = sellerPaymentGateway.handleConnectWebhook(payload, signature)

            when (result) {
                is ConnectWebhookResult.AccountUpdated -> {
                    handleAccountUpdated(result)
                }
                is ConnectWebhookResult.TransferCreated -> {
                    handleTransferCreated(result)
                }
                is ConnectWebhookResult.Ignored -> {
                    logger.debug("Connect webhook event ignored")
                }
            }

            ResponseEntity.ok("OK")
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid Connect webhook signature: {}", e.message)
            ResponseEntity.badRequest().body("Invalid signature")
        } catch (e: Exception) {
            logger.error("Error processing Connect webhook", e)
            ResponseEntity.internalServerError().body("Internal error")
        }
    }

    private fun handleAccountUpdated(result: ConnectWebhookResult.AccountUpdated) {
        logger.info(
            "Processing account.updated: account={}, charges={}, payouts={}, details={}",
            result.stripeAccountId,
            result.chargesEnabled,
            result.payoutsEnabled,
            result.detailsSubmitted,
        )

        val seller = sellerRepository.findByStripeAccountId(result.stripeAccountId)
        if (seller == null) {
            logger.warn("No seller found for Stripe account: {}", result.stripeAccountId)
            return
        }

        val updatedSeller = seller.withStripeStatus(
            chargesEnabled = result.chargesEnabled,
            payoutsEnabled = result.payoutsEnabled,
            onboardingCompleted = result.detailsSubmitted,
        )
        sellerRepository.save(updatedSeller)

        logger.info(
            "Updated seller {} Stripe status: charges={}, payouts={}, completed={}",
            seller.id,
            result.chargesEnabled,
            result.payoutsEnabled,
            result.detailsSubmitted,
        )

        // Log if seller is now ready to receive payouts
        if (result.chargesEnabled && result.payoutsEnabled) {
            logger.info("Seller {} is now ready to receive payouts!", seller.id)
        }
    }

    private fun handleTransferCreated(result: ConnectWebhookResult.TransferCreated) {
        logger.info(
            "Transfer created: id={}, account={}, amount={} {}",
            result.transferId,
            result.stripeAccountId,
            result.amount,
            result.currency,
        )
        // Transfer tracking could be implemented here if needed
        // For now, we just log it
    }
}
