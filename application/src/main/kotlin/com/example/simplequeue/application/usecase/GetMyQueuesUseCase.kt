package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.MemberRole
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.port.QueueMemberRepository
import com.example.simplequeue.domain.port.QueueRepository
import java.util.UUID

/**
 * Represents a queue with the user's role in it.
 */
data class QueueWithRole(
    val queue: Queue,
    val role: MemberRole,
)

/**
 * Use case for getting all queues a user has access to.
 * This includes queues they own and queues they are members of.
 */
class GetMyQueuesUseCase(
    private val queueRepository: QueueRepository,
    private val queueMemberRepository: QueueMemberRepository,
) {
    /**
     * Get all queues a user has access to.
     *
     * @param userId The user ID
     * @return List of queues with the user's role in each
     */
    fun execute(userId: String): List<QueueWithRole> {
        val result = mutableListOf<QueueWithRole>()

        // Get queues owned by user
        val ownedQueues = queueRepository.findByOwnerId(userId)
        for (queue in ownedQueues) {
            result.add(QueueWithRole(queue, MemberRole.OWNER))
        }

        // Get queues where user is a member
        val memberships = queueMemberRepository.findByUserId(userId)
        for (membership in memberships) {
            val queue = queueRepository.findById(membership.queueId)
            if (queue != null) {
                // Avoid duplicates (shouldn't happen, but defensive)
                if (result.none { it.queue.id == queue.id }) {
                    result.add(QueueWithRole(queue, membership.role))
                }
            }
        }

        return result.sortedBy { it.queue.name }
    }

    /**
     * Check if a user has at least the specified role for a queue.
     *
     * @param queueId The queue ID
     * @param userId The user ID
     * @param requiredRole The minimum required role
     * @return true if the user has at least the required role
     */
    fun hasAccess(queueId: UUID, userId: String, requiredRole: MemberRole = MemberRole.OPERATOR): Boolean {
        val queue = queueRepository.findById(queueId) ?: return false

        // Owner has all permissions
        if (queue.ownerId == userId) {
            return true
        }

        // Check membership
        val membership = queueMemberRepository.findByQueueIdAndUserId(queueId, userId)
        return membership?.role?.hasAtLeast(requiredRole) == true
    }
}
