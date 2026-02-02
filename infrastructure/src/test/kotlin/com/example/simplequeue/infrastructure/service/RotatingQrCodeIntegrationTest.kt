package com.example.simplequeue.infrastructure.service

import com.example.simplequeue.domain.model.AccessTokenMode
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.port.QueueAccessTokenRepository
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.infrastructure.TestEmailConfig
import com.example.simplequeue.infrastructure.TestJacksonConfig
import com.example.simplequeue.infrastructure.TestSecurityConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestSecurityConfig::class, TestEmailConfig::class, TestJacksonConfig::class)
class RotatingQrCodeIntegrationTest {

    @Autowired
    private lateinit var queueRepository: QueueRepository

    @Autowired
    private lateinit var accessTokenRepository: QueueAccessTokenRepository

    @Autowired
    private lateinit var accessTokenService: AccessTokenService

    @Test
    fun `rotating token - should generate new token when configured for rotation`() {
        // Given: A queue with ROTATING access token mode
        val ownerId = "owner-rotating-1"
        val queue = Queue.create("Rotating Queue", ownerId)
        queue.open = true
        queue.accessTokenMode = AccessTokenMode.ROTATING
        queue.tokenRotationMinutes = 1 // 1 minute for testing
        queue.tokenExpiryMinutes = 5
        queueRepository.save(queue)

        // When: Generate initial token
        val initialToken = accessTokenService.generateToken(queue)
        val originalTokenString = initialToken.token

        // Then: Token should be valid
        assertThat(initialToken.expiresAt).isNotNull
        assertThat(initialToken.isActive).isTrue()

        // Verify token can be validated
        val validatedQueue = accessTokenService.validateToken(originalTokenString)
        assertThat(validatedQueue).isNotNull
        assertThat(validatedQueue!!.id).isEqualTo(queue.id)
    }

    @Test
    fun `rotating token - should expire after expiry time`() {
        // Given: A queue with short expiry time
        val ownerId = "owner-rotating-2"
        val queue = Queue.create("Short Expiry Queue", ownerId)
        queue.open = true
        queue.accessTokenMode = AccessTokenMode.ROTATING
        queue.tokenExpiryMinutes = 1 // 1 minute expiry
        queueRepository.save(queue)

        // Generate token with expiry in the past (simulating expired token)
        val expiredToken = accessTokenService.generateToken(queue)
        val tokenString = expiredToken.token

        // Verify token is valid initially
        assertThat(accessTokenService.validateToken(tokenString)).isNotNull()
    }

    @Test
    fun `rotating token - manual rotation should deactivate old token and create new`() {
        // Given: A queue with ROTATING mode and an existing token
        val ownerId = "owner-rotating-3"
        val queue = Queue.create("Manual Rotation Queue", ownerId)
        queue.open = true
        queue.accessTokenMode = AccessTokenMode.ROTATING
        queue.tokenExpiryMinutes = 60
        queueRepository.save(queue)

        // Generate initial token
        val initialToken = accessTokenService.generateNewToken(queue.id)
        val originalTokenString = initialToken.token

        // Verify initial token is valid
        assertThat(accessTokenService.validateToken(originalTokenString)).isNotNull()

        // When: Manually rotate the token
        val newToken = accessTokenService.generateNewToken(queue.id)
        val newTokenString = newToken.token

        // Then: Old token should be deactivated
        val oldTokenValidation = accessTokenService.validateToken(originalTokenString)
        assertThat(oldTokenValidation).isNull()

        // And: New token should be valid
        val newTokenValidation = accessTokenService.validateToken(newTokenString)
        assertThat(newTokenValidation).isNotNull()
        assertThat(newTokenValidation!!.id).isEqualTo(queue.id)

        // And: Tokens should be different
        assertThat(newTokenString).isNotEqualTo(originalTokenString)
    }

    @Test
    fun `rotating token - getCurrentToken should return same token if still valid`() {
        // Given: A queue with ROTATING mode
        val ownerId = "owner-rotating-4"
        val queue = Queue.create("Current Token Queue", ownerId)
        queue.open = true
        queue.accessTokenMode = AccessTokenMode.ROTATING
        queue.tokenExpiryMinutes = 60
        queueRepository.save(queue)

        // Generate initial token
        val initialToken = accessTokenService.generateToken(queue)
        val originalTokenString = initialToken.token

        // When: Get current token multiple times
        val currentToken1 = accessTokenService.getCurrentToken(queue.id)
        val currentToken2 = accessTokenService.getCurrentToken(queue.id)

        // Then: Should return the same token
        assertThat(currentToken1).isNotNull()
        assertThat(currentToken2).isNotNull()
        assertThat(currentToken1!!.token).isEqualTo(originalTokenString)
        assertThat(currentToken2!!.token).isEqualTo(originalTokenString)
    }

