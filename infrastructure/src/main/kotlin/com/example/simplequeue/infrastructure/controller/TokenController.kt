package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.infrastructure.service.AccessTokenService
import com.example.simplequeue.application.usecase.IssueTicketUseCase
import com.example.simplequeue.domain.model.AccessTokenMode
import com.example.simplequeue.domain.model.QueueAccessToken
import com.example.simplequeue.domain.port.QueueRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Controller for token-based queue access.
 * Handles both public token-based join pages and admin token management.
 */
@Controller
class TokenController(
    private val accessTokenService: AccessTokenService,
    private val issueTicketUseCase: IssueTicketUseCase,
    private val queueRepository: QueueRepository,
    private val sendTicketEmailUseCase: com.example.simplequeue.application.usecase.SendTicketEmailUseCase,
) {
    private val logger = LoggerFactory.getLogger(TokenController::class.java)

    /**
     * Public endpoint: Join queue using access token.
     * GET /q/{token}
     * 
     * @param kiosk When true, enables kiosk mode (auto-close on ticket page)
     */
    @GetMapping("/q/{token}")
    fun joinQueueWithToken(
        @PathVariable token: String,
        @RequestParam(defaultValue = "false") kiosk: Boolean,
        model: Model,
    ): String {
        val queue = accessTokenService.validateToken(token)

        if (queue == null) {
            logger.warn("Invalid or expired token: {}", token)
            model.addAttribute("error", "Invalid or expired QR code. Please scan a new one.")
            return "token-error"
        }

        model.addAttribute("queue", queue)
        model.addAttribute("queueId", queue.id)
        model.addAttribute("token", token)
        model.addAttribute("kioskMode", kiosk)
        return "join-queue-token"
    }

    /**
     * Public endpoint: Submit ticket request with token.
     * POST /q/{token}/ticket
     * 
     * @param kiosk When true, redirects to ticket page with kiosk mode enabled
     */
    @PostMapping("/q/{token}/ticket")
    fun issueTicketWithToken(
        @PathVariable token: String,
        @RequestParam("name", required = false) name: String?,
        @RequestParam("email", required = false) email: String?,
        @RequestParam(defaultValue = "false") kiosk: Boolean,
        model: Model,
    ): String {
        val queue = accessTokenService.validateToken(token)

        if (queue == null) {
            logger.warn("Invalid or expired token on ticket submit: {}", token)
            model.addAttribute("error", "Invalid or expired QR code. Please scan a new one.")
            return "token-error"
        }

        return try {
            val cleanEmail = email?.takeIf { it.isNotBlank() }
            val ticket = issueTicketUseCase.executeWithToken(queue, name, cleanEmail)
            
            // Consume the token after successful ticket issuance
            // This increments use_count and handles ONE_TIME token rotation
            accessTokenService.consumeToken(token)
            
            // Auto-send email if provided
            if (!cleanEmail.isNullOrBlank()) {
                try {
                    sendTicketEmailUseCase.execute(ticket.id, cleanEmail)
                    logger.info("Auto-sent ticket email to {} for ticket {}", cleanEmail, ticket.id)
                } catch (e: Exception) {
                    logger.warn("Failed to auto-send ticket email to {}: {}", cleanEmail, e.message)
                    // Don't fail the request if email sending fails
                }
            }
            
            // Include kiosk parameter in redirect if enabled
            val redirectUrl = if (kiosk) {
                "redirect:/public/tickets/${ticket.id}?kiosk=true"
            } else {
                "redirect:/public/tickets/${ticket.id}"
            }
            redirectUrl
        } catch (e: IllegalStateException) {
            logger.warn("Queue {} is closed", queue.id)
            model.addAttribute("error", e.message ?: "Queue is closed")
            model.addAttribute("queue", queue)
            model.addAttribute("kioskMode", kiosk)
            return "join-queue-token"
        }
    }
}

/**
 * REST API controller for admin token management.
 */
