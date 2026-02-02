package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.QueueMember
import com.example.simplequeue.domain.port.InviteRepository
import com.example.simplequeue.domain.port.QueueMemberRepository
import com.example.simplequeue.domain.port.QueueRepository

/**
 * Use case for accepting an invite to join a queue.
 */
class AcceptInviteUseCase(
    private val inviteRepository: InviteRepository,
    private val queueMemberRepository: QueueMemberRepository,
    private val queueRepository: QueueRepository,
) {
    /**
     * Accept an invite using its token.
     *
     * @param token The invite token
     * @param userId The ID of the user accepting the invite
     * @return The created QueueMember
     * @throws IllegalArgumentException if invite not found
     * @throws IllegalStateException if invite cannot be accepted (expired, already used, etc.)
     */
    fun execute(token: String, userId: String): QueueMember {
        // Find invite by token
        val invite = inviteRepository.findByToken(token)
            ?: throw IllegalArgumentException("Invite not found")

        // Validate queue still exists
        val queue = queueRepository.findById(invite.queueId)
            ?: throw IllegalStateException("Queue no longer exists")

        // Check if user is already a member
        val existingMember = queueMemberRepository.findByQueueIdAndUserId(invite.queueId, userId)
        if (existingMember != null) {
            throw IllegalStateException("You are already a member of this queue")
        }

        // Check if user is the owner
        if (queue.ownerId == userId) {
            throw IllegalStateException("You are already the owner of this queue")
        }

        // Check if invite can be accepted (will throw if not)
        if (!invite.canBeAccepted()) {
            if (invite.isExpired()) {
                invite.markExpired()
                inviteRepository.save(invite)
                throw IllegalStateException("This invite has expired")
            }
            throw IllegalStateException("This invite cannot be accepted (status: ${invite.status})")
        }

        // Accept the invite
        invite.accept(userId)
        inviteRepository.save(invite)

        // Create queue member
        val member = QueueMember.create(
            queueId = invite.queueId,
            userId = userId,
            role = invite.role,
            invitedBy = invite.acceptedByUserId, // The original inviter could be tracked differently if needed
        )
        queueMemberRepository.save(member)

        return member
    }
}
