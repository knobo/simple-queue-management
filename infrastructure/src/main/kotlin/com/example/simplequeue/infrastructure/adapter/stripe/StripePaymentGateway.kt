package com.example.simplequeue.infrastructure.adapter.stripe

import com.example.simplequeue.domain.model.Subscription
import com.example.simplequeue.domain.model.SubscriptionTier
import com.example.simplequeue.domain.port.PaymentGateway
import com.example.simplequeue.domain.port.WebhookResult
import com.stripe.Stripe
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.model.Invoice
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.billingportal.SessionCreateParams as PortalSessionCreateParams
import com.stripe.param.checkout.SessionCreateParams
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class StripePaymentGateway(
    private val config: StripeConfig,
) : PaymentGateway {

    private val logger = LoggerFactory.getLogger(StripePaymentGateway::class.java)

    @PostConstruct
    fun init() {
        Stripe.apiKey = config.secretKey
        logger.info("Stripe API initialized")
    }

    override fun createCheckoutSession(
        userId: String,
        tier: SubscriptionTier,
        successUrl: String,
        cancelUrl: String,
        customerEmail: String?,
    ): String {
        val priceId = getPriceId(tier)
            ?: throw IllegalArgumentException("No Stripe price configured for tier: $tier")

        val paramsBuilder = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build()
            )
            .putMetadata("user_id", userId)
            .setSubscriptionData(
                SessionCreateParams.SubscriptionData.builder()
                    .putMetadata("user_id", userId)
                    .putMetadata("tier", tier.name)
                    .build()
            )

        if (customerEmail != null) {
            paramsBuilder.setCustomerEmail(customerEmail)
        }

        val session = Session.create(paramsBuilder.build())
        logger.info("Created checkout session {} for user {} tier {}", session.id, userId, tier)
        return session.url
    }

    override fun createCustomerPortalSession(
        stripeCustomerId: String,
        returnUrl: String,
    ): String {
        val params = PortalSessionCreateParams.builder()
            .setCustomer(stripeCustomerId)
            .setReturnUrl(returnUrl)
            .build()

        val session = com.stripe.model.billingportal.Session.create(params)
        logger.info("Created customer portal session for customer {}", stripeCustomerId)
        return session.url
    }

    override fun handleWebhookEvent(
        payload: String,
        signature: String,
    ): WebhookResult {
        val event: Event = try {
            Webhook.constructEvent(payload, signature, config.webhookSecret)
        } catch (e: SignatureVerificationException) {
            logger.error("Invalid webhook signature", e)
            throw IllegalArgumentException("Invalid webhook signature", e)
        }

        logger.info("Processing webhook event: {} ({})", event.type, event.id)

        return when (event.type) {
            "checkout.session.completed" -> handleCheckoutCompleted(event)
            "customer.subscription.updated" -> handleSubscriptionUpdated(event)
            "customer.subscription.deleted" -> handleSubscriptionDeleted(event)
            "invoice.payment_failed" -> handlePaymentFailed(event)
            "invoice.paid" -> handleInvoicePaid(event)
            else -> {
                logger.debug("Ignoring event type: {}", event.type)
                WebhookResult.Ignored
            }
        }
    }

    private fun handleCheckoutCompleted(event: Event): WebhookResult {
        val deserializer = event.dataObjectDeserializer
        val session: Session = if (deserializer.`object`.isPresent) {
            deserializer.`object`.get() as Session
        } else {
            // Fallback: retrieve session from Stripe API using ID from raw JSON
            logger.warn("Using fallback: retrieving checkout session from API")
            val rawJson = deserializer.rawJson
            val sessionId = rawJson?.let { 
                // Extract session ID from raw JSON
                Regex(""""id"\s*:\s*"(cs_[^"]+)"""").find(it)?.groupValues?.get(1)
            } ?: throw IllegalStateException("Could not extract session ID from webhook")
            Session.retrieve(sessionId)
        }

        val customerId = session.customer
        val subscriptionId = session.subscription
        val userId = session.metadata?.get("user_id")

        if (subscriptionId == null) {
            logger.warn("Checkout session {} has no subscription ID", session.id)
            return WebhookResult.Ignored
        }

        // Fetch the subscription to get tier and period info
        val subscription = com.stripe.model.Subscription.retrieve(subscriptionId)
        val tierFromMetadata = subscription.metadata?.get("tier")
        val tier = tierFromMetadata?.let { 
            try { SubscriptionTier.valueOf(it) } catch (e: Exception) { null }
        } ?: determineTierFromPriceId(subscription.items.data.firstOrNull()?.price?.id)

        logger.info(
            "Checkout completed: customer={}, subscription={}, user={}, tier={}",
            customerId, subscriptionId, userId, tier
        )

        return WebhookResult.SubscriptionCreated(
            stripeCustomerId = customerId,
            stripeSubscriptionId = subscriptionId,
            userId = userId,
            tier = tier,
            currentPeriodStart = Instant.ofEpochSecond(subscription.currentPeriodStart),
            currentPeriodEnd = Instant.ofEpochSecond(subscription.currentPeriodEnd),
        )
    }

    private fun handleSubscriptionUpdated(event: Event): WebhookResult {
        val subscription = event.dataObjectDeserializer.`object`.orElse(null) 
            as? com.stripe.model.Subscription
            ?: throw IllegalStateException("Could not deserialize subscription")

        val tier = determineTierFromPriceId(subscription.items.data.firstOrNull()?.price?.id)
        val status = mapStripeStatus(subscription.status)

        logger.info(
            "Subscription updated: id={}, customer={}, tier={}, status={}",
            subscription.id, subscription.customer, tier, status
        )

        return WebhookResult.SubscriptionUpdated(
            stripeCustomerId = subscription.customer,
            stripeSubscriptionId = subscription.id,
            tier = tier,
            status = status,
            currentPeriodStart = Instant.ofEpochSecond(subscription.currentPeriodStart),
            currentPeriodEnd = Instant.ofEpochSecond(subscription.currentPeriodEnd),
            cancelAtPeriodEnd = subscription.cancelAtPeriodEnd ?: false,
        )
    }

    private fun handleSubscriptionDeleted(event: Event): WebhookResult {
        val subscription = event.dataObjectDeserializer.`object`.orElse(null) 
            as? com.stripe.model.Subscription
            ?: throw IllegalStateException("Could not deserialize subscription")

        logger.info(
            "Subscription deleted: id={}, customer={}",
            subscription.id, subscription.customer
        )

        return WebhookResult.SubscriptionDeleted(
            stripeCustomerId = subscription.customer,
            stripeSubscriptionId = subscription.id,
        )
    }

    private fun handlePaymentFailed(event: Event): WebhookResult {
        val invoice = event.dataObjectDeserializer.`object`.orElse(null) as? Invoice
            ?: throw IllegalStateException("Could not deserialize invoice")

        logger.warn(
            "Payment failed: invoice={}, customer={}, subscription={}",
            invoice.id, invoice.customer, invoice.subscription
        )

        return WebhookResult.PaymentFailed(
            stripeCustomerId = invoice.customer,
            stripeSubscriptionId = invoice.subscription,
        )
    }

    private fun handleInvoicePaid(event: Event): WebhookResult {
        val invoice = event.dataObjectDeserializer.`object`.orElse(null) as? Invoice
            ?: throw IllegalStateException("Could not deserialize invoice")

        // Only process subscription invoices
        val subscriptionId = invoice.subscription
        if (subscriptionId == null) {
            logger.debug("Invoice {} has no subscription, ignoring", invoice.id)
            return WebhookResult.Ignored
        }

        // Get the amount in decimal (Stripe uses cents)
        val amount = java.math.BigDecimal(invoice.amountPaid)
            .divide(java.math.BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)

        // Skip zero-amount invoices (e.g., trials, prorations)
        if (amount <= java.math.BigDecimal.ZERO) {
            logger.debug("Invoice {} has zero amount, ignoring", invoice.id)
            return WebhookResult.Ignored
        }

        logger.info(
            "Invoice paid: invoice={}, customer={}, subscription={}, amount={}",
            invoice.id, invoice.customer, subscriptionId, amount
        )

        return WebhookResult.InvoicePaid(
            invoiceId = invoice.id,
            stripeSubscriptionId = subscriptionId,
            stripeCustomerId = invoice.customer,
            amount = amount,
            periodStart = Instant.ofEpochSecond(invoice.periodStart),
            periodEnd = Instant.ofEpochSecond(invoice.periodEnd),
        )
    }

    private fun getPriceId(tier: SubscriptionTier): String? =
        when (tier) {
            SubscriptionTier.FREE -> null
            SubscriptionTier.STARTER -> config.prices.pro // STARTER no longer sold, map to PRO
            SubscriptionTier.PRO -> config.prices.pro
            SubscriptionTier.ENTERPRISE -> config.prices.enterprise
        }

    private fun determineTierFromPriceId(priceId: String?): SubscriptionTier =
        when (priceId) {
            config.prices.pro -> SubscriptionTier.PRO
            config.prices.enterprise -> SubscriptionTier.ENTERPRISE
            else -> SubscriptionTier.FREE
        }

    private fun mapStripeStatus(status: String?): Subscription.SubscriptionStatus =
        when (status) {
            "active" -> Subscription.SubscriptionStatus.ACTIVE
            "past_due" -> Subscription.SubscriptionStatus.PAST_DUE
            "canceled", "cancelled" -> Subscription.SubscriptionStatus.CANCELLED
            "trialing" -> Subscription.SubscriptionStatus.TRIALING
            "unpaid" -> Subscription.SubscriptionStatus.PAST_DUE
            else -> Subscription.SubscriptionStatus.ACTIVE
        }
}
