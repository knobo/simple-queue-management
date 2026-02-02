package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.Feedback
import com.example.simplequeue.domain.model.FeedbackCategory
import com.example.simplequeue.domain.model.FeedbackType
import com.example.simplequeue.domain.port.FeedbackRepository

/**
 * Use case for submitting new feedback.
 * Handles validation and creation of feedback entries.
 */
class SubmitFeedbackUseCase(
    private val feedbackRepository: FeedbackRepository,
) {
    data class FeedbackRequest(
        val type: FeedbackType,
        val category: FeedbackCategory,
        val title: String,
        val description: String,
        val screenshotUrl: String? = null,
        val userAgent: String? = null,
        val currentUrl: String? = null,
    )

    fun execute(
        userId: String,
        userEmail: String,
        request: FeedbackRequest,
        subscriptionTier: String? = null,
    ): Feedback {
        // Validate title length (5-100 characters)
        require(request.title.length in 5..100) {
            "Title must be between 5 and 100 characters"
        }

        // Validate description length (20-2000 characters)
        require(request.description.length in 20..2000) {
            "Description must be between 20 and 2000 characters"
        }

        // Validate screenshot URL if provided
        request.screenshotUrl?.let { url ->
            require(url.startsWith("https://")) {
                "Screenshot URL must use HTTPS"
            }
        }

        val feedback = Feedback.create(
            userId = userId,
            userEmail = userEmail,
            type = request.type,
            category = request.category,
            title = request.title.trim(),
            description = request.description.trim(),
            screenshotUrl = request.screenshotUrl?.trim(),
            userAgent = request.userAgent,
            currentUrl = request.currentUrl,
            subscriptionTier = subscriptionTier,
        )

        feedbackRepository.save(feedback)

        return feedback
    }
}