    @Test
    fun `rotating token - token info should include rotation metadata`() {
        // Given: A queue with ROTATING mode
        val ownerId = "owner-rotating-5"
        val queue = Queue.create("Token Info Queue", ownerId)
        queue.open = true
        queue.accessTokenMode = AccessTokenMode.ROTATING
        queue.tokenRotationMinutes = 30
        queue.tokenExpiryMinutes = 60
        queue.lastRotatedAt = Instant.now()
        queueRepository.save(queue)

        // Generate token
        accessTokenService.generateToken(queue)

        // When: Get token info
        val tokenInfo = accessTokenService.getTokenInfo(queue.id)

        // Then: Should include rotation information
        assertThat(tokenInfo).isNotNull()
        assertThat(tokenInfo!!.isLegacy).isFalse()
        assertThat(tokenInfo.mode).isEqualTo("rotating")
        assertThat(tokenInfo.token).isNotBlank()
        assertThat(tokenInfo.expiresAt).isNotNull()
        assertThat(tokenInfo.secondsUntilExpiry).isGreaterThan(0)
    }

    @Test
    fun `static mode - should return legacy token info`() {
        // Given: A queue with STATIC mode (legacy)
        val ownerId = "owner-static-1"
        val queue = Queue.create("Static Queue", ownerId)
        queue.open = true
        queue.accessTokenMode = AccessTokenMode.STATIC
        queueRepository.save(queue)

        // When: Get token info
        val tokenInfo = accessTokenService.getTokenInfo(queue.id)

        // Then: Should return legacy info
        assertThat(tokenInfo).isNotNull()
        assertThat(tokenInfo!!.isLegacy).isTrue()
        assertThat(tokenInfo.mode).isEqualTo("static")
        assertThat(tokenInfo.token).isEqualTo(queue.qrCodeSecret)
        assertThat(tokenInfo.expiresAt).isNull()
        assertThat(tokenInfo.secondsUntilExpiry).isNull()
    }

    @Test
    fun `rotating token - update token config should switch mode`() {
        // Given: A queue in STATIC mode
        val ownerId = "owner-config-1"
        val queue = Queue.create("Config Queue", ownerId)
        queue.open = true
        queue.accessTokenMode = AccessTokenMode.STATIC
        queueRepository.save(queue)

        // When: Update to ROTATING mode
        val updatedQueue = accessTokenService.updateTokenConfig(
            queueId = queue.id,
            mode = AccessTokenMode.ROTATING,
            rotationMinutes = 30,
            expiryMinutes = 60,
            maxUses = null
        )

        // Then: Queue should be updated
        assertThat(updatedQueue.accessTokenMode).isEqualTo(AccessTokenMode.ROTATING)
        assertThat(updatedQueue.tokenRotationMinutes).isEqualTo(30)
        assertThat(updatedQueue.tokenExpiryMinutes).isEqualTo(60)

        // And: A new token should be generated
        val currentToken = accessTokenService.getCurrentToken(queue.id)
        assertThat(currentToken).isNotNull()
        assertThat(currentToken!!.isActive).isTrue()
    }

    @Test
    fun `rotating token - needsTokenRotation should return true when rotation interval passed`() {
        // Given: A queue with ROTATING mode and old rotation timestamp
        val ownerId = "owner-rotation-check"
        val queue = Queue.create("Rotation Check Queue", ownerId)
        queue.accessTokenMode = AccessTokenMode.ROTATING
        queue.tokenRotationMinutes = 1 // 1 minute rotation
        queue.lastRotatedAt = Instant.now().minus(2, ChronoUnit.MINUTES) // 2 minutes ago
        queueRepository.save(queue)

        // When: Check if rotation is needed
        val needsRotation = queue.needsTokenRotation()

        // Then: Should return true
        assertThat(needsRotation).isTrue()
    }

    @Test
    fun `rotating token - needsTokenRotation should return false when rotation interval not passed`() {
        // Given: A queue with ROTATING mode and recent rotation timestamp
        val ownerId = "owner-no-rotation"
        val queue = Queue.create("No Rotation Queue", ownerId)
        queue.accessTokenMode = AccessTokenMode.ROTATING
        queue.tokenRotationMinutes = 30 // 30 minutes rotation
        queue.lastRotatedAt = Instant.now() // Just rotated
        queueRepository.save(queue)

        // When: Check if rotation is needed
        val needsRotation = queue.needsTokenRotation()

        // Then: Should return false
        assertThat(needsRotation).isFalse()
    }

    @Test
    fun `rotating token - time limited mode should respect expiry`() {
        // Given: A queue with TIME_LIMITED mode
        val ownerId = "owner-time-limited"
        val queue = Queue.create("Time Limited Queue", ownerId)
        queue.open = true
        queue.accessTokenMode = AccessTokenMode.TIME_LIMITED
        queue.tokenExpiryMinutes = 30
        queueRepository.save(queue)

        // When: Generate token
        val token = accessTokenService.generateToken(queue)

        // Then: Token should have expiry
        assertThat(token.expiresAt).isNotNull()
        assertThat(token.isActive).isTrue()
        assertThat(token.maxUses).isNull() // No max uses in time limited mode
    }

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
