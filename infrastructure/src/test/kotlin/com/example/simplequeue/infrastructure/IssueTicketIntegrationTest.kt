package com.example.simplequeue.infrastructure

import com.example.simplequeue.application.usecase.IssueTicketUseCase
import com.example.simplequeue.domain.model.AccessTokenMode
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.port.QueueAccessTokenRepository
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.TicketRepository
import com.example.simplequeue.infrastructure.service.AccessTokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestSecurityConfig::class, TestEmailConfig::class, TestJacksonConfig::class)
class IssueTicketIntegrationTest {
    @Autowired
    private lateinit var issueTicketUseCase: IssueTicketUseCase

    @Autowired
    private lateinit var queueRepository: QueueRepository

    @Autowired
    private lateinit var ticketRepository: TicketRepository

    @Autowired
    private lateinit var accessTokenRepository: QueueAccessTokenRepository

    @Autowired
    private lateinit var accessTokenService: AccessTokenService

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    @Test
    fun shouldIssueTicketWhenQueueIsOpenAndSecretIsValid() {
        // Given
        val ownerId = "owner-1"
        val queue = Queue.create("Barbershop", ownerId)
        queue.open = true
        queueRepository.save(queue)
        val secret = queue.qrCodeSecret

        // When
        val ticket = issueTicketUseCase.execute(queue.id, secret)

        // Then
        assertThat(ticket).isNotNull
        assertThat(ticket.number).isEqualTo(1)
        assertThat(ticket.queueId).isEqualTo(queue.id)

        val saved = ticketRepository.findById(ticket.id)
        assertThat(saved).isNotNull
        assertThat(saved?.number).isEqualTo(1)
    }

    @Test
    fun oneTimeToken_cannotBeUsedTwice() {
        // Given: A queue with ONE_TIME access token mode
        val ownerId = "owner-onetime-1"
        val queue = Queue.create("One-Time Queue", ownerId)
        queue.open = true
        queue.accessTokenMode = AccessTokenMode.ONE_TIME
        queue.tokenExpiryMinutes = 60
        queueRepository.save(queue)

        // Generate an initial token
        val initialToken = accessTokenService.generateToken(queue)
        val tokenString = initialToken.token

        // First use: validate and consume token
        val queueFromToken1 = accessTokenService.validateToken(tokenString)
        assertThat(queueFromToken1).isNotNull
        assertThat(queueFromToken1!!.id).isEqualTo(queue.id)

        // Issue ticket and consume token
        val ticket1 = issueTicketUseCase.executeWithToken(queueFromToken1, "Customer 1", null)
        assertThat(ticket1).isNotNull
        
        val consumed = accessTokenService.consumeToken(tokenString)
        assertThat(consumed).isTrue()

        // Second use: same token should be invalid
        val queueFromToken2 = accessTokenService.validateToken(tokenString)
        assertThat(queueFromToken2).isNull()

        // Verify the token is deactivated in the database
        val deactivatedToken = accessTokenRepository.findByToken(tokenString)
        assertThat(deactivatedToken).isNotNull
        assertThat(deactivatedToken!!.isActive).isFalse()
        assertThat(deactivatedToken.useCount).isEqualTo(1)
    }

    @Test
    fun oneTimeToken_newTokenGeneratedAfterUse() {
        // Given: A queue with ONE_TIME access token mode
        val ownerId = "owner-onetime-2"
        val queue = Queue.create("One-Time Queue 2", ownerId)
        queue.open = true
        queue.accessTokenMode = AccessTokenMode.ONE_TIME
        queue.tokenExpiryMinutes = 60
        queueRepository.save(queue)

        // Generate an initial token
        val initialToken = accessTokenService.generateToken(queue)
        val originalTokenString = initialToken.token

        // Verify only one active token exists initially
        val activeTokensBefore = accessTokenRepository.findActiveByQueueId(queue.id)
        assertThat(activeTokensBefore).hasSize(1)
        assertThat(activeTokensBefore[0].token).isEqualTo(originalTokenString)

        // Validate and use the token
        val queueFromToken = accessTokenService.validateToken(originalTokenString)
        assertThat(queueFromToken).isNotNull

        // Issue ticket and consume token
        val ticket = issueTicketUseCase.executeWithToken(queueFromToken!!, "Customer", null)
        assertThat(ticket).isNotNull
        
        val consumed = accessTokenService.consumeToken(originalTokenString)
        assertThat(consumed).isTrue()

        // Verify a new token was generated
        val activeTokensAfter = accessTokenRepository.findActiveByQueueId(queue.id)
        assertThat(activeTokensAfter).hasSize(1)
        
        val newToken = activeTokensAfter[0]
        assertThat(newToken.token).isNotEqualTo(originalTokenString)
        assertThat(newToken.isActive).isTrue()
        assertThat(newToken.useCount).isEqualTo(0)

        // Verify the new token is valid
        val queueFromNewToken = accessTokenService.validateToken(newToken.token)
        assertThat(queueFromNewToken).isNotNull
        assertThat(queueFromNewToken!!.id).isEqualTo(queue.id)
    }

    @Test
    fun oneTimeToken_validateDoesNotConsumeToken() {
        // Given: A queue with ONE_TIME access token mode
        val ownerId = "owner-onetime-3"
        val queue = Queue.create("One-Time Queue 3", ownerId)
        queue.open = true
        queue.accessTokenMode = AccessTokenMode.ONE_TIME
        queue.tokenExpiryMinutes = 60
        queueRepository.save(queue)

        // Generate an initial token
        val initialToken = accessTokenService.generateToken(queue)
        val tokenString = initialToken.token

        // Validate the token multiple times (simulating page refresh on join page)
        repeat(5) {
            val queueFromToken = accessTokenService.validateToken(tokenString)
            assertThat(queueFromToken).isNotNull
            assertThat(queueFromToken!!.id).isEqualTo(queue.id)
        }

        // Token should still be valid and use_count should still be 0
        val token = accessTokenRepository.findByToken(tokenString)
        assertThat(token).isNotNull
        assertThat(token!!.isActive).isTrue()
        assertThat(token.useCount).isEqualTo(0)

        // Only after consuming should it be invalidated
        val consumed = accessTokenService.consumeToken(tokenString)
        assertThat(consumed).isTrue()

        val consumedToken = accessTokenRepository.findByToken(tokenString)
        assertThat(consumedToken).isNotNull
        assertThat(consumedToken!!.isActive).isFalse()
        assertThat(consumedToken.useCount).isEqualTo(1)
    }

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
