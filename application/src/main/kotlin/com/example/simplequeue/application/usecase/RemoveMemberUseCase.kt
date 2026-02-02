package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.port.QueueMemberRepository
import com.example.simplequeue.domain.port.QueueRepository
import java.util.UUID

/**
 * Use case for removing a member from a queue.
 */
class RemoveMemberUseCase(
    private val queueMemberRepository: QueueMemberRepository,
    private val queueRepository: QueueRepository,
) {
    /**
     * Remove a member from a queue.
     *
     * @param queueId The queue ID
     * @param memberId The member ID to remove
     * @param ownerId The ID of the user requesting removal (must be queue owner)
     * @throws IllegalArgumentException if member not found
     * @throws IllegalStateException if user is not the queue owner
     */
    fun execute(queueId: UUID, memberId: UUID, ownerId: String) {
        val queue = queueRepository.findById(queueId)
            ?: throw IllegalArgumentException("Queue not found")

        if (queue.ownerId != ownerId) {
            throw IllegalStateException("Only the queue owner can remove members")
        }

        val member = queueMemberRepository.findById(memberId)
            ?: throw IllegalArgumentException("Member not found")

        // Verify the member belongs to the specified queue
        if (member.queueId != queueId) {
            throw IllegalArgumentException("Member does not belong to this queue")
        }

        queueMemberRepository.delete(memberId)
    }
}
