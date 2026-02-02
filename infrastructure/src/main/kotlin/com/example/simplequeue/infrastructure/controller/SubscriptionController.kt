package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.service.SubscriptionLimits
import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.domain.model.Subscription
import com.example.simplequeue.domain.model.SubscriptionTier
import com.example.simplequeue.domain.port.PaymentGateway
import com.example.simplequeue.infrastructure.adapter.stripe.StripeConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/subscription")
class SubscriptionController(
    private val subscriptionService: SubscriptionService,
    private val paymentGateway: PaymentGateway,
    private val stripeConfig: StripeConfig,
    @Value("\${app.base-url}") private val baseUrl: String,
) {
    /**
     * Get the current user's subscription details.
     */
    @GetMapping
    fun getMySubscription(authentication: Authentication): SubscriptionDTO {
        val userId = getUserId(authentication.principal)
        val subscription = subscriptionService.getOrCreateSubscription(userId)
        return SubscriptionDTO.from(subscription)
    }

    /**
     * Get the current user's subscription limits and usage.
     */
    @GetMapping("/limits")
    fun getMyLimits(authentication: Authentication): LimitsDTO {
        val userId = getUserId(authentication.principal)
        val limits = subscriptionService.getLimits(userId)
        val queueCount = subscriptionService.getQueueCount(userId)
        return LimitsDTO.from(limits, queueCount)
    }

    /**
     * Create a Stripe Checkout session for subscribing to a tier.
     * Returns the checkout URL to redirect the user to.
     */
    @PostMapping("/checkout")
    fun createCheckoutSession(
        authentication: Authentication,
        @RequestBody request: CheckoutRequest,
    ): ResponseEntity<CheckoutResponse> {
        val userId = getUserId(authentication.principal)
        val email = getUserEmail(authentication.principal)

        val tier = try {
            SubscriptionTier.valueOf(request.tier.uppercase())
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(
                CheckoutResponse(
                    success = false,
                    url = null,
                    message = "Invalid subscription tier: ${request.tier}",
                )
            )
        }

        if (tier == SubscriptionTier.FREE) {
            return ResponseEntity.badRequest().body(
                CheckoutResponse(
                    success = false,
                    url = null,
                    message = "Cannot checkout for FREE tier",
                )
            )
        }

        val successUrl = request.successUrl ?: "$baseUrl/subscription/success?session_id={CHECKOUT_SESSION_ID}"
        val cancelUrl = request.cancelUrl ?: "$baseUrl/subscription/cancel"

        return try {
            val checkoutUrl = paymentGateway.createCheckoutSession(
                userId = userId,
                tier = tier,
                successUrl = successUrl,
                cancelUrl = cancelUrl,
                customerEmail = email,
            )
            ResponseEntity.ok(
                CheckoutResponse(
                    success = true,
                    url = checkoutUrl,
                    message = null,
                )
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                CheckoutResponse(
                    success = false,
                    url = null,
                    message = "Failed to create checkout session: ${e.message}",
                )
            )
        }
    }

    /**
     * Create a Stripe Customer Portal session for managing subscription.
     * Returns the portal URL to redirect the user to.
     */
    @PostMapping("/portal")
    fun createPortalSession(
        authentication: Authentication,
        @RequestBody(required = false) request: PortalRequest?,
    ): ResponseEntity<PortalResponse> {
        val userId = getUserId(authentication.principal)
        val subscription = subscriptionService.getOrCreateSubscription(userId)

        val stripeCustomerId = subscription.stripeCustomerId
            ?: return ResponseEntity.badRequest().body(
                PortalResponse(
                    success = false,
                    url = null,
                    message = "No active Stripe subscription found",
                )
            )

        val returnUrl = request?.returnUrl ?: "$baseUrl/subscription"

        return try {
            val portalUrl = paymentGateway.createCustomerPortalSession(
                stripeCustomerId = stripeCustomerId,
                returnUrl = returnUrl,
            )
            ResponseEntity.ok(
                PortalResponse(
                    success = true,
                    url = portalUrl,
                    message = null,
                )
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                PortalResponse(
                    success = false,
                    url = null,
                    message = "Failed to create portal session: ${e.message}",
                )
            )
        }
    }

    /**
     * Get the Stripe publishable key for frontend use.
     */
    @GetMapping("/stripe-key")
    fun getStripePublishableKey(): Map<String, String> {
        return mapOf("publishableKey" to stripeConfig.publishableKey)
    }

    data class CheckoutRequest(
        val tier: String,
        val successUrl: String? = null,
        val cancelUrl: String? = null,
    )

    data class CheckoutResponse(
        val success: Boolean,
        val url: String?,
        val message: String?,
    )

    data class PortalRequest(
        val returnUrl: String? = null,
    )

    data class PortalResponse(
        val success: Boolean,
        val url: String?,
        val message: String?,
    )

    private fun getUserId(principal: Any?): String =
        when (principal) {
            is Jwt -> principal.subject
            is OAuth2User -> principal.name
            else -> throw IllegalStateException("Unknown principal type: ${principal?.javaClass}")
        }

    private fun getUserEmail(principal: Any?): String? =
        when (principal) {
            is Jwt -> principal.getClaimAsString("email")
            is OAuth2User -> principal.getAttribute("email")
            else -> null
        }

    /**
     * DTO for subscription information.
     */
    data class SubscriptionDTO(
        val tier: SubscriptionTier,
        val status: String,
        val isActive: Boolean,
        val isPaid: Boolean,
        val currentPeriodEnd: Instant,
        val cancelAtPeriodEnd: Boolean,
    ) {
        companion object {
            fun from(subscription: Subscription): SubscriptionDTO =
                SubscriptionDTO(
                    tier = subscription.tier,
                    status = subscription.status.name,
                    isActive = subscription.isActive(),
                    isPaid = subscription.isPaid(),
                    currentPeriodEnd = subscription.currentPeriodEnd,
                    cancelAtPeriodEnd = subscription.cancelAtPeriodEnd,
                )
        }
    }

    /**
     * DTO for subscription limits and current usage.
     */
    data class LimitsDTO(
        val tier: SubscriptionTier,
        val maxQueues: Int,
        val currentQueues: Int,
        val maxOperatorsPerQueue: Int,
        val maxTicketsPerDay: Int,
        val canUseEmailNotifications: Boolean,
        val canUseCustomBranding: Boolean,
        val canCreateMoreQueues: Boolean,
    ) {
        companion object {
            fun from(limits: SubscriptionLimits, currentQueues: Int): LimitsDTO =
                LimitsDTO(
                    tier = limits.tier,
                    maxQueues = limits.maxQueues,
                    currentQueues = currentQueues,
                    maxOperatorsPerQueue = limits.maxOperatorsPerQueue,
                    maxTicketsPerDay = limits.maxTicketsPerDay,
                    canUseEmailNotifications = limits.canUseEmailNotifications,
                    canUseCustomBranding = limits.canUseCustomBranding,
                    canCreateMoreQueues = currentQueues < limits.maxQueues,
                )
        }
    }
}
