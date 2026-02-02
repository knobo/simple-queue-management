package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.domain.model.AccessTokenMode
import com.example.simplequeue.domain.model.Counter
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.CounterRepository
import com.example.simplequeue.domain.port.TicketRepository
import com.example.simplequeue.infrastructure.adapter.persistence.JdbcQueueRepository
import com.example.simplequeue.infrastructure.service.AccessTokenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class DisplayControllerTest {
    private val queueRepository: JdbcQueueRepository = mock()
    private val accessTokenService: AccessTokenService = mock()
    private val ticketRepository: TicketRepository = mock()
    private val counterRepository: CounterRepository = mock()
    private val controller = DisplayController(queueRepository, accessTokenService, ticketRepository, counterRepository)

    private fun createTestQueue(
        id: UUID = UUID.randomUUID(),
        displayToken: String = "test-display-token",
        accessTokenMode: AccessTokenMode = AccessTokenMode.STATIC,
    ): Queue = Queue(
        id = id,
        name = "Test Queue",
        ownerId = "owner-1",
        open = true,
        qrCodeSecret = "test-secret",
        qrCodeType = Queue.QrCodeType.PERSISTENT,
        lastRotatedAt = Instant.now(),
        displayToken = displayToken,
        accessTokenMode = accessTokenMode,
    )

    @Nested
    inner class GetTokenStatus {
        @Test
        fun `returns 404 for unknown display token`() {
            whenever(queueRepository.findByDisplayToken("unknown")).thenReturn(null)
            
            val response = controller.getTokenStatus("unknown")
            
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `returns static token status for STATIC mode queue`() {
            val queue = createTestQueue(accessTokenMode = AccessTokenMode.STATIC)
            whenever(queueRepository.findByDisplayToken(queue.displayToken!!)).thenReturn(queue)
            
            val response = controller.getTokenStatus(queue.displayToken!!)
            
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertTrue(body.isStatic)
            assertEquals(queue.qrCodeSecret, body.token)
            assertTrue(body.joinUrl.contains(queue.qrCodeSecret))
            assertNull(body.secondsUntilExpiry)
            assertNull(body.secondsUntilRotation)
        }

        @Test
        fun `returns dynamic token status for ROTATING mode queue`() {
            val queue = createTestQueue(accessTokenMode = AccessTokenMode.ROTATING)
            val tokenInfo = AccessTokenService.TokenInfo(
                token = "rotating-token-123",
                isLegacy = false,
                mode = "rotating",
                expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES),
                secondsUntilExpiry = 300L,
                secondsUntilRotation = 120L,
            )
            
            whenever(queueRepository.findByDisplayToken(queue.displayToken!!)).thenReturn(queue)
            whenever(accessTokenService.getTokenInfo(queue.id)).thenReturn(tokenInfo)
            
            val response = controller.getTokenStatus(queue.displayToken!!)
            
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(false, body.isStatic)
            assertEquals("rotating-token-123", body.token)
            assertTrue(body.joinUrl.contains("rotating-token-123"))
            assertEquals(300L, body.secondsUntilExpiry)
            assertEquals(120L, body.secondsUntilRotation)
        }

        @Test
        fun `returns 404 when no token info available for dynamic queue`() {
            val queue = createTestQueue(accessTokenMode = AccessTokenMode.ROTATING)
            
            whenever(queueRepository.findByDisplayToken(queue.displayToken!!)).thenReturn(queue)
            whenever(accessTokenService.getTokenInfo(queue.id)).thenReturn(null)
            
            val response = controller.getTokenStatus(queue.displayToken!!)
            
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }
    }

    @Nested
    inner class GetCounterStatus {
        private fun createTestTicket(
            queueId: UUID,
            number: Int,
            status: Ticket.TicketStatus = Ticket.TicketStatus.WAITING,
        ): Ticket = Ticket(
            id = UUID.randomUUID(),
            queueId = queueId,
            number = number,
            name = null,
            status = status,
            stateId = null,
            ntfyTopic = "topic-$number",
            createdAt = Instant.now(),
        )

        private fun createTestCounter(
            queueId: UUID,
            number: Int,
            currentTicketId: UUID? = null,
            operatorId: String? = null,
        ): Counter = Counter(
            id = UUID.randomUUID(),
            queueId = queueId,
            number = number,
            name = null,
            currentOperatorId = operatorId,
            currentTicketId = currentTicketId,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )

        @Test
        fun `returns 404 for unknown display token`() {
            whenever(queueRepository.findByDisplayToken("unknown")).thenReturn(null)
            
            val response = controller.getCounterStatus("unknown", null)
            
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `returns counter status with active counters`() {
            val queueId = UUID.randomUUID()
            val queue = createTestQueue(id = queueId)
            val ticket = createTestTicket(queueId, 42, Ticket.TicketStatus.CALLED)
            val counter1 = createTestCounter(queueId, 1, ticket.id, "operator-1")
            val counter2 = createTestCounter(queueId, 2)
            val waitingTickets = listOf(
                createTestTicket(queueId, 43),
                createTestTicket(queueId, 44),
            )
            
            whenever(queueRepository.findByDisplayToken(queue.displayToken!!)).thenReturn(queue)
            whenever(counterRepository.findByQueueId(queueId)).thenReturn(listOf(counter1, counter2))
            whenever(ticketRepository.findById(ticket.id)).thenReturn(ticket)
            whenever(ticketRepository.findByQueueIdAndStatus(queueId, Ticket.TicketStatus.WAITING))
                .thenReturn(waitingTickets)
            
            val response = controller.getCounterStatus(queue.displayToken!!, null)
            
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals("Test Queue", body.queueName)
            assertTrue(body.queueOpen)
            assertEquals(2, body.counters.size)
            
            // First counter serving
            assertEquals(1, body.counters[0].counterNumber)
            assertEquals("A-042", body.counters[0].currentTicketCode)
            assertEquals("operator-1", body.counters[0].operatorId)
            
            // Second counter idle
            assertEquals(2, body.counters[1].counterNumber)
            assertNull(body.counters[1].currentTicketCode)
            
            assertEquals(listOf("A-043", "A-044"), body.nextTicketCodes)
            assertEquals(2, body.waitingCount)
        }

        @Test
        fun `filters to single counter when parameter provided`() {
            val queueId = UUID.randomUUID()
            val queue = createTestQueue(id = queueId)
            val ticket = createTestTicket(queueId, 42, Ticket.TicketStatus.CALLED)
            val counter1 = createTestCounter(queueId, 1, ticket.id)
            val counter2 = createTestCounter(queueId, 2)
            
            whenever(queueRepository.findByDisplayToken(queue.displayToken!!)).thenReturn(queue)
            whenever(counterRepository.findByQueueId(queueId)).thenReturn(listOf(counter1, counter2))
            whenever(ticketRepository.findById(ticket.id)).thenReturn(ticket)
            whenever(ticketRepository.findByQueueIdAndStatus(queueId, Ticket.TicketStatus.WAITING))
                .thenReturn(emptyList())
            
            val response = controller.getCounterStatus(queue.displayToken!!, 1)
            
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(1, body.counters.size)
            assertEquals(1, body.counters[0].counterNumber)
            assertEquals("A-042", body.counters[0].currentTicketCode)
        }

        @Test
        fun `returns empty counters when queue has no counters`() {
            val queueId = UUID.randomUUID()
            val queue = createTestQueue(id = queueId)
            
            whenever(queueRepository.findByDisplayToken(queue.displayToken!!)).thenReturn(queue)
            whenever(counterRepository.findByQueueId(queueId)).thenReturn(emptyList())
            whenever(ticketRepository.findByQueueIdAndStatus(queueId, Ticket.TicketStatus.WAITING))
                .thenReturn(emptyList())
            
            val response = controller.getCounterStatus(queue.displayToken!!, null)
            
            assertEquals(HttpStatus.OK, response.statusCode)
            val body = response.body!!
            assertEquals(0, body.counters.size)
            assertEquals(0, body.waitingCount)
        }
    }
}
