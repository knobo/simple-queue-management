package com.example.simplequeue.domain.model

import java.time.Instant

/**
 * Configurable limits for a subscription tier.
 * These limits are stored in the database and can be adjusted by superadmin.
 */
data class TierLimit(
    val tier: SubscriptionTier,
    val maxQueues: Int,
    val maxOperatorsPerQueue: Int,
    val maxTicketsPerDay: Int,
    val maxActiveTickets: Int,
    val maxInvitesPerMonth: Int,
    val maxCountersPerQueue: Int,
    val canUseEmailNotifications: Boolean,
    val canUseCustomBranding: Boolean,
    val canUseAnalytics: Boolean,
    val canUseApiAccess: Boolean,
    val updatedAt: Instant,
    val updatedBy: String?,
) {
    /**
     * Check if a numeric limit is unlimited (-1).
     */
    fun isUnlimited(value: Int): Boolean = value == UNLIMITED

    /**
     * Check if user can create more queues.
     */
    fun canCreateQueue(currentCount: Int): Boolean =
        isUnlimited(maxQueues) || currentCount < maxQueues

    /**
     * Check if user can add more operators to a queue.
     */
    fun canAddOperator(currentCount: Int): Boolean =
        isUnlimited(maxOperatorsPerQueue) || currentCount < maxOperatorsPerQueue

    /**
     * Check if user can issue more tickets today.
     */
    fun canIssueTicket(ticketsIssuedToday: Int): Boolean =
        isUnlimited(maxTicketsPerDay) || ticketsIssuedToday < maxTicketsPerDay

    /**
     * Check if user can have more active tickets.
     */
    fun canHaveActiveTicket(activeTicketCount: Int): Boolean =
        isUnlimited(maxActiveTickets) || activeTicketCount < maxActiveTickets

    /**
     * Check if user can send more invites this month.
     */
    fun canSendInvite(invitesSentThisMonth: Int): Boolean =
        isUnlimited(maxInvitesPerMonth) || invitesSentThisMonth < maxInvitesPerMonth

    /**
     * Check if user can add more counters to a queue.
     */
    fun canAddCounter(currentCount: Int): Boolean =
        isUnlimited(maxCountersPerQueue) || currentCount < maxCountersPerQueue

    /**
     * Get remaining quota for a limit, or null if unlimited.
     */
    fun getRemainingQueues(currentCount: Int): Int? =
        if (isUnlimited(maxQueues)) null else maxQueues - currentCount

    fun getRemainingOperators(currentCount: Int): Int? =
        if (isUnlimited(maxOperatorsPerQueue)) null else maxOperatorsPerQueue - currentCount

    fun getRemainingTicketsToday(ticketsIssuedToday: Int): Int? =
        if (isUnlimited(maxTicketsPerDay)) null else maxTicketsPerDay - ticketsIssuedToday

    fun getRemainingInvites(invitesSentThisMonth: Int): Int? =
        if (isUnlimited(maxInvitesPerMonth)) null else maxInvitesPerMonth - invitesSentThisMonth

    fun getRemainingCounters(currentCount: Int): Int? =
        if (isUnlimited(maxCountersPerQueue)) null else maxCountersPerQueue - currentCount

    companion object {
        const val UNLIMITED = -1

        /**
         * Create default limits for a tier (used as fallback if DB is empty).
         */
        fun defaultForTier(tier: SubscriptionTier): TierLimit {
            val now = Instant.now()
            return when (tier) {
                SubscriptionTier.FREE -> TierLimit(
                    tier = tier,
                    maxQueues = 1,
                    maxOperatorsPerQueue = 0,
                    maxTicketsPerDay = 50,
                    maxActiveTickets = 100,
                    maxInvitesPerMonth = 5,
                    maxCountersPerQueue = 1,
                    canUseEmailNotifications = false,
                    canUseCustomBranding = false,
                    canUseAnalytics = false,
                    canUseApiAccess = false,
                    updatedAt = now,
                    updatedBy = "system-default",
                )
                SubscriptionTier.STARTER -> TierLimit(
                    tier = tier,
                    maxQueues = 3,
                    maxOperatorsPerQueue = 2,
                    maxTicketsPerDay = 200,
                    maxActiveTickets = 500,
                    maxInvitesPerMonth = 20,
                    maxCountersPerQueue = 3,
                    canUseEmailNotifications = true,
                    canUseCustomBranding = false,
                    canUseAnalytics = false,
                    canUseApiAccess = false,
                    updatedAt = now,
                    updatedBy = "system-default",
                )
                SubscriptionTier.PRO -> TierLimit(
                    tier = tier,
                    maxQueues = 10,
                    maxOperatorsPerQueue = 10,
                    maxTicketsPerDay = UNLIMITED,
                    maxActiveTickets = UNLIMITED,
                    maxInvitesPerMonth = UNLIMITED,
                    maxCountersPerQueue = 10,
                    canUseEmailNotifications = true,
                    canUseCustomBranding = true,
                    canUseAnalytics = true,
                    canUseApiAccess = false,
                    updatedAt = now,
                    updatedBy = "system-default",
                )
                SubscriptionTier.ENTERPRISE -> TierLimit(
                    tier = tier,
                    maxQueues = UNLIMITED,
                    maxOperatorsPerQueue = UNLIMITED,
                    maxTicketsPerDay = UNLIMITED,
                    maxActiveTickets = UNLIMITED,
                    maxInvitesPerMonth = UNLIMITED,
                    maxCountersPerQueue = UNLIMITED,
                    canUseEmailNotifications = true,
                    canUseCustomBranding = true,
                    canUseAnalytics = true,
                    canUseApiAccess = true,
                    updatedAt = now,
                    updatedBy = "system-default",
                )
            }
        }
    }
}
