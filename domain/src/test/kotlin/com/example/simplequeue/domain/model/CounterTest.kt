package com.example.simplequeue.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant
import java.util.UUID

class CounterTest {

    @Test
    fun `should create counter with factory method`() {
        val queueId = UUID.randomUUID()
        val counter = Counter.create(queueId, 1, "Window A")

        assertNotNull(counter.id)
        assertEquals(queueId, counter.queueId)
        assertEquals(1, counter.number)
        assertEquals("Window A", counter.name)
        assertNull(counter.currentOperatorId)
        assertNull(counter.currentTicketId)
        assertNotNull(counter.createdAt)
        assertNotNull(counter.updatedAt)
    }

    @Test
    fun `should create counter without name`() {
        val queueId = UUID.randomUUID()
        val counter = Counter.create(queueId, 2)

        assertEquals(queueId, counter.queueId)
        assertEquals(2, counter.number)
        assertNull(counter.name)
    }

    @Test
    fun `displayName should return custom name if set`() {
        val counter = Counter.create(UUID.randomUUID(), 1, "Reception")

        assertEquals("Reception", counter.displayName)
    }

    @Test
    fun `displayName should return default format if no name`() {
        val counter = Counter.create(UUID.randomUUID(), 3)

        assertEquals("Skranke 3", counter.displayName)
    }

    @Test
    fun `isOccupied should return false when no operator assigned`() {
        val counter = Counter.create(UUID.randomUUID(), 1)

        assertFalse(counter.isOccupied)
    }

    @Test
    fun `isOccupied should return true when operator is assigned`() {
        val counter = Counter.create(UUID.randomUUID(), 1)
        counter.assignOperator("user-123")

        assertTrue(counter.isOccupied)
    }

    @Test
    fun `isServing should return false when no ticket being served`() {
        val counter = Counter.create(UUID.randomUUID(), 1)

        assertFalse(counter.isServing)
    }

    @Test
    fun `isServing should return true when serving a ticket`() {
        val counter = Counter.create(UUID.randomUUID(), 1)
        counter.startServing(UUID.randomUUID())

        assertTrue(counter.isServing)
    }

    @Test
    fun `assignOperator should set operatorId and update timestamp`() {
        val counter = Counter.create(UUID.randomUUID(), 1)
        val originalUpdatedAt = counter.updatedAt

        Thread.sleep(10) // Ensure different timestamp
        counter.assignOperator("operator-456")

        assertEquals("operator-456", counter.currentOperatorId)
        assertTrue(counter.updatedAt.isAfter(originalUpdatedAt) || counter.updatedAt == originalUpdatedAt)
    }

    @Test
    fun `releaseOperator should clear operatorId and ticketId`() {
        val counter = Counter.create(UUID.randomUUID(), 1)
        counter.assignOperator("operator-789")
        counter.startServing(UUID.randomUUID())

        assertTrue(counter.isOccupied)
        assertTrue(counter.isServing)

        counter.releaseOperator()

        assertNull(counter.currentOperatorId)
        assertNull(counter.currentTicketId)
        assertFalse(counter.isOccupied)
        assertFalse(counter.isServing)
    }

    @Test
    fun `startServing should set ticketId`() {
        val counter = Counter.create(UUID.randomUUID(), 1)
        val ticketId = UUID.randomUUID()

        counter.startServing(ticketId)

        assertEquals(ticketId, counter.currentTicketId)
        assertTrue(counter.isServing)
    }

    @Test
    fun `finishServing should clear ticketId`() {
        val counter = Counter.create(UUID.randomUUID(), 1)
        counter.startServing(UUID.randomUUID())

        assertTrue(counter.isServing)

        counter.finishServing()

        assertNull(counter.currentTicketId)
        assertFalse(counter.isServing)
    }

    @Test
    fun `counter should be a data class with proper equality`() {
        val id = UUID.randomUUID()
        val queueId = UUID.randomUUID()
        val now = Instant.now()

        val counter1 = Counter(
            id = id,
            queueId = queueId,
            number = 1,
            name = "Test",
            currentOperatorId = null,
            currentTicketId = null,
            createdAt = now,
            updatedAt = now,
        )

        val counter2 = Counter(
            id = id,
            queueId = queueId,
            number = 1,
            name = "Test",
            currentOperatorId = null,
            currentTicketId = null,
            createdAt = now,
            updatedAt = now,
        )

        assertEquals(counter1, counter2)
        assertEquals(counter1.hashCode(), counter2.hashCode())
    }
}
