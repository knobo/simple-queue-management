package com.example.simplequeue.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Represents user feedback (bug reports, feature requests, or general comments).
 */
data class Feedback(
    val id: UUID,
    val userId: String,
    val userEmail: String,
    val type: FeedbackType,
    val category: FeedbackCategory,
    val title: String,
    val description: String,
    val screenshotUrl: String?,
    val status: FeedbackStatus,

    // Context captured at submission time
    val userAgent: String?,
    val currentUrl: String?,
    val subscriptionTier: String?,

    val createdAt: Instant,
    val updatedAt: Instant,
    val resolvedAt: Instant?,
) {
    companion object {
        fun create(
            userId: String,
            userEmail: String,
            type: FeedbackType,
            category: FeedbackCategory,
            title: String,
            description: String,
            screenshotUrl: String? = null,
            userAgent: String? = null,
            currentUrl: String? = null,
            subscriptionTier: String? = null,
        ): Feedback {
            val now = Instant.now()
            return Feedback(
                id = UUID.randomUUID(),
                userId = userId,
                userEmail = userEmail,
                type = type,
                category = category,
                title = title,
                description = description,
                screenshotUrl = screenshotUrl,
                status = FeedbackStatus.NEW,
                userAgent = userAgent,
                currentUrl = currentUrl,
                subscriptionTier = subscriptionTier,
                createdAt = now,
                updatedAt = now,
                resolvedAt = null,
            )
        }
    }

    fun updateStatus(newStatus: FeedbackStatus): Feedback {
        val now = Instant.now()
        return copy(
            status = newStatus,
            updatedAt = now,
            resolvedAt = if (newStatus == FeedbackStatus.RESOLVED || newStatus == FeedbackStatus.CLOSED) now else resolvedAt,
        )
    }
}

/**
 * Type of feedback being submitted.
 */
enum class FeedbackType {
    BUG,      // Something is broken
    FEATURE,  // Feature request
    GENERAL,  // General feedback/comment
}

/**
 * Category to help organize and route feedback.
 */
enum class FeedbackCategory {
    QUEUE_MANAGEMENT,  // Queue handling, tickets, serving
    NOTIFICATIONS,     // Alerts, email, SMS
    DASHBOARD,         // Dashboard, statistics
    BILLING,           // Payment, subscription
    MOBILE,            // Mobile app, responsive design
    PERFORMANCE,       // Speed, loading times
    USABILITY,         // UX, design
    INTEGRATIONS,      // API, webhooks
    OTHER,             // Anything else
}

/**
 * Status of the feedback item.
 */
enum class FeedbackStatus {
    NEW,          // Just received
    IN_PROGRESS,  // Being worked on
    RESOLVED,     // Fixed/implemented
    CLOSED,       // Closed without action
}

/**
 * Admin note attached to feedback for internal tracking.
 */
data class FeedbackNote(
    val id: UUID,
    val feedbackId: UUID,
    val adminUserId: String,
    val note: String,
    val createdAt: Instant,
) {
    companion object {
        fun create(
            feedbackId: UUID,
            adminUserId: String,
            note: String,
        ): FeedbackNote {
            return FeedbackNote(
                id = UUID.randomUUID(),
                feedbackId = feedbackId,
                adminUserId = adminUserId,
                note = note,
                createdAt = Instant.now(),
            )
        }
    }
}
