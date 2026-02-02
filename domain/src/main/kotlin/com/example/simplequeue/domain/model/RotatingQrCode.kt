package com.example.simplequeue.domain.model

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Value object representing a rotating QR code for queue access.
 * Encapsulates the logic for token rotation, expiry, and validation.
 * 
 * This is used when a queue has [AccessTokenMode.ROTATING] to manage
 * the lifecycle of time-limited QR codes that prevent users from
 * copying and reusing codes outside the physical location.
 * 
 * @property token The current token string
 * @property createdAt When this token was created
 * @property expiresAt When this token expires (null for static tokens)
 * @property rotationIntervalMinutes How often the token should rotate
 * @property maxUses Maximum number of uses before expiry (null for unlimited)
 * @property useCount Current number of uses
 */
data class RotatingQrCode(
    val token: String,
    val createdAt: Instant = Instant.now(),
    val expiresAt: Instant? = null,
    val rotationIntervalMinutes: Int = 30,
    val maxUses: Int? = null,
    val useCount: Int = 0,
) {
    init {
        require(rotationIntervalMinutes >= 0) {
            "Rotation interval must be non-negative"
        }
        require(maxUses == null || maxUses > 0) {
            "Max uses must be positive when specified"
        }
        require(useCount >= 0) {
            "Use count must be non-negative"
        }
        expiresAt?.let {
            require(!it.isBefore(createdAt)) {
                "Expiry time must be after creation time"
            }
        }
    }
    /**
     * Check if this QR code token is currently valid.
     * A token is valid if:
     * - It hasn't expired (if expiry is set)
     * - It hasn't exceeded max uses (if set)
     */
    fun isValid(): Boolean {
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            return false
        }
        if (maxUses != null && useCount >= maxUses) {
            return false
        }
        return true
    }

    /**
     * Check if this token needs rotation based on the configured interval.
     * @param lastRotatedAt When the token was last rotated
     */
    fun needsRotation(lastRotatedAt: Instant): Boolean {
        if (rotationIntervalMinutes == 0) return false
        val nextRotation = lastRotatedAt.plus(rotationIntervalMinutes.toLong(), ChronoUnit.MINUTES)
        return Instant.now().isAfter(nextRotation)
    }

    /**
     * Get seconds remaining until expiry.
     * Returns null if no expiry is set.
     */
    fun secondsUntilExpiry(): Long? {
        return expiresAt?.let {
            val seconds = ChronoUnit.SECONDS.between(Instant.now(), it)
            if (seconds < 0) 0 else seconds
        }
    }

    /**
     * Get seconds remaining until next rotation.
     * @param lastRotatedAt When the token was last rotated
     */
    fun secondsUntilRotation(lastRotatedAt: Instant): Long {
        if (rotationIntervalMinutes == 0) return 0
        val nextRotation = lastRotatedAt.plus(rotationIntervalMinutes.toLong(), ChronoUnit.MINUTES)
        val seconds = ChronoUnit.SECONDS.between(Instant.now(), nextRotation)
        return if (seconds < 0) 0 else seconds
    }

    /**
     * Create a new token with incremented use count.
     */
    fun recordUse(): RotatingQrCode = copy(useCount = useCount + 1)

    /**
     * Validate this QR code and return a detailed validation result.
     */
    fun validate(): QrCodeValidationResult {
        return when {
            !isValid() -> {
                when {
                    expiresAt != null && expiresAt.isBefore(Instant.now()) -> QrCodeValidationResult.Expired
                    maxUses != null && useCount >= maxUses -> QrCodeValidationResult.MaxUsesExceeded(maxUses)
                    else -> QrCodeValidationResult.Invalid
                }
            }
            else -> QrCodeValidationResult.Valid(this)
        }
    }

    companion object {
        /**
         * Default rotation interval in minutes for new rotating QR codes.
         */
        const val DEFAULT_ROTATION_MINUTES = 30

        /**
         * Default expiry time in minutes for new rotating QR codes.
         */
        const val DEFAULT_EXPIRY_MINUTES = 60

        /**
         * Create a new rotating QR code with the given configuration.
         * 
         * @param token The token string
         * @param expiryMinutes Minutes until expiry (null for no expiry)
         * @param rotationMinutes Minutes between rotations
         * @param maxUses Maximum uses before expiry (null for unlimited)
         */
        fun create(
            token: String,
            expiryMinutes: Int? = DEFAULT_EXPIRY_MINUTES,
            rotationMinutes: Int = DEFAULT_ROTATION_MINUTES,
            maxUses: Int? = null,
        ): RotatingQrCode {
            val now = Instant.now()
            return RotatingQrCode(
                token = token,
                createdAt = now,
                expiresAt = expiryMinutes?.let { now.plus(it.toLong(), ChronoUnit.MINUTES) },
                rotationIntervalMinutes = rotationMinutes,
                maxUses = maxUses,
                useCount = 0,
            )
        }

        /**
         * Create a static (non-rotating) QR code.
         * These use the legacy qr_code_secret and never expire.
         */
        fun createStatic(token: String): RotatingQrCode =
            RotatingQrCode(
                token = token,
                createdAt = Instant.now(),
                expiresAt = null,
                rotationIntervalMinutes = 0,
                maxUses = null,
                useCount = 0,
            )
    }
}

/**
 * Result of validating a rotating QR code.
 */
sealed class QrCodeValidationResult {
    /**
     * QR code is valid and can be used.
     * @property rotatingQrCode The valid QR code
     */
    data class Valid(val rotatingQrCode: RotatingQrCode) : QrCodeValidationResult()

    /**
     * QR code has expired.
     */
    data object Expired : QrCodeValidationResult()

    /**
     * QR code has exceeded maximum uses.
     */
    data class MaxUsesExceeded(val maxUses: Int) : QrCodeValidationResult()

    /**
     * QR code not found or invalid.
     */
    data object Invalid : QrCodeValidationResult()
}
