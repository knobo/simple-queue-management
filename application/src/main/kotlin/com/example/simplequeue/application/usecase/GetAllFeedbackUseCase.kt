package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.Feedback
import com.example.simplequeue.domain.model.FeedbackCategory
import com.example.simplequeue.domain.model.FeedbackNote
import com.example.simplequeue.domain.model.FeedbackStatus
import com.example.simplequeue.domain.model.FeedbackType
import com.example.simplequeue.domain.port.FeedbackNoteRepository
import com.example.simplequeue.domain.port.FeedbackRepository
import java.util.UUID

/**
 * Use case for admin operations on feedback.
 * Includes listing, filtering, updating status, and adding notes.
 */
class GetAllFeedbackUseCase(
    private val feedbackRepository: FeedbackRepository,
    private val feedbackNoteRepository: FeedbackNoteRepository,
) {
    data class FeedbackFilter(
        val type: FeedbackType? = null,
        val status: FeedbackStatus? = null,
        val category: FeedbackCategory? = null,
        val search: String? = null,
    )

    data class FeedbackStats(
        val total: Int,
        val byType: Map<FeedbackType, Int>,
        val byStatus: Map<FeedbackStatus, Int>,
    )

    /**
     * Get all feedback with optional filtering.
     */
    fun execute(filter: FeedbackFilter = FeedbackFilter()): List<Feedback> {
        return feedbackRepository.findFiltered(
            type = filter.type,
            status = filter.status,
            category = filter.category,
            search = filter.search,
        ).sortedByDescending { it.createdAt }
    }

    /**
     * Get a specific feedback item by ID.
     */
    fun getById(feedbackId: UUID): Feedback? {
        return feedbackRepository.findById(feedbackId)
    }

    /**
     * Update the status of a feedback item.
     */
    fun updateStatus(feedbackId: UUID, newStatus: FeedbackStatus): Feedback? {
        val feedback = feedbackRepository.findById(feedbackId) ?: return null
        val updated = feedback.updateStatus(newStatus)
        feedbackRepository.save(updated)
        return updated
    }

    /**
     * Add an admin note to a feedback item.
     */
    fun addNote(feedbackId: UUID, adminUserId: String, noteText: String): FeedbackNote {
        val note = FeedbackNote.create(
            feedbackId = feedbackId,
            adminUserId = adminUserId,
            note = noteText.trim(),
        )
        feedbackNoteRepository.save(note)
        return note
    }

    /**
     * Get all notes for a feedback item.
     */
    fun getNotes(feedbackId: UUID): List<FeedbackNote> {
        return feedbackNoteRepository.findByFeedbackId(feedbackId)
            .sortedByDescending { it.createdAt }
    }

    /**
     * Get statistics about feedback.
     */
    fun getStats(): FeedbackStats {
        val byType = feedbackRepository.countByType()
        val byStatus = feedbackRepository.countByStatus()
        val total = byStatus.values.sum()

        return FeedbackStats(
            total = total,
            byType = byType,
            byStatus = byStatus,
        )
    }
}
