package com.example.simplequeue.infrastructure.service

import com.example.simplequeue.domain.model.AccessTokenMode
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.QueueAccessToken
import com.example.simplequeue.domain.port.QueueAccessTokenRepository
import com.example.simplequeue.domain.port.QueueRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Service for managing queue access tokens.
 * Handles token generation, validation, and rotation.
 */
@Service
class AccessTokenService(
    private val queueRepository: QueueRepository,
    private val tokenRepository: QueueAccessTokenRepository,
) {
    private val logger = LoggerFactory.getLogger(AccessTokenService::class.java)

    /**
     * Validate an access token and return the associated queue if valid.
     * This method only checks validity - it does NOT consume the token.
     * Call consumeToken() after successfully issuing a ticket.
     *
     * @param token The token string to validate
     * @return The queue if token is valid, null otherwise
     */
    fun validateToken(token: String): Queue? {
        val accessToken = tokenRepository.findByToken(token) ?: return null

        // Check if expired
        if (accessToken.expiresAt?.isBefore(Instant.now()) == true) {
            logger.debug("Token {} expired at {}", token, accessToken.expiresAt)
            return null
        }

        // Check usage limit
        if (accessToken.maxUses != null && accessToken.useCount >= accessToken.maxUses!!) {
            logger.debug("Token {} exceeded max uses ({}/{})", token, accessToken.useCount, accessToken.maxUses)
            return null
        }

        // Check if active
        if (!accessToken.isActive) {
            logger.debug("Token {} is deactivated", token)
            return null
        }

        // Get queue
        val queue = queueRepository.findById(accessToken.queueId)
        if (queue == null) {
            logger.warn("Token {} references non-existent queue {}", token, accessToken.queueId)
            return null
        }

        return queue
    }

    /**
     * Consume a token after successfully issuing a ticket.
     * Increments use count and handles ONE_TIME token rotation.
     *
     * @param token The token string to consume
     * @return true if token was consumed, false if invalid
     */
    fun consumeToken(token: String): Boolean {
        val accessToken = tokenRepository.findByToken(token) ?: return false

        // Verify token is still valid before consuming
        if (!accessToken.isValid()) {
            logger.warn("Attempted to consume invalid token: {}", token)
            return false
        }

        val queue = queueRepository.findById(accessToken.queueId) ?: return false

        // Increment use count
        tokenRepository.incrementUseCount(accessToken.id)
        logger.debug("Incremented use count for token {}", token)

        // For ONE_TIME mode, deactivate the token after use and generate new
        if (queue.accessTokenMode == AccessTokenMode.ONE_TIME) {
            tokenRepository.deactivate(accessToken.id)
            // Generate a new token for next use
            val newToken = generateToken(queue)
            logger.info("ONE_TIME token {} consumed, new token generated: {}", token, newToken.token)
        }

        return true
    }

    /**
     * Get or create the current active token for a queue.
     * For STATIC mode, returns null (use qr_code_secret instead).
     */
    fun getCurrentToken(queueId: UUID): QueueAccessToken? {
        val queue = queueRepository.findById(queueId) ?: return null

        if (queue.accessTokenMode == AccessTokenMode.STATIC) {
            return null
        }

        // Try to find existing valid token
        val existingToken = tokenRepository.findCurrentToken(queueId)
        if (existingToken != null && existingToken.isValid()) {
            return existingToken
        }

        // Generate new token if none exists
        return generateToken(queue)
    }

    /**
     * Generate a new access token for a queue.
     */
    fun generateToken(queue: Queue): QueueAccessToken {
        val expiresAt = when (queue.accessTokenMode) {
            AccessTokenMode.STATIC -> null
            AccessTokenMode.ROTATING -> Instant.now().plus(queue.tokenExpiryMinutes.toLong(), ChronoUnit.MINUTES)
            AccessTokenMode.ONE_TIME -> Instant.now().plus(queue.tokenExpiryMinutes.toLong(), ChronoUnit.MINUTES)
            AccessTokenMode.TIME_LIMITED -> Instant.now().plus(queue.tokenExpiryMinutes.toLong(), ChronoUnit.MINUTES)
        }

        val maxUses = when (queue.accessTokenMode) {
            AccessTokenMode.ONE_TIME -> 1
            else -> queue.tokenMaxUses
        }

        val token = QueueAccessToken.create(
            queueId = queue.id,
            expiresAt = expiresAt,
            maxUses = maxUses,
        )

        tokenRepository.save(token)
        logger.info("Generated new access token for queue {}: expires={}, maxUses={}", 
            queue.id, expiresAt, maxUses)

        return token
    }

    /**
     * Generate a new token explicitly (admin action).
     */
    fun generateNewToken(queueId: UUID): QueueAccessToken {
        val queue = queueRepository.findById(queueId)
            ?: throw IllegalArgumentException("Queue not found")

        if (queue.accessTokenMode == AccessTokenMode.STATIC) {
            throw IllegalStateException("Cannot generate tokens for queues in STATIC mode")
        }

        // Deactivate old tokens if rotating
        if (queue.accessTokenMode == AccessTokenMode.ROTATING) {
            tokenRepository.deactivateOldTokens(queueId)
        }

        return generateToken(queue)
    }

    /**
     * Deactivate a specific token.
     */
    fun deactivateToken(tokenId: UUID) {
        tokenRepository.deactivate(tokenId)
        logger.info("Deactivated token {}", tokenId)
    }

    /**
     * Get all tokens for a queue (for admin view).
     */
    fun getTokensForQueue(queueId: UUID): List<QueueAccessToken> {
        return tokenRepository.findByQueueId(queueId)
    }

    /**
     * Update queue token configuration.
     */
    fun updateTokenConfig(
        queueId: UUID,
        mode: AccessTokenMode,
        rotationMinutes: Int,
        expiryMinutes: Int,
        maxUses: Int?,
    ): Queue {
        val queue = queueRepository.findById(queueId)
            ?: throw IllegalArgumentException("Queue not found")

        val updatedQueue = queue.updateTokenConfig(
            mode = mode,
            rotationMinutes = rotationMinutes,
            expiryMinutes = expiryMinutes,
            maxUses = maxUses,
        )

        queueRepository.save(updatedQueue)
        logger.info("Updated token config for queue {}: mode={}, rotation={}min, expiry={}min, maxUses={}", 
            queueId, mode, rotationMinutes, expiryMinutes, maxUses)

        // If switching from STATIC to dynamic mode, generate initial token
        if (queue.accessTokenMode == AccessTokenMode.STATIC && mode != AccessTokenMode.STATIC) {
            generateToken(updatedQueue)
        }

        return updatedQueue
    }

    /**
     * Scheduled task to rotate tokens for queues that need it.
     * Runs every minute.
     */
    @Scheduled(fixedRate = 60000)
    fun rotateTokens() {
        val queuesNeedingRotation = queueRepository.findQueuesNeedingTokenRotation()

        for (queue in queuesNeedingRotation) {
            try {
                // Deactivate old tokens
                tokenRepository.deactivateOldTokens(queue.id)

                // Generate new token
                val newToken = generateToken(queue)

                // Update last rotated timestamp
                queue.lastRotatedAt = Instant.now()
                queueRepository.save(queue)

                logger.info("Rotated token for queue {}, new token expires at {}", 
                    queue.id, newToken.expiresAt)
            } catch (e: Exception) {
                logger.error("Failed to rotate token for queue {}: {}", queue.id, e.message, e)
            }
        }

        if (queuesNeedingRotation.isNotEmpty()) {
            logger.info("Rotated tokens for {} queues", queuesNeedingRotation.size)
        }
    }

    /**
     * Get the join URL for a queue, using appropriate token.
     */
    fun getJoinUrl(queueId: UUID, baseUrl: String): String {
        val queue = queueRepository.findById(queueId)
            ?: throw IllegalArgumentException("Queue not found")

        return if (queue.accessTokenMode == AccessTokenMode.STATIC) {
            // Legacy: use queue ID and secret
            "$baseUrl/public/q/$queueId/join?secret=${queue.qrCodeSecret}"
        } else {
            // Dynamic: use token
            val token = getCurrentToken(queueId)
                ?: throw IllegalStateException("No valid token available for queue")
            "$baseUrl/q/${token.token}"
        }
    }

    /**
     * Get token info for QR code display, including time until next rotation.
     */
    fun getTokenInfo(queueId: UUID): TokenInfo? {
        val queue = queueRepository.findById(queueId) ?: return null

        if (queue.accessTokenMode == AccessTokenMode.STATIC) {
            return TokenInfo(
                token = queue.qrCodeSecret,
                isLegacy = true,
                mode = "static",
                expiresAt = null,
                secondsUntilExpiry = null,
                secondsUntilRotation = null,
            )
        }

        val token = getCurrentToken(queueId) ?: return null

        val secondsUntilExpiry = token.expiresAt?.let {
            Duration.between(Instant.now(), it).seconds.coerceAtLeast(0)
        }

        val secondsUntilRotation = if (queue.accessTokenMode == AccessTokenMode.ROTATING && queue.tokenRotationMinutes > 0) {
            val nextRotation = queue.lastRotatedAt.plus(queue.tokenRotationMinutes.toLong(), ChronoUnit.MINUTES)
            Duration.between(Instant.now(), nextRotation).seconds.coerceAtLeast(0)
        } else null

        return TokenInfo(
            token = token.token,
            isLegacy = false,
            mode = queue.accessTokenMode.name.lowercase(),
            expiresAt = token.expiresAt,
            secondsUntilExpiry = secondsUntilExpiry,
            secondsUntilRotation = secondsUntilRotation,
        )
    }

    data class TokenInfo(
        val token: String,
        val isLegacy: Boolean,
        val mode: String,
        val expiresAt: Instant?,
        val secondsUntilExpiry: Long?,
        val secondsUntilRotation: Long?,
    )
}
