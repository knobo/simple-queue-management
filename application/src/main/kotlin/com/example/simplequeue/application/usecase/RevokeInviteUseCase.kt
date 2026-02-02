package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.port.InviteRepository
import com.example.simplequeue.domain.port.QueueRepository
import java.util.UUID

/**
 * Use case for revoking a pending invite.
 */
class RevokeInviteUseCase(
    private val inviteRepository: InviteRepository,
    private val queueRepository: QueueRepository,
) {
    /**
     * Revoke a pending invite.
     *
     * @param inviteId The ID of the invite to revoke
     * @param ownerId The ID of the user revoking (must be queue owner)
     * @throws IllegalArgumentException if invite not found
     * @throws IllegalStateException if user is not the queue owner or invite is not pending
     */
    fun execute(inviteId: UUID, ownerId: String) {
        val invite = inviteRepository.findById(inviteId)
            ?: throw IllegalArgumentException("Invite not found")

        val queue = queueRepository.findById(invite.queueId)
            ?: throw IllegalStateException("Queue not found")

        if (queue.ownerId != ownerId) {
            throw IllegalStateException("Only the queue owner can revoke invites")
        }

        invite.revoke()
        inviteRepository.save(invite)
    }
}
