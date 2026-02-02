package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.port.InviteRepository

/**
 * Use case for declining an invite.
 */
class DeclineInviteUseCase(
    private val inviteRepository: InviteRepository,
) {
    /**
     * Decline an invite using its token.
     *
     * @param token The invite token
     * @throws IllegalArgumentException if invite not found
     * @throws IllegalStateException if invite is not pending
     */
    fun execute(token: String) {
        val invite = inviteRepository.findByToken(token)
            ?: throw IllegalArgumentException("Invite not found")

        invite.decline()
        inviteRepository.save(invite)
    }
}
