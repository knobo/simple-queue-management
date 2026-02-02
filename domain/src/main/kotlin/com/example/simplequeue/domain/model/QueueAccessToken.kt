package com.example.simplequeue.domain.model

import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

/**
 * Represents an access token for joining a queue.
 * These tokens can be embedded in QR codes and validated when customers scan them.
 */
data class QueueAccessToken(
    val id: UUID,
    val queueId: UUID,
    val token: String,
    val expiresAt: Instant?,
    val maxUses: Int?,
    var useCount: Int = 0,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
) {
    companion object {
        private val secureRandom = SecureRandom()
        private const val TOKEN_LENGTH = 24
        private const val TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"

        /**
         * Generate a cryptographically secure random token.
         * Uses a character set that avoids ambiguous characters (0, O, 1, l, I).
         */
        fun generateSecureToken(): String {
            return (1..TOKEN_LENGTH)
                .map { TOKEN_CHARS[secureRandom.nextInt(TOKEN_CHARS.length)] }
                .joinToString("")
        }

        /**
         * Create a new access token for a queue.
         */
        fun create(
            queueId: UUID,
            expiresAt: Instant? = null,
            maxUses: Int? = null,
        ): QueueAccessToken {
            return QueueAccessToken(
                id = UUID.randomUUID(),
                queueId = queueId,
                token = generateSecureToken(),
                expiresAt = expiresAt,
                maxUses = maxUses,
                useCount = 0,
                isActive = true,
                createdAt = Instant.now(),
            )
        }
    }

    /**
     * Check if this token is valid for use.
     */
    fun isValid(): Boolean {
        if (!isActive) return false
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) return false
        if (maxUses != null && useCount >= maxUses) return false
        return true
    }

    /**
     * Record a use of this token.
     */
    fun recordUse() {
        useCount++
    }

    /**
     * Deactivate this token.
     */
    fun deactivate() {
        isActive = false
    }
}

/**
 * Access token mode for a queue.
 * Determines how tokens are generated and validated.
 */
enum class AccessTokenMode {
    /**
     * Legacy mode - uses qr_code_secret directly, no token table.
     * Maintains backwards compatibility with existing QR codes.
     */
    STATIC,

    /**
     * Tokens rotate automatically on a schedule.
     * Old tokens are deactivated when new ones are generated.
     */
    ROTATING,

    /**
     * Each token can only be used once.
     * A new token is generated after each use.
     */
    ONE_TIME,

    /**
     * Tokens expire after a configured time period.
     * Multiple tokens can be active simultaneously.
     */
    TIME_LIMITED,
}
