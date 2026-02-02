package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.domain.model.SubscriptionTier
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Internal API for Queue Core to check subscription limits and quotas.
 * This controller is intended for internal service-to-service communication.
 * 
 * For MVP: No authentication required - assumes same K8s cluster access.
 * TODO: Add internal API key validation or network-level restrictions for production.
 */
@RestController
@RequestMapping("/api/internal/subscription")
class InternalSubscriptionController(
    private val subscriptionService: SubscriptionService
) {

    /**
     * Get full quota information for a user.
     * Queue Core can call this to get all limits at once.
     */
    @GetMapping("/{userId}/quota")
    fun getQuota(@PathVariable userId: String): ResponseEntity<QuotaResponse> {
        val limits = subscriptionService.getTierLimitForUser(userId)
        val queueCount = subscriptionService.getQueueCount(userId)
        
        return ResponseEntity.ok(
            QuotaResponse(
                tier = limits.tier,
                maxQueues = limits.maxQueues,
                maxOperatorsPerQueue = limits.maxOperatorsPerQueue,
                maxCountersPerQueue = limits.maxCountersPerQueue,
                maxTicketsPerDay = limits.maxTicketsPerDay,
                canUseEmailNotifications = limits.canUseEmailNotifications,
                canUseCustomBranding = limits.canUseCustomBranding,
                canCreateQueue = limits.canCreateQueue(queueCount),
                canUseAnalytics = limits.canUseAnalytics,
                canUseApiAccess = limits.canUseApiAccess,
                currentQueues = queueCount
            )
        )
    }

    /**
     * Check if a user can create a new queue.
     * Queue Core should call this before creating a queue.
     */
    @GetMapping("/{userId}/can-create-queue")
    fun canCreateQueue(
        @PathVariable userId: String,
        @RequestParam currentQueueCount: Int
    ): ResponseEntity<Boolean> {
        val limits = subscriptionService.getTierLimitForUser(userId)
        return ResponseEntity.ok(limits.canCreateQueue(currentQueueCount))
    }

    /**
     * Check if a user can invite an operator.
     * Queue Core should call this before adding an operator to a queue.
     */
    @GetMapping("/{userId}/can-invite-operator")
    fun canInviteOperator(
        @PathVariable userId: String,
        @RequestParam currentOperatorCount: Int
    ): ResponseEntity<Boolean> {
        val limits = subscriptionService.getTierLimitForUser(userId)
        return ResponseEntity.ok(limits.canAddOperator(currentOperatorCount))
    }

    /**
     * Check if a user can add a counter.
     * Queue Core should call this before adding a counter to a queue.
     */
    @GetMapping("/{userId}/can-add-counter")
    fun canAddCounter(
        @PathVariable userId: String,
        @RequestParam currentCounterCount: Int
    ): ResponseEntity<Boolean> {
        val limits = subscriptionService.getTierLimitForUser(userId)
        return ResponseEntity.ok(limits.canAddCounter(currentCounterCount))
    }

    /**
     * Check if a user can issue more tickets today.
     */
    @GetMapping("/{userId}/can-issue-ticket")
    fun canIssueTicket(
        @PathVariable userId: String,
        @RequestParam ticketsIssuedToday: Int
    ): ResponseEntity<Boolean> {
        val limits = subscriptionService.getTierLimitForUser(userId)
        return ResponseEntity.ok(limits.canIssueTicket(ticketsIssuedToday))
    }

    /**
     * Get just the subscription tier for a user.
     * Lightweight endpoint for quick tier checks.
     */
    @GetMapping("/{userId}/tier")
    fun getTier(@PathVariable userId: String): ResponseEntity<SubscriptionTier> {
        val tier = subscriptionService.getTier(userId)
        return ResponseEntity.ok(tier)
    }
}

/**
 * DTO representing a user's quota and subscription limits.
 * Returned by the internal subscription API for Queue Core consumption.
 */
data class QuotaResponse(
    val tier: SubscriptionTier,
    val maxQueues: Int,
    val maxOperatorsPerQueue: Int,
    val maxCountersPerQueue: Int,
    val maxTicketsPerDay: Int,
    val canUseEmailNotifications: Boolean,
    val canUseCustomBranding: Boolean,
    val canCreateQueue: Boolean,
    val canUseAnalytics: Boolean,
    val canUseApiAccess: Boolean,
    val currentQueues: Int
)
