package com.example.simplequeue.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName
import java.time.Instant
import java.time.temporal.ChronoUnit

@DisplayName("RotatingQrCode Tests")
class RotatingQrCodeTest {

    @Test
    @DisplayName("Static QR code should always be valid")
    fun `static QR code should always be valid`() {
        // Given
        val staticQr = RotatingQrCode.createStatic("static-secret")

        // Then
        assertThat(staticQr.isValid()).isTrue()
        assertThat(staticQr.expiresAt).isNull()
        assertThat(staticQr.rotationIntervalMinutes).isEqualTo(0)
    }

    @Test
    @DisplayName("Rotating QR code with future expiry should be valid")
    fun `rotating QR code with future expiry should be valid`() {
        // Given
        val rotatingQr = RotatingQrCode.create(
            token = "rotating-token",
            expiryMinutes = 30,
            rotationMinutes = 10,
        )

        // Then
        assertThat(rotatingQr.isValid()).isTrue()
        assertThat(rotatingQr.expiresAt).isNotNull()
        assertThat(rotatingQr.rotationIntervalMinutes).isEqualTo(10)
    }

    @Test
    @DisplayName("QR code with past expiry should be invalid")
    fun `QR code with past expiry should be invalid`() {
        // Given
        val expiredQr = RotatingQrCode(
            token = "expired-token",
            createdAt = Instant.now().minus(2, ChronoUnit.HOURS),
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS),
            rotationIntervalMinutes = 30,
        )

