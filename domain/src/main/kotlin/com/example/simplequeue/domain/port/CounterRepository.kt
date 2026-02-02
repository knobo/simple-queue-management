package com.example.simplequeue.domain.port

import com.example.simplequeue.domain.model.Counter
import java.util.UUID

interface CounterRepository {
    fun save(counter: Counter)
    fun findById(id: UUID): Counter?
    fun findByQueueId(queueId: UUID): List<Counter>
    fun findByQueueIdAndNumber(queueId: UUID, number: Int): Counter?
    fun findByCurrentOperatorId(operatorId: String): Counter?
    fun findByCurrentTicketId(ticketId: UUID): Counter?
    fun delete(id: UUID)
    fun countByQueueId(queueId: UUID): Int
    
    /**
     * Get the next available counter number for a queue.
     */
    fun getNextNumber(queueId: UUID): Int
}
