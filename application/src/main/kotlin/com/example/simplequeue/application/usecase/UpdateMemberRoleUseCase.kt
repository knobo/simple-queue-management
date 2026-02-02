package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.MemberRole
import com.example.simplequeue.domain.model.QueueMember
import com.example.simplequeue.domain.port.QueueMemberRepository
import com.example.simplequeue.domain.port.QueueRepository
import java.util.UUID

/**
 * Use case for updating a member's role in a queue.
 */
class UpdateMemberRoleUseCase(
    private val queueMemberRepository: QueueMemberRepository,
    private val queueRepository: QueueRepository,
) {
    /**
     * Update a member's role in a queue.
     *
     * @param queueId The queue ID
     * @param memberId The member ID to update
     * @param newRole The new role to assign
     * @param requesterId The ID of the user requesting the change (must be queue owner)
     * @return The updated member
     * @throws IllegalArgumentException if member not found
     * @throws IllegalStateException if user is not the queue owner or trying to change owner role
     */
    fun execute(queueId: UUID, memberId: UUID, newRole: MemberRole, requesterId: String): QueueMember {
        val queue = queueRepository.findById(queueId)
            ?: throw IllegalArgumentException("Queue not found")

        if (queue.ownerId != requesterId) {
            throw IllegalStateException("Only the queue owner can change member roles")
        }

        val member = queueMemberRepository.findById(memberId)
            ?: throw IllegalArgumentException("Member not found")

        // Verify the member belongs to the specified queue
        if (member.queueId != queueId) {
            throw IllegalArgumentException("Member does not belong to this queue")
        }

        // Cannot change to OWNER role (there's only one owner)
        if (newRole == MemberRole.OWNER) {
            throw IllegalStateException("Cannot assign OWNER role to members")
        }

        // Create updated member with new role
        val updatedMember = member.copy(role = newRole)
        queueMemberRepository.save(updatedMember)
        
        return updatedMember
    }
}
