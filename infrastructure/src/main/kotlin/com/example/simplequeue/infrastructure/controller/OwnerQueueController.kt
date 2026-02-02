package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.service.ReferralService
import com.example.simplequeue.application.usecase.CompleteTicketUseCase
import com.example.simplequeue.application.usecase.CreateCounterUseCase
import com.example.simplequeue.application.usecase.CreateQueueUseCase
import com.example.simplequeue.application.usecase.DeleteCounterUseCase
import com.example.simplequeue.application.usecase.DeleteQueueUseCase
import com.example.simplequeue.application.usecase.EndCounterSessionUseCase
import com.example.simplequeue.application.usecase.GetMyQueuesUseCase
import com.example.simplequeue.application.usecase.GetOperatorSessionUseCase
import com.example.simplequeue.application.usecase.ManageQueueStateUseCase
import com.example.simplequeue.application.usecase.QueueWithRole
import com.example.simplequeue.application.usecase.RemoveMemberUseCase
import com.example.simplequeue.application.usecase.RevokeInviteUseCase
import com.example.simplequeue.application.usecase.RevokeTicketUseCase
import com.example.simplequeue.application.usecase.SendInviteUseCase
import com.example.simplequeue.application.usecase.ServeTicketUseCase
import com.example.simplequeue.application.usecase.StartCounterSessionUseCase
import com.example.simplequeue.application.usecase.UpdateCounterUseCase
import com.example.simplequeue.application.usecase.UpdateMemberRoleUseCase
import com.example.simplequeue.domain.model.Counter
import com.example.simplequeue.domain.model.Invite
import com.example.simplequeue.domain.model.MemberRole
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.QueueMember
import com.example.simplequeue.domain.model.QueueState
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.CounterRepository
import com.example.simplequeue.domain.port.InviteRepository
import com.example.simplequeue.domain.port.QueueMemberRepository
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.infrastructure.filter.ReferralCookieFilter
import com.example.simplequeue.infrastructure.service.AccessTokenService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/owner")
class OwnerQueueController(
    private val queueRepository: QueueRepository,
    private val queueMemberRepository: QueueMemberRepository,
    private val inviteRepository: InviteRepository,
    private val counterRepository: CounterRepository,
    private val serveTicketUseCase: ServeTicketUseCase,
    private val completeTicketUseCase: CompleteTicketUseCase,
    private val revokeTicketUseCase: RevokeTicketUseCase,
    private val deleteQueueUseCase: DeleteQueueUseCase,
    private val createQueueUseCase: CreateQueueUseCase,
    private val manageQueueStateUseCase: ManageQueueStateUseCase,
    private val sendInviteUseCase: SendInviteUseCase,
    private val revokeInviteUseCase: RevokeInviteUseCase,
    private val removeMemberUseCase: RemoveMemberUseCase,
    private val updateMemberRoleUseCase: UpdateMemberRoleUseCase,
    private val createCounterUseCase: CreateCounterUseCase,
    private val deleteCounterUseCase: DeleteCounterUseCase,
    private val updateCounterUseCase: UpdateCounterUseCase,
    private val getMyQueuesUseCase: GetMyQueuesUseCase,
    private val referralService: ReferralService,
    private val accessTokenService: AccessTokenService,
    private val startCounterSessionUseCase: StartCounterSessionUseCase,
    private val endCounterSessionUseCase: EndCounterSessionUseCase,
    private val getOperatorSessionUseCase: GetOperatorSessionUseCase,
) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(OwnerQueueController::class.java)
    }

    @PostMapping("/queues")
    fun registerQueue(
        @RequestBody request: QueueRequest,
        authentication: Authentication,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): Queue {
        logger.info("Creating queue ${request.name}")
        logger.info("Authentication principal: ${authentication.principal}")
        val ownerId = getOwnerId(authentication.principal)

        // Check if this is the user's first queue (before creating it)
        val isFirstQueue = queueRepository.findByOwnerId(ownerId).isEmpty()

        // Validation logic is now in CreateQueueUseCase (except the single queue check might be duplicated or moved)
        // I moved the single queue check to UseCase, so I can just call it.
        val queue = createQueueUseCase.execute(request.name, ownerId)

        // If this is the first queue, try to apply referral code from cookie
        if (isFirstQueue) {
            processReferralCookieIfPresent(httpRequest, httpResponse, ownerId)
        }

        return queue
    }

    /**
     * Process referral code from cookie if present.
     * This is called when a user creates their first queue, allowing referrals
     * to be applied even if the user registered before visiting the referral link.
     */
    private fun processReferralCookieIfPresent(
        request: HttpServletRequest,
        response: HttpServletResponse,
        userId: String
    ) {
        val referralCode = request.cookies
            ?.find { it.name == ReferralCookieFilter.REFERRAL_COOKIE_NAME }
            ?.value

        if (referralCode != null) {
            logger.info("Found referral code cookie for user $userId on first queue creation: $referralCode")

            try {
                val success = referralService.processReferralForUser(userId, referralCode)
                if (success) {
                    logger.info("Successfully processed referral for user $userId on first queue creation")
                }
            } catch (e: Exception) {
                logger.error("Error processing referral for user $userId: ${e.message}", e)
            }

            // Always delete the cookie after processing (success or failure)
            val cookie = Cookie(ReferralCookieFilter.REFERRAL_COOKIE_NAME, "").apply {
                maxAge = 0
                path = "/"
                isHttpOnly = true
            }
            response.addCookie(cookie)
        }
    }

    @GetMapping("/queues/me")
    fun getMyQueues(authentication: Authentication): List<QueueWithRoleDTO> {
        val userId = getOwnerId(authentication.principal)
        return getMyQueuesUseCase.execute(userId).map { QueueWithRoleDTO.from(it) }
    }

    @PatchMapping("/queues/{id}/status")
    fun toggleStatus(
        @PathVariable id: UUID,
        @RequestParam open: Boolean,
        authentication: Authentication,
    ) {
        val ownerId = getOwnerId(authentication.principal)
        val queue =
            queueRepository.findById(id)
                ?: throw IllegalArgumentException("Queue not found")

        if (queue.ownerId != ownerId) {
            throw IllegalStateException("Not the owner")
        }

        queue.open = open
        queueRepository.save(queue)
    }

    @PostMapping("/queues/{id}/next")
    fun callNext(
        @PathVariable id: UUID,
        authentication: Authentication,
    ) {
        // Operators can call next ticket
        checkAccess(id, authentication, MemberRole.OPERATOR)
        val operatorId = getOwnerId(authentication.principal)
        serveTicketUseCase.execute(id, operatorId = operatorId)
    }

    @PostMapping("/queues/{id}/tickets/{ticketId}/serve")
    fun serveTicket(
        @PathVariable id: UUID,
        @PathVariable ticketId: UUID,
        authentication: Authentication,
    ) {
        // Operators can serve tickets
        checkAccess(id, authentication, MemberRole.OPERATOR)
        val operatorId = getOwnerId(authentication.principal)
        serveTicketUseCase.execute(id, ticketId, operatorId)
    }

    @PostMapping("/queues/{id}/tickets/{ticketId}/complete")
    fun completeTicket(
        @PathVariable id: UUID,
        @PathVariable ticketId: UUID,
        authentication: Authentication,
    ) {
        // Operators can complete tickets
        checkAccess(id, authentication, MemberRole.OPERATOR)
        completeTicketUseCase.execute(id, ticketId)
    }

    @DeleteMapping("/queues/{id}/tickets/{ticketId}")
    fun revokeTicket(
        @PathVariable id: UUID,
        @PathVariable ticketId: UUID,
        authentication: Authentication,
    ) {
        // Operators can revoke/cancel tickets
        checkAccess(id, authentication, MemberRole.OPERATOR)
        revokeTicketUseCase.execute(id, ticketId)
    }

    @DeleteMapping("/queues/{id}")
    fun deleteQueue(
        @PathVariable id: UUID,
        authentication: Authentication,
    ) {
        val ownerId = getOwnerId(authentication.principal)
        val queue =
            queueRepository.findById(id)
                ?: throw IllegalArgumentException("Queue not found")

        if (queue.ownerId != ownerId) {
            throw IllegalStateException("Not the owner")
        }

        deleteQueueUseCase.execute(id)
    }

    @PostMapping("/queues/{id}/states")
    fun addState(
        @PathVariable id: UUID,
        @RequestBody request: StateRequest,
        authentication: Authentication,
    ): QueueState {
        checkOwnership(id, authentication)
        return manageQueueStateUseCase.addState(id, request.name, request.status)
    }

    @DeleteMapping("/queues/{id}/states/{stateId}")
    fun removeState(
        @PathVariable id: UUID,
        @PathVariable stateId: UUID,
        authentication: Authentication,
    ) {
        checkOwnership(id, authentication)
        manageQueueStateUseCase.removeState(id, stateId)
    }

    @PatchMapping("/queues/{id}/ticket-page-mode")
    fun updateTicketPageMode(
        @PathVariable id: UUID,
        @RequestBody request: TicketPageModeRequest,
        authentication: Authentication,
    ) {
        checkOwnership(id, authentication)
        val queue = queueRepository.findById(id)
            ?: throw IllegalArgumentException("Queue not found")
        queue.ticketPageMode = request.ticketPageMode
        queueRepository.save(queue)
    }

    // ==================== Invite Endpoints ====================

    @PostMapping("/queues/{id}/invites")
    fun sendInvite(
        @PathVariable id: UUID,
        @RequestBody request: InviteRequest,
        authentication: Authentication,
    ): InviteDTO {
        val ownerId = getOwnerId(authentication.principal)
        val invite = sendInviteUseCase.execute(
            queueId = id,
            email = request.email,
            role = request.role ?: MemberRole.OPERATOR,
            inviterId = ownerId,
        )
        return InviteDTO.from(invite)
    }

    @GetMapping("/queues/{id}/invites")
    fun getInvites(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): List<InviteDTO> {
        checkOwnership(id, authentication)
        return inviteRepository.findByQueueId(id).map { InviteDTO.from(it) }
    }

    @DeleteMapping("/queues/{id}/invites/{inviteId}")
    fun revokeInvite(
        @PathVariable id: UUID,
        @PathVariable inviteId: UUID,
        authentication: Authentication,
    ) {
        val ownerId = getOwnerId(authentication.principal)
        revokeInviteUseCase.execute(inviteId, ownerId)
    }

    // ==================== Member Endpoints ====================

    @GetMapping("/queues/{id}/members")
    fun getMembers(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): List<MemberDTO> {
        checkOwnership(id, authentication)
        return queueMemberRepository.findByQueueId(id).map { MemberDTO.from(it) }
    }

    @DeleteMapping("/queues/{id}/members/{memberId}")
    fun removeMember(
        @PathVariable id: UUID,
        @PathVariable memberId: UUID,
        authentication: Authentication,
    ) {
        val ownerId = getOwnerId(authentication.principal)
        removeMemberUseCase.execute(id, memberId, ownerId)
    }

    @PutMapping("/queues/{id}/members/{memberId}/role")
    fun updateMemberRole(
        @PathVariable id: UUID,
        @PathVariable memberId: UUID,
        @RequestBody request: UpdateRoleRequest,
        authentication: Authentication,
    ): MemberDTO {
        val ownerId = getOwnerId(authentication.principal)
        val member = updateMemberRoleUseCase.execute(id, memberId, request.role, ownerId)
        return MemberDTO.from(member)
    }

    // ==================== Counter Endpoints ====================

    @GetMapping("/queues/{id}/counters")
    fun getCounters(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): List<CounterDTO> {
        checkOwnership(id, authentication)
        return counterRepository.findByQueueId(id).map { CounterDTO.from(it) }
    }

    @PostMapping("/queues/{id}/counters")
    fun createCounter(
        @PathVariable id: UUID,
        @RequestBody request: CreateCounterRequest,
        authentication: Authentication,
    ): CounterDTO {
        val ownerId = getOwnerId(authentication.principal)
        val counter = createCounterUseCase.execute(id, request.name, ownerId)
        return CounterDTO.from(counter)
    }

    @PutMapping("/queues/{id}/counters/{counterId}")
    fun updateCounter(
        @PathVariable id: UUID,
        @PathVariable counterId: UUID,
        @RequestBody request: UpdateCounterRequest,
        authentication: Authentication,
    ): CounterDTO {
        val ownerId = getOwnerId(authentication.principal)
        val counter = updateCounterUseCase.execute(id, counterId, request.name, ownerId)
        return CounterDTO.from(counter)
    }

    @DeleteMapping("/queues/{id}/counters/{counterId}")
    fun deleteCounter(
        @PathVariable id: UUID,
        @PathVariable counterId: UUID,
        authentication: Authentication,
    ) {
        checkOwnership(id, authentication)
        deleteCounterUseCase.execute(counterId)
    }

    // ==================== Counter Session Endpoints ====================

    @PostMapping("/queues/{id}/session/start")
    fun startCounterSession(
        @PathVariable id: UUID,
        @RequestBody request: StartSessionRequest,
        authentication: Authentication,
    ): CounterSessionDTO {
        // Operators can start a session at a counter
        checkAccess(id, authentication, MemberRole.OPERATOR)
        val operatorId = getOwnerId(authentication.principal)
        val result = startCounterSessionUseCase.execute(id, request.counterId, operatorId)
        return CounterSessionDTO.from(result.session, result.counter)
    }

    @PostMapping("/queues/{id}/session/end")
    fun endCounterSession(
        @PathVariable id: UUID,
        authentication: Authentication,
    ) {
        // Operators can end their session
        checkAccess(id, authentication, MemberRole.OPERATOR)
        val operatorId = getOwnerId(authentication.principal)
        endCounterSessionUseCase.execute(operatorId)
    }

    @GetMapping("/queues/{id}/session")
    fun getOperatorSession(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): CounterSessionDTO? {
        // Operators can get their current session
        checkAccess(id, authentication, MemberRole.OPERATOR)
        val operatorId = getOwnerId(authentication.principal)
        val info = getOperatorSessionUseCase.executeForQueue(id, operatorId)
        val session = info?.session
        val counter = info?.counter
        return if (info != null && info.hasActiveSession && session != null && counter != null) {
            CounterSessionDTO.from(session, counter)
        } else {
            null
        }
    }

    // ==================== Token Status Endpoint ====================

    /**
     * Get token status for QR code page auto-refresh.
     * Returns validity status, expiry time, and seconds remaining.
     */
    @GetMapping("/queues/{id}/token/status")
    fun getTokenStatus(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): TokenStatusResponse {
        checkOwnership(id, authentication)
        
        val tokenInfo = accessTokenService.getTokenInfo(id)
            ?: throw IllegalArgumentException("Queue not found or no token configured")
        
        val isValid = when {
            tokenInfo.isLegacy -> true // Static tokens are always valid
            tokenInfo.secondsUntilExpiry != null && tokenInfo.secondsUntilExpiry <= 0 -> false
            else -> true
        }
        
        return TokenStatusResponse(
            valid = isValid,
            expiresAt = tokenInfo.expiresAt?.toString(),
            secondsRemaining = tokenInfo.secondsUntilExpiry ?: Long.MAX_VALUE,
            mode = tokenInfo.mode,
            token = tokenInfo.token,
            isLegacy = tokenInfo.isLegacy,
            secondsUntilRotation = tokenInfo.secondsUntilRotation,
        )
    }

    // ==================== Helper Methods ====================

    private fun getOwnerId(principal: Any?): String =
        when (principal) {
            is Jwt -> principal.subject
            is OAuth2User -> principal.name
            else -> throw IllegalStateException("Unknown principal type: ${principal?.javaClass}")
        }

    private fun checkOwnership(
        queueId: UUID,
        authentication: Authentication,
    ) {
        val ownerId = getOwnerId(authentication.principal)
        val queue =
            queueRepository.findById(queueId)
                ?: throw IllegalArgumentException("Queue not found")

        if (queue.ownerId != ownerId) {
            throw IllegalStateException("Not the owner")
        }
    }

    /**
     * Check if user has at least the specified role for a queue.
     * Used for operations that operators can perform (e.g., serving tickets).
     */
    private fun checkAccess(
        queueId: UUID,
        authentication: Authentication,
        requiredRole: MemberRole = MemberRole.OPERATOR,
    ) {
        val userId = getOwnerId(authentication.principal)
        if (!getMyQueuesUseCase.hasAccess(queueId, userId, requiredRole)) {
            throw IllegalStateException("Insufficient permissions")
        }
    }

    // ==================== DTOs ====================

    data class QueueRequest(
        val name: String,
    )

    data class StateRequest(
        val name: String,
        val status: Ticket.TicketStatus,
    )

    data class TicketPageModeRequest(
        val ticketPageMode: Queue.TicketPageMode,
    )

    data class InviteRequest(
        val email: String,
        val role: MemberRole? = MemberRole.OPERATOR,
    )

    data class UpdateRoleRequest(
        val role: MemberRole,
    )

    data class CreateCounterRequest(
        val name: String? = null,
    )

    data class UpdateCounterRequest(
        val name: String? = null,
    )

    data class StartSessionRequest(
        val counterId: UUID,
    )

    data class InviteDTO(
        val id: UUID,
        val queueId: UUID,
        val email: String,
        val role: MemberRole,
        val token: String,
        val status: String,
        val createdAt: Instant,
        val expiresAt: Instant,
    ) {
        companion object {
            fun from(invite: Invite): InviteDTO =
                InviteDTO(
                    id = invite.id,
                    queueId = invite.queueId,
                    email = invite.email,
                    role = invite.role,
                    token = invite.token,
                    status = invite.status.name,
                    createdAt = invite.createdAt,
                    expiresAt = invite.expiresAt,
                )
        }
    }

    data class MemberDTO(
        val id: UUID,
        val queueId: UUID,
        val userId: String,
        val role: MemberRole,
        val joinedAt: Instant,
        val invitedBy: String?,
    ) {
        companion object {
            fun from(member: QueueMember): MemberDTO =
                MemberDTO(
                    id = member.id,
                    queueId = member.queueId,
                    userId = member.userId,
                    role = member.role,
                    joinedAt = member.joinedAt,
                    invitedBy = member.invitedBy,
                )
        }
    }

    data class QueueWithRoleDTO(
        val id: UUID,
        val name: String,
        val ownerId: String,
        val open: Boolean,
        val role: MemberRole,
    ) {
        companion object {
            fun from(queueWithRole: QueueWithRole): QueueWithRoleDTO =
                QueueWithRoleDTO(
                    id = queueWithRole.queue.id,
                    name = queueWithRole.queue.name,
                    ownerId = queueWithRole.queue.ownerId,
                    open = queueWithRole.queue.open,
                    role = queueWithRole.role,
                )
        }
    }

    data class CounterDTO(
        val id: UUID,
        val queueId: UUID,
        val number: Int,
        val name: String?,
        val displayName: String,
        val currentOperatorId: String?,
        val currentTicketId: UUID?,
        val isOccupied: Boolean,
        val isServing: Boolean,
        val createdAt: Instant,
        val updatedAt: Instant,
    ) {
        companion object {
            fun from(counter: Counter): CounterDTO =
                CounterDTO(
                    id = counter.id,
                    queueId = counter.queueId,
                    number = counter.number,
                    name = counter.name,
                    displayName = counter.displayName,
                    currentOperatorId = counter.currentOperatorId,
                    currentTicketId = counter.currentTicketId,
                    isOccupied = counter.isOccupied,
                    isServing = counter.isServing,
                    createdAt = counter.createdAt,
                    updatedAt = counter.updatedAt,
                )
        }
    }

    data class CounterSessionDTO(
        val sessionId: UUID,
        val counterId: UUID,
        val counterNumber: Int,
        val counterDisplayName: String,
        val operatorId: String,
        val startedAt: Instant,
    ) {
        companion object {
            fun from(session: com.example.simplequeue.domain.model.CounterSession, counter: Counter): CounterSessionDTO =
                CounterSessionDTO(
                    sessionId = session.id,
                    counterId = session.counterId,
                    counterNumber = counter.number,
                    counterDisplayName = counter.displayName,
                    operatorId = session.operatorId,
                    startedAt = session.startedAt,
                )
        }
    }

    data class TokenStatusResponse(
        val valid: Boolean,
        val expiresAt: String?,
        val secondsRemaining: Long,
        val mode: String,
        val token: String,
        val isLegacy: Boolean,
        val secondsUntilRotation: Long?,
    )
}