@RestController
@RequestMapping("/api/queues/{queueId}/tokens")
class TokenApiController(
    private val accessTokenService: AccessTokenService,
    private val queueRepository: QueueRepository,
) {
    private val logger = LoggerFactory.getLogger(TokenApiController::class.java)

    /**
     * Get current active token for QR display.
     */
    @GetMapping("/current")
    fun getCurrentToken(
        @PathVariable queueId: UUID,
        @AuthenticationPrincipal user: OAuth2User,
    ): ResponseEntity<TokenResponse> {
        val queue = queueRepository.findById(queueId)
            ?: return ResponseEntity.notFound().build()

        // Verify ownership (simplified - should check organization membership too)
        if (queue.ownerId != user.name) {
            return ResponseEntity.status(403).build()
        }

        if (queue.accessTokenMode == AccessTokenMode.STATIC) {
            // For static mode, return the legacy secret
            return ResponseEntity.ok(TokenResponse(
                token = queue.qrCodeSecret,
                queueId = queueId,
                mode = "static",
                expiresAt = null,
                maxUses = null,
                useCount = 0,
                isLegacy = true,
            ))
        }

        val token = accessTokenService.getCurrentToken(queueId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(TokenResponse(
            token = token.token,
            queueId = queueId,
            mode = queue.accessTokenMode.name.lowercase(),
            expiresAt = token.expiresAt?.toEpochMilli(),
            maxUses = token.maxUses,
            useCount = token.useCount,
            isLegacy = false,
            secondsUntilExpiry = token.expiresAt?.let { 
                Duration.between(Instant.now(), it).seconds.coerceAtLeast(0)
            },
        ))
    }

    /**
     * Generate a new token (manual refresh).
     */
    @PostMapping
    fun generateToken(
        @PathVariable queueId: UUID,
        @AuthenticationPrincipal user: OAuth2User,
    ): ResponseEntity<TokenResponse> {
        val queue = queueRepository.findById(queueId)
            ?: return ResponseEntity.notFound().build()

        if (queue.ownerId != user.name) {
            return ResponseEntity.status(403).build()
        }

        return try {
            val token = accessTokenService.generateNewToken(queueId)
            ResponseEntity.ok(TokenResponse(
                token = token.token,
                queueId = queueId,
                mode = queue.accessTokenMode.name.lowercase(),
                expiresAt = token.expiresAt?.toEpochMilli(),
                maxUses = token.maxUses,
                useCount = token.useCount,
                isLegacy = false,
                secondsUntilExpiry = token.expiresAt?.let { 
                    Duration.between(Instant.now(), it).seconds.coerceAtLeast(0)
                },
            ))
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().build()
        }
    }

    /**
     * Deactivate a specific token.
     */
    @DeleteMapping("/{tokenId}")
    fun deactivateToken(
        @PathVariable queueId: UUID,
        @PathVariable tokenId: UUID,
        @AuthenticationPrincipal user: OAuth2User,
    ): ResponseEntity<Void> {
        val queue = queueRepository.findById(queueId)
            ?: return ResponseEntity.notFound().build()

        if (queue.ownerId != user.name) {
            return ResponseEntity.status(403).build()
        }

        accessTokenService.deactivateToken(tokenId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Get all tokens for a queue (admin view).
     */
    @GetMapping
    fun getAllTokens(
        @PathVariable queueId: UUID,
        @AuthenticationPrincipal user: OAuth2User,
    ): ResponseEntity<List<TokenResponse>> {
        val queue = queueRepository.findById(queueId)
            ?: return ResponseEntity.notFound().build()

        if (queue.ownerId != user.name) {
            return ResponseEntity.status(403).build()
        }

        val tokens = accessTokenService.getTokensForQueue(queueId).map { token ->
            TokenResponse(
                id = token.id,
                token = token.token,
                queueId = queueId,
                mode = queue.accessTokenMode.name.lowercase(),
                expiresAt = token.expiresAt?.toEpochMilli(),
                maxUses = token.maxUses,
                useCount = token.useCount,
                isActive = token.isActive,
                isLegacy = false,
                createdAt = token.createdAt.toEpochMilli(),
            )
        }

        return ResponseEntity.ok(tokens)
    }

    /**
     * Update token configuration for a queue.
     */
    @PutMapping("/config")
    fun updateTokenConfig(
        @PathVariable queueId: UUID,
        @RequestBody request: TokenConfigRequest,
        @AuthenticationPrincipal user: OAuth2User,
    ): ResponseEntity<TokenConfigResponse> {
        val queue = queueRepository.findById(queueId)
            ?: return ResponseEntity.notFound().build()

        if (queue.ownerId != user.name) {
            return ResponseEntity.status(403).build()
        }

        val mode = try {
            AccessTokenMode.valueOf(request.mode.uppercase())
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }

        val updatedQueue = accessTokenService.updateTokenConfig(
            queueId = queueId,
            mode = mode,
            rotationMinutes = request.rotationMinutes ?: 0,
            expiryMinutes = request.expiryMinutes ?: 60,
            maxUses = request.maxUses,
        )

        return ResponseEntity.ok(TokenConfigResponse(
            mode = updatedQueue.accessTokenMode.name.lowercase(),
            rotationMinutes = updatedQueue.tokenRotationMinutes,
            expiryMinutes = updatedQueue.tokenExpiryMinutes,
            maxUses = updatedQueue.tokenMaxUses,
        ))
    }

    data class TokenResponse(
        val id: UUID? = null,
        val token: String,
        val queueId: UUID,
        val mode: String,
        val expiresAt: Long?,
        val maxUses: Int?,
        val useCount: Int,
        val isActive: Boolean = true,
        val isLegacy: Boolean,
        val createdAt: Long? = null,
        val secondsUntilExpiry: Long? = null,
    )

    data class TokenConfigRequest(
        val mode: String,
        val rotationMinutes: Int?,
        val expiryMinutes: Int?,
        val maxUses: Int?,
    )

    data class TokenConfigResponse(
        val mode: String,
        val rotationMinutes: Int,
        val expiryMinutes: Int,
        val maxUses: Int?,
    )
}
