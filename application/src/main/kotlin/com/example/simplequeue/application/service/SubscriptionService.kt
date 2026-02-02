package com.example.simplequeue.application.service

import com.example.simplequeue.domain.model.Subscription
import com.example.simplequeue.domain.model.SubscriptionTier
import com.example.simplequeue.domain.model.TierLimit
import com.example.simplequeue.domain.port.QueueMemberRepository
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.SellerReferralRepository
import com.example.simplequeue.domain.port.SubscriptionRepository
import com.example.simplequeue.domain.port.TierLimitRepository
import com.example.simplequeue.domain.port.WebhookResult
import java.time.Instant
import java.util.UUID

/**
 * Backward-compatible wrapper around TierLimit for existing code.
 * @deprecated Use TierLimit directly instead
 */
data class SubscriptionLimits(
    val tier: SubscriptionTier,
    val maxQueues: Int,
    val maxOperatorsPerQueue: Int,
    val maxTicketsPerDay: Int,
    val canUseEmailNotifications: Boolean,
    val canUseCustomBranding: Boolean,
) {
    companion object {
        /**
         * Convert TierLimit to legacy SubscriptionLimits format.
         */
        fun fromTierLimit(limit: TierLimit): SubscriptionLimits = SubscriptionLimits(
            tier = limit.tier,
            maxQueues = if (limit.isUnlimited(limit.maxQueues)) Int.MAX_VALUE else limit.maxQueues,
            maxOperatorsPerQueue = if (limit.isUnlimited(limit.maxOperatorsPerQueue)) Int.MAX_VALUE else limit.maxOperatorsPerQueue,
            maxTicketsPerDay = if (limit.isUnlimited(limit.maxTicketsPerDay)) Int.MAX_VALUE else limit.maxTicketsPerDay,
            canUseEmailNotifications = limit.canUseEmailNotifications,
            canUseCustomBranding = limit.canUseCustomBranding,
        )

        /**
         * Get limits for a tier using hardcoded defaults (fallback).
         * @deprecated Use TierLimitRepository instead
         */
        fun forTier(tier: SubscriptionTier): SubscriptionLimits =
            fromTierLimit(TierLimit.defaultForTier(tier))
    }
}

