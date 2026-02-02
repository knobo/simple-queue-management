package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.usecase.AcceptInviteUseCase
import com.example.simplequeue.application.usecase.DeclineInviteUseCase
import com.example.simplequeue.domain.model.MemberRole
import com.example.simplequeue.domain.model.QueueMember
import com.example.simplequeue.domain.port.InviteRepository
import com.example.simplequeue.domain.port.QueueRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * Controller for handling invite acceptance flow.
 * These endpoints are for the invitee (person who received the invite).
 */
@RestController
@RequestMapping("/api/invites")
class InviteController(
    private val inviteRepository: InviteRepository,
    private val queueRepository: QueueRepository,
    private val acceptInviteUseCase: AcceptInviteUseCase,
    private val declineInviteUseCase: DeclineInviteUseCase,
) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(InviteController::class.java)
    }

    /**
     * Get information about an invite (public endpoint).
     * Used to display invite details before accepting.
     */
    @GetMapping("/{token}")
    fun getInviteInfo(@PathVariable token: String): InviteInfoDTO {
        val invite = inviteRepository.findByToken(token)
            ?: throw IllegalArgumentException("Invite not found")

        val queue = queueRepository.findById(invite.queueId)

        return InviteInfoDTO(
            queueName = queue?.name ?: "Unknown Queue",
            role = invite.role,
            status = invite.status.name,
            expiresAt = invite.expiresAt,
            isExpired = invite.isExpired(),
            canBeAccepted = invite.canBeAccepted(),
        )
    }

    /**
     * Accept an invite (requires authentication).
     * The accepting user becomes a member of the queue.
     */
    @PostMapping("/{token}/accept")
    fun acceptInvite(
        @PathVariable token: String,
        authentication: Authentication,
    ): AcceptedInviteDTO {
        val userId = getUserId(authentication.principal)
        logger.info("User {} accepting invite with token {}", userId, token)

        val member = acceptInviteUseCase.execute(token, userId)

        val queue = queueRepository.findById(member.queueId)

        return AcceptedInviteDTO(
            memberId = member.id,
            queueId = member.queueId,
            queueName = queue?.name ?: "Unknown Queue",
            role = member.role,
            joinedAt = member.joinedAt,
        )
    }

    /**
     * Decline an invite (public endpoint).
     * Can be done without authentication.
     */
    @PostMapping("/{token}/decline")
    fun declineInvite(@PathVariable token: String) {
        logger.info("Invite declined: {}", token)
        declineInviteUseCase.execute(token)
    }

    private fun getUserId(principal: Any?): String =
        when (principal) {
            is Jwt -> principal.subject
            is OAuth2User -> principal.name
            else -> throw IllegalStateException("Unknown principal type: ${principal?.javaClass}")
        }

    // ==================== DTOs ====================

    data class InviteInfoDTO(
        val queueName: String,
        val role: MemberRole,
        val status: String,
        val expiresAt: Instant,
        val isExpired: Boolean,
        val canBeAccepted: Boolean,
    )

    data class AcceptedInviteDTO(
        val memberId: UUID,
        val queueId: UUID,
        val queueName: String,
        val role: MemberRole,
        val joinedAt: Instant,
    )
}