        // Then
        assertThat(expiredQr.isValid()).isFalse()
    }

    @Test
    @DisplayName("QR code within max uses should be valid")
    fun `QR code within max uses should be valid`() {
        // Given
        val qrCode = RotatingQrCode.create(
            token = "limited-token",
            maxUses = 5,
        ).copy(useCount = 3)

        // Then
        assertThat(qrCode.isValid()).isTrue()
        assertThat(qrCode.useCount).isEqualTo(3)
    }

    @Test
    @DisplayName("QR code exceeding max uses should be invalid")
    fun `QR code exceeding max uses should be invalid`() {
        // Given
        val qrCode = RotatingQrCode.create(
            token = "exhausted-token",
            maxUses = 3,
        ).copy(useCount = 3)

        // Then
        assertThat(qrCode.isValid()).isFalse()
    }

    @Test
    @DisplayName("should calculate seconds until expiry correctly")
    fun `should calculate seconds until expiry correctly`() {
        // Given
        val expiryMinutes = 5
        val qrCode = RotatingQrCode.create(
            token = "expiring-token",
            expiryMinutes = expiryMinutes,
        )

        // When
        val secondsRemaining = qrCode.secondsUntilExpiry()

        // Then
        assertThat(secondsRemaining).isNotNull()
        assertThat(secondsRemaining).isGreaterThan(((expiryMinutes - 1) * 60).toLong())
        assertThat(secondsRemaining).isLessThanOrEqualTo((expiryMinutes * 60).toLong())
    }

    @Test
    @DisplayName("static QR code should have null seconds until expiry")
    fun `static QR code should have null seconds until expiry`() {
        // Given
        val staticQr = RotatingQrCode.createStatic("static-secret")

        // When
        val secondsRemaining = staticQr.secondsUntilExpiry()

        // Then
        assertThat(secondsRemaining).isNull()
    }

    @Test
    @DisplayName("expired QR code should return zero seconds until expiry")
    fun `expired QR code should return zero seconds until expiry`() {
        // Given
        val expiredQr = RotatingQrCode(
            token = "expired-token",
            createdAt = Instant.now().minus(2, ChronoUnit.HOURS),
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS),
            rotationIntervalMinutes = 30,
        )

        // When
        val secondsRemaining = expiredQr.secondsUntilExpiry()

        // Then
        assertThat(secondsRemaining).isEqualTo(0)
    }

    @Test
    @DisplayName("should calculate seconds until rotation correctly")
    fun `should calculate seconds until rotation correctly`() {
        // Given
        val rotationMinutes = 10
        val qrCode = RotatingQrCode.create(
            token = "rotating-token",
            rotationMinutes = rotationMinutes,
        )
        val lastRotatedAt = Instant.now()

        // When
        val secondsUntilRotation = qrCode.secondsUntilRotation(lastRotatedAt)

        // Then
        assertThat(secondsUntilRotation).isGreaterThan(((rotationMinutes - 1) * 60).toLong())
        assertThat(secondsUntilRotation).isLessThanOrEqualTo((rotationMinutes * 60).toLong())
    }

    @Test
    @DisplayName("should detect when rotation is needed")
    fun `should detect when rotation is needed`() {
        // Given
        val qrCode = RotatingQrCode.create(
            token = "needs-rotation",
            rotationMinutes = 5,
        )
        val lastRotatedAt = Instant.now().minus(10, ChronoUnit.MINUTES)

        // Then
        assertThat(qrCode.needsRotation(lastRotatedAt)).isTrue()
    }

    @Test
    @DisplayName("should detect when rotation is not needed")
    fun `should detect when rotation is not needed`() {
        // Given
        val qrCode = RotatingQrCode.create(
            token = "fresh-token",
            rotationMinutes = 30,
        )
        val lastRotatedAt = Instant.now()

        // Then
        assertThat(qrCode.needsRotation(lastRotatedAt)).isFalse()
    }

    @Test
    @DisplayName("recordUse should increment use count")
    fun `recordUse should increment use count`() {
        // Given
        val qrCode = RotatingQrCode.create(
            token = "usage-token",
            maxUses = 5,
        )
        assertThat(qrCode.useCount).isEqualTo(0)

        // When
        val afterFirstUse = qrCode.recordUse()
        val afterSecondUse = afterFirstUse.recordUse()

        // Then
        assertThat(afterFirstUse.useCount).isEqualTo(1)
        assertThat(afterSecondUse.useCount).isEqualTo(2)
    }

    @Test
    @DisplayName("should use default values when creating rotating QR code")
    fun `should use default values when creating rotating QR code`() {
        // Given
        val qrCode = RotatingQrCode.create(token = "default-token")

        // Then
        assertThat(qrCode.rotationIntervalMinutes).isEqualTo(RotatingQrCode.DEFAULT_ROTATION_MINUTES)
        assertThat(qrCode.expiresAt).isNotNull()
        assertThat(qrCode.maxUses).isNull()
        assertThat(qrCode.useCount).isEqualTo(0)
    }

    @Test
    @DisplayName("should handle null expiry for non-expiring tokens")
    fun `should handle null expiry for non-expiring tokens`() {
        // Given
        val qrCode = RotatingQrCode.create(
            token = "no-expiry-token",
            expiryMinutes = null,
        )

        // Then
        assertThat(qrCode.expiresAt).isNull()
        assertThat(qrCode.isValid()).isTrue()
        assertThat(qrCode.secondsUntilExpiry()).isNull()
    }

    @Test
    @DisplayName("should handle exactly at max uses boundary")
    fun `should handle exactly at max uses boundary`() {
        // Given - exactly at max uses
        val atLimit = RotatingQrCode.create(
            token = "at-limit",
            maxUses = 3,
        ).copy(useCount = 2)

        // Then - should still be valid (useCount < maxUses)
        assertThat(atLimit.isValid()).isTrue()

        // Given - exactly at max uses
        val atLimitExact = atLimit.recordUse()

        // Then - should still be valid (useCount == maxUses is the last allowed use)
        // Note: The validation checks if useCount >= maxUses, so at exactly maxUses it's invalid
        // This is because after recording the use, it becomes invalid
        assertThat(atLimitExact.isValid()).isFalse()
    }

    @Test
    @DisplayName("should throw exception for negative rotation interval")
    fun `should throw exception for negative rotation interval`() {
        assertThatThrownBy {
            RotatingQrCode.create(
                token = "invalid",
                rotationMinutes = -1,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Rotation interval must be non-negative")
    }

    @Test
    @DisplayName("should throw exception for zero max uses")
    fun `should throw exception for zero max uses`() {
        assertThatThrownBy {
            RotatingQrCode.create(
                token = "invalid",
                maxUses = 0,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Max uses must be positive when specified")
    }

    @Test
    @DisplayName("should throw exception for negative use count")
    fun `should throw exception for negative use count`() {
        assertThatThrownBy {
            RotatingQrCode(
                token = "invalid",
                useCount = -1,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Use count must be non-negative")
    }

    @Test
    @DisplayName("should throw exception for expiry before creation")
    fun `should throw exception for expiry before creation`() {
        val now = Instant.now()
        assertThatThrownBy {
            RotatingQrCode(
                token = "invalid",
                createdAt = now,
                expiresAt = now.minus(1, ChronoUnit.HOURS),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Expiry time must be after creation time")
    }

    @Test
    @DisplayName("should validate and return correct result")
    fun `should validate and return correct result`() {
        // Given - valid token
        val validQr = RotatingQrCode.create(token = "valid")

        // When
        val result = validQr.validate()

        // Then
        assertThat(result).isInstanceOf(QrCodeValidationResult.Valid::class.java)
        assertThat((result as QrCodeValidationResult.Valid).rotatingQrCode).isEqualTo(validQr)
    }

    @Test
    @DisplayName("should validate expired token correctly")
    fun `should validate expired token correctly`() {
        // Given - expired token
        val expiredQr = RotatingQrCode(
            token = "expired",
            createdAt = Instant.now().minus(2, ChronoUnit.HOURS),
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS),
            rotationIntervalMinutes = 30,
        )

        // When
        val result = expiredQr.validate()

        // Then
        assertThat(result).isInstanceOf(QrCodeValidationResult.Expired::class.java)
    }

    @Test
    @DisplayName("should validate max uses exceeded correctly")
    fun `should validate max uses exceeded correctly`() {
        // Given - max uses exceeded
        val maxUsesQr = RotatingQrCode.create(
            token = "max-uses",
            maxUses = 2,
        ).copy(useCount = 2)

        // When
        val result = maxUsesQr.validate()

        // Then
        assertThat(result).isInstanceOf(QrCodeValidationResult.MaxUsesExceeded::class.java)
        assertThat((result as QrCodeValidationResult.MaxUsesExceeded).maxUses).isEqualTo(2)
    }

    @Test
    @DisplayName("should handle zero rotation interval for static tokens")
    fun `should handle zero rotation interval for static tokens`() {
        // Given
        val staticQr = RotatingQrCode(
            token = "static",
            rotationIntervalMinutes = 0,
        )

        // Then
        assertThat(staticQr.rotationIntervalMinutes).isEqualTo(0)
        assertThat(staticQr.needsRotation(Instant.now())).isFalse()
        assertThat(staticQr.secondsUntilRotation(Instant.now())).isEqualTo(0)
    }

    @Test
    @DisplayName("should calculate seconds until rotation at boundary")
    fun `should calculate seconds until rotation at boundary`() {
        // Given - exactly at rotation time
        val qrCode = RotatingQrCode.create(
            token = "boundary",
            rotationMinutes = 5,
        )
        val lastRotatedAt = Instant.now().minus(5, ChronoUnit.MINUTES)

        // When
        val secondsUntil = qrCode.secondsUntilRotation(lastRotatedAt)

        // Then - should be 0 or very close to 0
        assertThat(secondsUntil).isLessThanOrEqualTo(1)
    }
}
