package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.Feedback
import com.example.simplequeue.domain.port.FeedbackRepository
import java.util.UUID

/**
 * Use case for retrieving the current user's feedback submissions.
 */
class GetMyFeedbackUseCase(
    private val feedbackRepository: FeedbackRepository,
) {
    /**
     * Get all feedback submitted by the user.
     */
    fun execute(userId: String): List<Feedback> {
        return feedbackRepository.findByUserId(userId)
            .sortedByDescending { it.createdAt }
    }

    /**
     * Get a specific feedback item if it belongs to the user.
     */
    fun getById(userId: String, feedbackId: UUID): Feedback? {
        val feedback = feedbackRepository.findById(feedbackId)
        return if (feedback?.userId == userId) feedback else null
    }
}
