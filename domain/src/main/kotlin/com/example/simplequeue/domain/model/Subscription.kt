package com.example.simplequeue.domain.model

import java.time.Instant
import java.util.UUID

data class Subscription(
    val id: UUID,
    val userId: String,
    var tier: SubscriptionTier,
    var status: SubscriptionStatus,
    var stripeCustomerId: String?,
    var stripeSubscriptionId: String?,
    var currentPeriodStart: Instant,
    var currentPeriodEnd: Instant,
    var cancelAtPeriodEnd: Boolean,
    val createdAt: Instant,
    var updatedAt: Instant,
) {
    enum class SubscriptionStatus {
        ACTIVE,
        PAST_DUE,
        CANCELLED,
        TRIALING,
    }

    companion object {
        /**
         * Create a new FREE subscription for a user.
         */
        fun createFree(userId: String): Subscription {
            val now = Instant.now()
            // Free subscriptions don't have a real period, set to far future
            val farFuture = now.plusSeconds(365L * 24 * 60 * 60 * 100) // 100 years
            return Subscription(
                id = UUID.randomUUID(),
                userId = userId,
                tier = SubscriptionTier.FREE,
                status = SubscriptionStatus.ACTIVE,
                stripeCustomerId = null,
                stripeSubscriptionId = null,
                currentPeriodStart = now,
                currentPeriodEnd = farFuture,
                cancelAtPeriodEnd = false,
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    fun isActive(): Boolean =
        status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING

    fun isPaid(): Boolean = tier != SubscriptionTier.FREE
}
