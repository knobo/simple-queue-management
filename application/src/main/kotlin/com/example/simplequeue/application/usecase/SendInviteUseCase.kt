package com.example.simplequeue.application.usecase

import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.domain.model.Invite
import com.example.simplequeue.domain.model.MemberRole
import com.example.simplequeue.domain.port.InviteRepository
import com.example.simplequeue.domain.port.QueueRepository
import java.util.UUID

/**
 * Use case for sending an invite to a user to join a queue as an operator.
 */
class SendInviteUseCase(
    private val inviteRepository: InviteRepository,
    private val queueRepository: QueueRepository,
    private val subscriptionService: SubscriptionService,
) {
    /**
     * Send an invite to join a queue.
     *
     * @param queueId The queue to invite to
     * @param email The email address of the invitee
     * @param role The role to assign (typically OPERATOR)
     * @param inviterId The user ID of the person sending the invite (must be queue owner)
     * @return The created invite
     * @throws IllegalArgumentException if queue not found
     * @throws IllegalStateException if user is not the queue owner
     * @throws FeatureNotAllowedException if subscription limits exceeded
     */
    fun execute(
        queueId: UUID,
        email: String,
        role: MemberRole,
        inviterId: String,
    ): Invite {
        // Validate queue exists
        val queue = queueRepository.findById(queueId)
            ?: throw IllegalArgumentException("Queue not found")

        // Check ownership
        if (queue.ownerId != inviterId) {
            throw IllegalStateException("Only the queue owner can send invites")
        }

        // Check subscription limits
        if (!subscriptionService.canInviteOperator(inviterId, queueId)) {
            val limits = subscriptionService.getLimits(inviterId)
            throw FeatureNotAllowedException(
                "You have reached the maximum number of operators (${limits.maxOperatorsPerQueue}) " +
                    "for your ${limits.tier} plan. Upgrade your subscription to invite more operators."
            )
        }

        // Validate email format (basic check)
        require(email.isNotBlank() && email.contains("@")) {
            "Invalid email address"
        }

        // Check for existing pending invite with same email for this queue
        val existingInvites = inviteRepository.findPendingByQueueId(queueId)
        if (existingInvites.any { it.email.equals(email, ignoreCase = true) }) {
            throw IllegalStateException("An invite has already been sent to this email address")
        }

        // Create and save invite
        val invite = Invite.create(
            queueId = queueId,
            email = email,
            role = role,
        )
        inviteRepository.save(invite)

        // Note: Email sending is handled separately or can be triggered here
        // For now, we just return the invite with its token

        return invite
    }
}
