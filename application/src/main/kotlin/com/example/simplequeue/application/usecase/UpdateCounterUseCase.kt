package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.Counter
import com.example.simplequeue.domain.port.CounterRepository
import com.example.simplequeue.domain.port.QueueRepository
import java.util.UUID

/**
 * Use case for updating a counter's details.
 */
class UpdateCounterUseCase(
    private val counterRepository: CounterRepository,
    private val queueRepository: QueueRepository,
) {
    /**
     * Update a counter's name.
     *
     * @param queueId The queue ID
     * @param counterId The counter ID to update
     * @param name The new name (null to clear custom name)
     * @param requesterId The ID of the user requesting the update (must be queue owner)
     * @return The updated counter
     * @throws IllegalArgumentException if counter not found
     * @throws IllegalStateException if user is not the queue owner
     */
    fun execute(queueId: UUID, counterId: UUID, name: String?, requesterId: String): Counter {
        val queue = queueRepository.findById(queueId)
            ?: throw IllegalArgumentException("Queue not found")

        if (queue.ownerId != requesterId) {
            throw IllegalStateException("Only the queue owner can update counters")
        }

        val counter = counterRepository.findById(counterId)
            ?: throw IllegalArgumentException("Counter not found")

        // Verify the counter belongs to the specified queue
        if (counter.queueId != queueId) {
            throw IllegalArgumentException("Counter does not belong to this queue")
        }

        // Create new counter with updated name
        val updatedCounter = Counter(
            id = counter.id,
            queueId = counter.queueId,
            number = counter.number,
            name = name?.takeIf { it.isNotBlank() },
            currentOperatorId = counter.currentOperatorId,
            currentTicketId = counter.currentTicketId,
            createdAt = counter.createdAt,
            updatedAt = java.time.Instant.now(),
        )

        counterRepository.save(updatedCounter)
        return updatedCounter
    }
}
