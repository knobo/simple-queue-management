package com.example.simplequeue.domain.model

import java.time.Instant
import java.util.UUID

data class Queue(
    val id: UUID,
    val name: String,
    val ownerId: String,
    var open: Boolean,
    var qrCodeSecret: String,
    var qrCodeType: QrCodeType,
    var lastRotatedAt: Instant,
    var ticketPageMode: TicketPageMode = TicketPageMode.BOTH,

    // Extended fields
    val description: String? = null,
    val locationHint: String? = null,
    val estimatedServiceTimeMinutes: Int? = null,
    val organizationId: UUID? = null,

    // Access token configuration
    var accessTokenMode: AccessTokenMode = AccessTokenMode.STATIC,
    var tokenRotationMinutes: Int = 0,
    var tokenExpiryMinutes: Int = 60,
    var tokenMaxUses: Int? = null,

    // Kiosk mode configuration
    var autoCloseSeconds: Int = 5,

    // Display token for public display screens
    var displayToken: String? = null,
) {
    enum class QrCodeType {
        SINGLE_USE, DAILY, PERSISTENT
    }

    enum class TicketPageMode {
        QR_ONLY, BUTTON_ONLY, BOTH
    }

    companion object {
        fun create(name: String, ownerId: String): Queue {
            return Queue(
                id = UUID.randomUUID(),
                name = name,
                ownerId = ownerId,
                open = false,
                qrCodeSecret = UUID.randomUUID().toString(),
                qrCodeType = QrCodeType.PERSISTENT,
                lastRotatedAt = Instant.now(),
                ticketPageMode = TicketPageMode.BOTH,
                description = null,
                locationHint = null,
                estimatedServiceTimeMinutes = null,
                organizationId = null,
                accessTokenMode = AccessTokenMode.STATIC,
                tokenRotationMinutes = 0,
                tokenExpiryMinutes = 60,
                tokenMaxUses = null,
                autoCloseSeconds = 5,
                displayToken = UUID.randomUUID().toString().replace("-", ""),
            )
        }
    }

    fun rotateQrCode() {
        this.qrCodeSecret = UUID.randomUUID().toString()
        this.lastRotatedAt = Instant.now()
    }

    fun updateDescription(
        description: String? = this.description,
        locationHint: String? = this.locationHint,
        estimatedServiceTimeMinutes: Int? = this.estimatedServiceTimeMinutes,
    ): Queue = copy(
        description = description,
        locationHint = locationHint,
        estimatedServiceTimeMinutes = estimatedServiceTimeMinutes,
    )

    /**
     * Check if this queue uses dynamic access tokens.
     */
    fun usesDynamicTokens(): Boolean = accessTokenMode != AccessTokenMode.STATIC

    /**
     * Check if tokens need rotation based on the configured interval.
     */
    fun needsTokenRotation(): Boolean {
        if (accessTokenMode != AccessTokenMode.ROTATING) return false
        if (tokenRotationMinutes <= 0) return false
        val rotationInterval = java.time.Duration.ofMinutes(tokenRotationMinutes.toLong())
        return lastRotatedAt.plus(rotationInterval).isBefore(Instant.now())
    }

    /**
     * Update token configuration.
     */
    fun updateTokenConfig(
        mode: AccessTokenMode = this.accessTokenMode,
        rotationMinutes: Int = this.tokenRotationMinutes,
        expiryMinutes: Int = this.tokenExpiryMinutes,
        maxUses: Int? = this.tokenMaxUses,
    ): Queue = copy(
        accessTokenMode = mode,
        tokenRotationMinutes = rotationMinutes,
        tokenExpiryMinutes = expiryMinutes,
        tokenMaxUses = maxUses,
    )

    /**
     * Regenerate the display token.
     */
    fun regenerateDisplayToken() {
        this.displayToken = UUID.randomUUID().toString().replace("-", "")
    }
}
