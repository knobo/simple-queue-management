package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.domain.port.SellerPaymentGateway
import com.example.simplequeue.domain.port.SellerRepository
import com.stripe.exception.StripeException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/seller/connect")
@PreAuthorize("hasRole('SELLER') or hasRole('seller') or hasRole('SUPERADMIN') or hasRole('superadmin')")
class SellerConnectController(
    private val sellerRepository: SellerRepository,
    private val sellerPaymentGateway: SellerPaymentGateway,
    @Value("\${app.base-url:http://localhost:8080}") private val baseUrl: String,
    @Value("\${stripe.connect.default-country:NO}") private val defaultCountry: String,
) {
    private val logger = LoggerFactory.getLogger(SellerConnectController::class.java)

    /**
     * Start Stripe Connect onboarding.
     * Creates a Stripe Express account if not exists, then returns an account link URL.
     */
    @PostMapping("/start")
    fun startOnboarding(authentication: Authentication): ResponseEntity<OnboardingStartResponse> {
        val userId = getUserId(authentication.principal)
        val seller = sellerRepository.findByUserId(userId)
            ?: return ResponseEntity.notFound().build()

        logger.info("Starting Stripe Connect onboarding for seller: {}", seller.id)

        return try {
            // Create Stripe Express account if not exists
            val stripeAccountId = if (seller.stripeAccountId == null) {
                val accountId = sellerPaymentGateway.createExpressAccount(
                    email = seller.email,
                    country = defaultCountry,
                )
                // Save the stripeAccountId to seller
                val updatedSeller = seller.withStripeAccount(accountId)
                sellerRepository.save(updatedSeller)
                logger.info("Created Stripe Express account {} for seller {}", accountId, seller.id)
                accountId
            } else {
                logger.info("Using existing Stripe account {} for seller {}", seller.stripeAccountId, seller.id)
                seller.stripeAccountId
            }

            // Create account link for onboarding
            val refreshUrl = "$baseUrl/seller/connect/refresh"
            val returnUrl = "$baseUrl/seller/connect/return"

            val accountLinkUrl = sellerPaymentGateway.createAccountLink(
                stripeAccountId = stripeAccountId!!,
                refreshUrl = refreshUrl,
                returnUrl = returnUrl,
            )

            logger.info("Created account link for seller {}", seller.id)

            ResponseEntity.ok(
                OnboardingStartResponse(
                    stripeAccountId = stripeAccountId,
                    onboardingUrl = accountLinkUrl,
                )
            )
        } catch (e: StripeException) {
            logger.error("Stripe error during onboarding for seller {}: {}", seller.id, e.message, e)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(OnboardingStartResponse("", "", error = "Payment provider error: ${e.message}"))
        } catch (e: Exception) {
            logger.error("Unexpected error during onboarding for seller {}: {}", seller.id, e.message, e)
            ResponseEntity.internalServerError()
                .body(OnboardingStartResponse("", "", error = "Internal error. Please try again later."))
        }
    }

    /**
     * Get current Stripe Connect status.
     */
    @GetMapping("/status")
    fun getStatus(authentication: Authentication): ResponseEntity<ConnectStatusResponse> {
        val userId = getUserId(authentication.principal)
        val seller = sellerRepository.findByUserId(userId)
            ?: return ResponseEntity.notFound().build()

        val accountId = seller.stripeAccountId
        if (accountId == null) {
            return ResponseEntity.ok(
                ConnectStatusResponse(
                    hasStripeAccount = false,
                    stripeAccountId = null,
                    chargesEnabled = false,
                    payoutsEnabled = false,
                    onboardingCompleted = false,
                    canReceivePayouts = false,
                    dashboardUrl = null,
                    error = null,
                )
            )
        }

        return try {
            // Fetch fresh status from Stripe
            val status = sellerPaymentGateway.getAccountStatus(accountId)

            // Update seller if status changed
            if (status.chargesEnabled != seller.stripeChargesEnabled ||
                status.payoutsEnabled != seller.stripePayoutsEnabled ||
                status.detailsSubmitted != seller.stripeOnboardingCompleted
            ) {
                val updatedSeller = seller.withStripeStatus(
                    chargesEnabled = status.chargesEnabled,
                    payoutsEnabled = status.payoutsEnabled,
                    onboardingCompleted = status.detailsSubmitted,
                )
                sellerRepository.save(updatedSeller)
                logger.info(
                    "Updated seller {} Stripe status: charges={}, payouts={}, completed={}",
                    seller.id, status.chargesEnabled, status.payoutsEnabled, status.detailsSubmitted
                )
            }

            // Generate dashboard link if onboarding is complete
            val dashboardUrl = if (status.detailsSubmitted) {
                try {
                    sellerPaymentGateway.createLoginLink(accountId)
                } catch (e: Exception) {
                    logger.warn("Could not create login link for seller {}: {}", seller.id, e.message)
                    null
                }
            } else {
                null
            }

            ResponseEntity.ok(
                ConnectStatusResponse(
                    hasStripeAccount = true,
                    stripeAccountId = accountId,
                    chargesEnabled = status.chargesEnabled,
                    payoutsEnabled = status.payoutsEnabled,
                    onboardingCompleted = status.detailsSubmitted,
                    canReceivePayouts = status.chargesEnabled && status.payoutsEnabled,
                    dashboardUrl = dashboardUrl,
                    error = null,
                )
            )
        } catch (e: StripeException) {
            logger.error("Stripe error fetching status for seller {}: {}", seller.id, e.message, e)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(
                    ConnectStatusResponse(
                        hasStripeAccount = true,
                        stripeAccountId = accountId,
                        chargesEnabled = seller.stripeChargesEnabled,
                        payoutsEnabled = seller.stripePayoutsEnabled,
                        onboardingCompleted = seller.stripeOnboardingCompleted,
                        canReceivePayouts = seller.canReceivePayouts(),
                        dashboardUrl = null,
                        error = "Could not fetch latest status from payment provider",
                    )
                )
        }
    }

    /**
     * Generate a Stripe Express Dashboard login link.
     */
    @PostMapping("/dashboard")
    fun openStripeDashboard(authentication: Authentication): ResponseEntity<DashboardLinkResponse> {
        val userId = getUserId(authentication.principal)
        val seller = sellerRepository.findByUserId(userId)
            ?: return ResponseEntity.notFound().build()

        val stripeAccountId = seller.stripeAccountId
            ?: return ResponseEntity.badRequest().body(
                DashboardLinkResponse(url = null, error = "No Stripe account connected")
            )

        return try {
            val loginUrl = sellerPaymentGateway.createLoginLink(stripeAccountId)
            ResponseEntity.ok(DashboardLinkResponse(url = loginUrl, error = null))
        } catch (e: Exception) {
            logger.error("Failed to create login link for seller {}: {}", seller.id, e.message)
            ResponseEntity.badRequest().body(
                DashboardLinkResponse(url = null, error = "Could not create dashboard link")
            )
        }
    }

    /**
     * Generate a new onboarding link (for retry/refresh).
     */
    @PostMapping("/refresh-link")
    fun refreshOnboardingLink(authentication: Authentication): ResponseEntity<OnboardingStartResponse> {
        val userId = getUserId(authentication.principal)
        val seller = sellerRepository.findByUserId(userId)
            ?: return ResponseEntity.notFound().build()

        val stripeAccountId = seller.stripeAccountId
            ?: return ResponseEntity.badRequest()
                .body(OnboardingStartResponse("", "", error = "No Stripe account connected. Please start onboarding first."))

        return try {
            val refreshUrl = "$baseUrl/seller/connect/refresh"
            val returnUrl = "$baseUrl/seller/connect/return"

            val accountLinkUrl = sellerPaymentGateway.createAccountLink(
                stripeAccountId = stripeAccountId,
                refreshUrl = refreshUrl,
                returnUrl = returnUrl,
            )

            logger.info("Created refresh account link for seller {}", seller.id)

            ResponseEntity.ok(
                OnboardingStartResponse(
                    stripeAccountId = stripeAccountId,
                    onboardingUrl = accountLinkUrl,
                )
            )
        } catch (e: StripeException) {
            logger.error("Stripe error refreshing link for seller {}: {}", seller.id, e.message, e)
            ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(OnboardingStartResponse("", "", error = "Payment provider error: ${e.message}"))
        } catch (e: Exception) {
            logger.error("Unexpected error refreshing link for seller {}: {}", seller.id, e.message, e)
            ResponseEntity.internalServerError()
                .body(OnboardingStartResponse("", "", error = "Internal error. Please try again later."))
        }
    }

    private fun getUserId(principal: Any?): String =
        when (principal) {
            is Jwt -> principal.subject
            is OAuth2User -> principal.name
            else -> throw IllegalStateException("Unknown principal type: ${principal?.javaClass}")
        }

    // DTOs
    data class OnboardingStartResponse(
        val stripeAccountId: String,
        val onboardingUrl: String,
        val url: String = onboardingUrl, // Alias for frontend compatibility
        val error: String? = null,
    )

    data class DashboardLinkResponse(
        val url: String?,
        val error: String?,
    )

    data class ConnectStatusResponse(
        val hasStripeAccount: Boolean,
        val stripeAccountId: String?,
        val chargesEnabled: Boolean,
        val payoutsEnabled: Boolean,
        val onboardingCompleted: Boolean,
        val canReceivePayouts: Boolean,
        val dashboardUrl: String?,
        val error: String?,
    )
}
