package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.domain.model.SubscriptionTier
import com.example.simplequeue.domain.model.TierLimit
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.time.Instant

/**
 * REST controller for managing tier limits.
 * Only accessible by superadmins.
 */
@RestController
@RequestMapping("/api/admin/tier-limits")
@PreAuthorize("hasRole('SUPERADMIN') or hasRole('superadmin')")
class TierLimitsController(
    private val subscriptionService: SubscriptionService,
) {
    /**
     * Get all tier limits.
     */
    @GetMapping
    fun getAll(): List<TierLimitDTO> = subscriptionService.getAllTierLimits().map { TierLimitDTO.from(it) }

    /**
     * Get tier limit by tier name.
     */
    @GetMapping("/{tier}")
    fun getByTier(
        @PathVariable tier: String,
    ): ResponseEntity<TierLimitDTO> =
        try {
            val subscriptionTier = SubscriptionTier.valueOf(tier.uppercase())
            val limit = subscriptionService.getTierLimit(subscriptionTier)
            ResponseEntity.ok(TierLimitDTO.from(limit))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }

    /**
     * Update tier limit.
     */
    @PutMapping("/{tier}")
    fun update(
        @PathVariable tier: String,
        @RequestBody request: UpdateTierLimitRequest,
        authentication: Authentication,
    ): ResponseEntity<TierLimitDTO> =
        try {
            val subscriptionTier = SubscriptionTier.valueOf(tier.uppercase())
            val userId = getUserId(authentication.principal)

            val updated =
                TierLimit(
                    tier = subscriptionTier,
                    maxQueues = request.maxQueues,
                    maxOperatorsPerQueue = request.maxOperatorsPerQueue,
                    maxTicketsPerDay = request.maxTicketsPerDay,
                    maxActiveTickets = request.maxActiveTickets,
                    maxInvitesPerMonth = request.maxInvitesPerMonth,
                    maxCountersPerQueue = request.maxCountersPerQueue,
                    canUseEmailNotifications = request.canUseEmailNotifications,
                    canUseCustomBranding = request.canUseCustomBranding,
                    canUseAnalytics = request.canUseAnalytics,
                    canUseApiAccess = request.canUseApiAccess,
                    updatedAt = Instant.now(),
                    updatedBy = userId,
                )

            subscriptionService.updateTierLimit(updated)
            ResponseEntity.ok(TierLimitDTO.from(updated))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: IllegalStateException) {
            ResponseEntity.internalServerError().build()
        }

    private fun getUserId(principal: Any?): String =
        when (principal) {
            is Jwt -> principal.subject
            is OAuth2User -> principal.name
            else -> "unknown"
        }

    // DTOs

    data class TierLimitDTO(
        val tier: String,
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
        val isMaxQueuesUnlimited: Boolean,
        val isMaxOperatorsUnlimited: Boolean,
        val isMaxTicketsUnlimited: Boolean,
        val isMaxActiveTicketsUnlimited: Boolean,
        val isMaxInvitesUnlimited: Boolean,
        val isMaxCountersUnlimited: Boolean,
        val updatedAt: Instant,
        val updatedBy: String?,
    ) {
        companion object {
            fun from(limit: TierLimit): TierLimitDTO =
                TierLimitDTO(
                    tier = limit.tier.name,
                    maxQueues = limit.maxQueues,
                    maxOperatorsPerQueue = limit.maxOperatorsPerQueue,
                    maxTicketsPerDay = limit.maxTicketsPerDay,
                    maxActiveTickets = limit.maxActiveTickets,
                    maxInvitesPerMonth = limit.maxInvitesPerMonth,
                    maxCountersPerQueue = limit.maxCountersPerQueue,
                    canUseEmailNotifications = limit.canUseEmailNotifications,
                    canUseCustomBranding = limit.canUseCustomBranding,
                    canUseAnalytics = limit.canUseAnalytics,
                    canUseApiAccess = limit.canUseApiAccess,
                    isMaxQueuesUnlimited = limit.isUnlimited(limit.maxQueues),
                    isMaxOperatorsUnlimited = limit.isUnlimited(limit.maxOperatorsPerQueue),
                    isMaxTicketsUnlimited = limit.isUnlimited(limit.maxTicketsPerDay),
                    isMaxActiveTicketsUnlimited = limit.isUnlimited(limit.maxActiveTickets),
                    isMaxInvitesUnlimited = limit.isUnlimited(limit.maxInvitesPerMonth),
                    isMaxCountersUnlimited = limit.isUnlimited(limit.maxCountersPerQueue),
                    updatedAt = limit.updatedAt,
                    updatedBy = limit.updatedBy,
                )
        }
    }

    data class UpdateTierLimitRequest(
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
    )
}
