package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.Counter
import com.example.simplequeue.domain.port.CounterRepository
import java.util.UUID

/**
 * Use case for listing all counters (service desks) in a queue.
 */
class ListCountersUseCase(
    private val counterRepository: CounterRepository,
) {
    /**
     * List all counters for a queue, ordered by counter number.
     *
     * @param queueId The ID of the queue
     * @return List of counters, ordered by number ascending
     */
    fun execute(queueId: UUID): List<Counter> {
        return counterRepository.findByQueueId(queueId)
    }
}
