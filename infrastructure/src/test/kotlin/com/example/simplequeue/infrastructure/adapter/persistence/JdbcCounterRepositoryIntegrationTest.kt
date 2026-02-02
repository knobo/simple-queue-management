package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.Counter
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.CounterRepository
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.TicketRepository
import com.example.simplequeue.infrastructure.TestEmailConfig
import com.example.simplequeue.infrastructure.TestJacksonConfig
import com.example.simplequeue.infrastructure.TestSecurityConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
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
import java.util.UUID

/**
 * Integration tests for JdbcCounterRepository.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestSecurityConfig::class, TestEmailConfig::class, TestJacksonConfig::class)
class JdbcCounterRepositoryIntegrationTest {

    @Autowired
    private lateinit var counterRepository: CounterRepository

    @Autowired
    private lateinit var queueRepository: QueueRepository

    @Autowired
    private lateinit var ticketRepository: TicketRepository

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private lateinit var testQueue: Queue

    @BeforeEach
    fun setUp() {
        // Clean up test data
        jdbcClient.sql("DELETE FROM counters WHERE queue_id IN (SELECT id FROM queues WHERE owner_id LIKE 'test-counter-%')").update()
        jdbcClient.sql("DELETE FROM tickets WHERE queue_id IN (SELECT id FROM queues WHERE owner_id LIKE 'test-counter-%')").update()
        jdbcClient.sql("DELETE FROM queue_states WHERE queue_id IN (SELECT id FROM queues WHERE owner_id LIKE 'test-counter-%')").update()
        jdbcClient.sql("DELETE FROM queues WHERE owner_id LIKE 'test-counter-%'").update()

        // Create test queue
        testQueue = Queue.create("Test Queue", "test-counter-${UUID.randomUUID()}")
        queueRepository.save(testQueue)
    }

    @Nested
    inner class SaveAndFindById {

        @Test
        fun `should save and retrieve counter`() {
            val counter = Counter.create(testQueue.id, 1, "Window A")
            counterRepository.save(counter)

            val retrieved = counterRepository.findById(counter.id)

            assertThat(retrieved).isNotNull
            assertThat(retrieved!!.id).isEqualTo(counter.id)
            assertThat(retrieved.queueId).isEqualTo(testQueue.id)
            assertThat(retrieved.number).isEqualTo(1)
            assertThat(retrieved.name).isEqualTo("Window A")
            assertThat(retrieved.currentOperatorId).isNull()
            assertThat(retrieved.currentTicketId).isNull()
        }

        @Test
        fun `should update existing counter on save`() {
            val counter = Counter.create(testQueue.id, 1, "Original Name")
            counterRepository.save(counter)

            // Update the counter
            counter.assignOperator("operator-123")
            counterRepository.save(counter)

            val retrieved = counterRepository.findById(counter.id)
            assertThat(retrieved!!.currentOperatorId).isEqualTo("operator-123")
        }

        @Test
        fun `should return null for non-existent counter`() {
            val result = counterRepository.findById(UUID.randomUUID())
            assertThat(result).isNull()
        }
    }

    @Nested
    inner class FindByQueueId {

        @Test
        fun `should find all counters for a queue ordered by number`() {
            val counter3 = Counter.create(testQueue.id, 3, "Counter 3")
            val counter1 = Counter.create(testQueue.id, 1, "Counter 1")
            val counter2 = Counter.create(testQueue.id, 2, "Counter 2")

            counterRepository.save(counter3)
            counterRepository.save(counter1)
            counterRepository.save(counter2)

            val counters = counterRepository.findByQueueId(testQueue.id)

            assertThat(counters).hasSize(3)
            assertThat(counters[0].number).isEqualTo(1)
            assertThat(counters[1].number).isEqualTo(2)
            assertThat(counters[2].number).isEqualTo(3)
        }

        @Test
        fun `should return empty list for queue with no counters`() {
            val emptyQueue = Queue.create("Empty Queue", "test-counter-${UUID.randomUUID()}")
            queueRepository.save(emptyQueue)

            val counters = counterRepository.findByQueueId(emptyQueue.id)

            assertThat(counters).isEmpty()
        }
    }

    @Nested
    inner class FindByQueueIdAndNumber {

        @Test
        fun `should find counter by queue and number`() {
            val counter = Counter.create(testQueue.id, 5, "Counter 5")
            counterRepository.save(counter)

            val result = counterRepository.findByQueueIdAndNumber(testQueue.id, 5)

            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo(counter.id)
        }

        @Test
        fun `should return null for non-existent number`() {
            val counter = Counter.create(testQueue.id, 1, "Counter 1")
            counterRepository.save(counter)

            val result = counterRepository.findByQueueIdAndNumber(testQueue.id, 99)

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class FindByCurrentOperatorId {

        @Test
        fun `should find counter by operator id`() {
            val counter = Counter.create(testQueue.id, 1, "Counter 1")
            counter.assignOperator("operator-abc")
            counterRepository.save(counter)

            val result = counterRepository.findByCurrentOperatorId("operator-abc")

            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo(counter.id)
        }

        @Test
        fun `should return null when operator not assigned to any counter`() {
            val counter = Counter.create(testQueue.id, 1, "Counter 1")
            counterRepository.save(counter)

            val result = counterRepository.findByCurrentOperatorId("non-existent-operator")

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class FindByCurrentTicketId {

        @Test
        fun `should find counter by ticket id`() {
            // Create a real ticket first (FK constraint requires it to exist)
            val ticket = Ticket.issue(testQueue.id, 1, "Test Customer")
            ticketRepository.save(ticket)

            val counter = Counter.create(testQueue.id, 1, "Counter 1")
            counter.startServing(ticket.id)
            counterRepository.save(counter)

            val result = counterRepository.findByCurrentTicketId(ticket.id)

            assertThat(result).isNotNull
            assertThat(result!!.id).isEqualTo(counter.id)
        }

        @Test
        fun `should return null when ticket not being served`() {
            val counter = Counter.create(testQueue.id, 1, "Counter 1")
            counterRepository.save(counter)

            val result = counterRepository.findByCurrentTicketId(UUID.randomUUID())

            assertThat(result).isNull()
        }
    }

    @Nested
    inner class Delete {

        @Test
        fun `should delete counter`() {
            val counter = Counter.create(testQueue.id, 1, "Counter 1")
            counterRepository.save(counter)

            counterRepository.delete(counter.id)

            val result = counterRepository.findById(counter.id)
            assertThat(result).isNull()
        }

        @Test
        fun `should not throw when deleting non-existent counter`() {
            // Should not throw
            counterRepository.delete(UUID.randomUUID())
        }
    }

    @Nested
    inner class CountByQueueId {

        @Test
        fun `should count counters in queue`() {
            counterRepository.save(Counter.create(testQueue.id, 1, "Counter 1"))
            counterRepository.save(Counter.create(testQueue.id, 2, "Counter 2"))
            counterRepository.save(Counter.create(testQueue.id, 3, "Counter 3"))

            val count = counterRepository.countByQueueId(testQueue.id)

            assertThat(count).isEqualTo(3)
        }

        @Test
        fun `should return zero for empty queue`() {
            val emptyQueue = Queue.create("Empty Queue", "test-counter-${UUID.randomUUID()}")
            queueRepository.save(emptyQueue)

            val count = counterRepository.countByQueueId(emptyQueue.id)

            assertThat(count).isEqualTo(0)
        }
    }

    @Nested
    inner class GetNextNumber {

        @Test
        fun `should return 1 for queue with no counters`() {
            val emptyQueue = Queue.create("Empty Queue", "test-counter-${UUID.randomUUID()}")
            queueRepository.save(emptyQueue)

            val nextNumber = counterRepository.getNextNumber(emptyQueue.id)

            assertThat(nextNumber).isEqualTo(1)
        }

        @Test
        fun `should return max plus one`() {
            counterRepository.save(Counter.create(testQueue.id, 1, "Counter 1"))
            counterRepository.save(Counter.create(testQueue.id, 5, "Counter 5"))
            counterRepository.save(Counter.create(testQueue.id, 3, "Counter 3"))

            val nextNumber = counterRepository.getNextNumber(testQueue.id)

            assertThat(nextNumber).isEqualTo(6)
        }
    }

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