/**
 * Service for managing user subscriptions and checking feature limits.
 * 
 * Note: In Management Platform, queue-related methods are not available.
 * Use Queue Core service for queue quota checks.
 */
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val queueRepository: QueueRepository? = null, // Optional - only needed in Queue Core
    private val queueMemberRepository: QueueMemberRepository? = null, // Optional - only needed in Queue Core
    private val tierLimitRepository: TierLimitRepository? = null, // Optional for backward compatibility
    private val sellerReferralRepository: SellerReferralRepository? = null, // Optional for backward compatibility
) {
    /**
     * Get the subscription for a user, creating a FREE one if none exists.
     */
    fun getOrCreateSubscription(userId: String): Subscription {
        val existing = subscriptionRepository.findByUserId(userId)
        if (existing != null) {
            return existing
        }

        val newSubscription = Subscription.createFree(userId)
        subscriptionRepository.save(newSubscription)
        return newSubscription
    }

    /**
     * Get the subscription tier for a user.
     */
    fun getTier(userId: String): SubscriptionTier {
        val subscription = getOrCreateSubscription(userId)
        return if (subscription.isActive()) subscription.tier else SubscriptionTier.FREE
    }

    /**
     * Get the tier limits from database or fallback to defaults.
     */
    fun getTierLimit(tier: SubscriptionTier): TierLimit {
        return tierLimitRepository?.findByTier(tier)
            ?: TierLimit.defaultForTier(tier)
    }

    /**
     * Get the tier limits for a user based on their subscription tier.
     */
    fun getTierLimitForUser(userId: String): TierLimit {
        val tier = getTier(userId)
        return getTierLimit(tier)
    }

    /**
     * Get all tier limits (for admin UI).
     */
    fun getAllTierLimits(): List<TierLimit> {
        return tierLimitRepository?.findAll()
            ?: SubscriptionTier.entries.map { TierLimit.defaultForTier(it) }
    }

    /**
     * Update tier limits (superadmin only).
     */
    fun updateTierLimit(limit: TierLimit) {
        tierLimitRepository?.save(limit)
            ?: throw IllegalStateException("TierLimitRepository not configured")
    }

    /**
     * Get the limits for a user based on their subscription tier.
     * @deprecated Use getTierLimitForUser() instead
     */
    fun getLimits(userId: String): SubscriptionLimits {
        val tierLimit = getTierLimitForUser(userId)
        return SubscriptionLimits.fromTierLimit(tierLimit)
    }

    /**
     * Check if a user can create a new queue based on their subscription limits.
     * Note: Only available when queueRepository is configured (Queue Core service).
     */
    fun canCreateQueue(userId: String): Boolean {
        val repo = queueRepository
            ?: throw IllegalStateException("Queue operations not available in Management Platform. Use Queue Core service.")
        val limit = getTierLimitForUser(userId)
        val currentQueues = repo.findByOwnerId(userId).size
        return limit.canCreateQueue(currentQueues)
    }

    /**
     * Check if a user can invite another operator to a specific queue.
     * Note: Only available when queueMemberRepository is configured (Queue Core service).
     */
    fun canInviteOperator(userId: String, queueId: UUID): Boolean {
        val repo = queueMemberRepository
            ?: throw IllegalStateException("Queue operations not available in Management Platform. Use Queue Core service.")
        val limit = getTierLimitForUser(userId)
        val currentMembers = repo.countByQueueId(queueId)
        return limit.canAddOperator(currentMembers)
    }

    /**
     * Get the number of queues a user currently owns.
     * Note: Only available when queueRepository is configured (Queue Core service).
     */
    fun getQueueCount(userId: String): Int {
        val repo = queueRepository
            ?: throw IllegalStateException("Queue operations not available in Management Platform. Use Queue Core service.")
        return repo.findByOwnerId(userId).size
    }

    /**
     * Get the number of operators for a specific queue.
     * Note: Only available when queueMemberRepository is configured (Queue Core service).
     */
    fun getOperatorCount(queueId: UUID): Int {
        val repo = queueMemberRepository
            ?: throw IllegalStateException("Queue operations not available in Management Platform. Use Queue Core service.")
        return repo.countByQueueId(queueId)
    }

    /**
     * Check if a user can add a new counter to a specific queue.
     */
    fun canAddCounter(userId: String, currentCounterCount: Int): Boolean {
        val limit = getTierLimitForUser(userId)
        return limit.canAddCounter(currentCounterCount)
    }

    /**
     * Find subscription by Stripe customer ID.
     */
    fun findByStripeCustomerId(stripeCustomerId: String): Subscription? {
        return subscriptionRepository.findByStripeCustomerId(stripeCustomerId)
    }

    /**
     * Process a subscription created event from Stripe.
     */
    fun handleSubscriptionCreated(result: WebhookResult.SubscriptionCreated) {
        // Try to find existing subscription by userId from metadata, or create new
        val userId = result.userId
            ?: throw IllegalStateException("No user_id in subscription metadata")

        val subscription = subscriptionRepository.findByUserId(userId)
            ?: Subscription.createFree(userId)

        subscription.tier = result.tier
        subscription.status = Subscription.SubscriptionStatus.ACTIVE
        subscription.stripeCustomerId = result.stripeCustomerId
        subscription.stripeSubscriptionId = result.stripeSubscriptionId
        subscription.currentPeriodStart = result.currentPeriodStart
        subscription.currentPeriodEnd = result.currentPeriodEnd
        subscription.updatedAt = Instant.now()

        subscriptionRepository.save(subscription)

        // Link subscription to existing referral if present
        linkReferralToSubscription(userId, subscription.id)
    }

    /**
     * Link a subscription to an existing seller referral for the user.
     * This enables commission tracking for payments on this subscription.
     */
    private fun linkReferralToSubscription(userId: String, subscriptionId: UUID) {
        sellerReferralRepository?.findByCustomerUserId(userId)?.let { referral ->
            if (referral.subscriptionId == null) {
                val linkedReferral = referral.copy(subscriptionId = subscriptionId)
                sellerReferralRepository.save(linkedReferral)
            }
        }
    }

    /**
     * Process a subscription updated event from Stripe.
     */
    fun handleSubscriptionUpdated(result: WebhookResult.SubscriptionUpdated) {
        val subscription = subscriptionRepository.findByStripeCustomerId(result.stripeCustomerId)
            ?: throw IllegalStateException("No subscription found for Stripe customer: ${result.stripeCustomerId}")

        subscription.tier = result.tier
        subscription.status = result.status
        subscription.currentPeriodStart = result.currentPeriodStart
        subscription.currentPeriodEnd = result.currentPeriodEnd
        subscription.cancelAtPeriodEnd = result.cancelAtPeriodEnd
        subscription.updatedAt = Instant.now()

        subscriptionRepository.save(subscription)
    }

    /**
     * Process a subscription deleted event from Stripe.
     */
    fun handleSubscriptionDeleted(result: WebhookResult.SubscriptionDeleted) {
        val subscription = subscriptionRepository.findByStripeCustomerId(result.stripeCustomerId)
            ?: return // Already gone, nothing to do

        // Downgrade to free tier
        subscription.tier = SubscriptionTier.FREE
        subscription.status = Subscription.SubscriptionStatus.CANCELLED
        subscription.stripeSubscriptionId = null
        subscription.cancelAtPeriodEnd = false
        subscription.updatedAt = Instant.now()

        subscriptionRepository.save(subscription)
    }

    /**
     * Process a payment failed event from Stripe.
     */
    fun handlePaymentFailed(result: WebhookResult.PaymentFailed) {
        val subscription = subscriptionRepository.findByStripeCustomerId(result.stripeCustomerId)
            ?: return // No subscription to update

        subscription.status = Subscription.SubscriptionStatus.PAST_DUE
        subscription.updatedAt = Instant.now()

        subscriptionRepository.save(subscription)
    }
}
