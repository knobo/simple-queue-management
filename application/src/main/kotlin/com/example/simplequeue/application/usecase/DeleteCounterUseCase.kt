package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.port.CounterRepository
import java.util.UUID

/**
 * Use case for deleting a counter (service desk) from a queue.
 */
class DeleteCounterUseCase(
    private val counterRepository: CounterRepository,
) {
    /**
     * Delete a counter by its ID.
     *
     * @param counterId The ID of the counter to delete
     * @throws IllegalArgumentException if the counter doesn't exist
     * @throws IllegalStateException if trying to delete the last counter in a queue
     */
    fun execute(counterId: UUID) {
        val counter = counterRepository.findById(counterId)
            ?: throw IllegalArgumentException("Counter not found: $counterId")

        // Check if this is the last counter in the queue
        val counterCount = counterRepository.countByQueueId(counter.queueId)
        if (counterCount <= 1) {
            throw IllegalStateException("Cannot delete the last counter in a queue. Every queue must have at least one counter.")
        }

        counterRepository.delete(counterId)
    }
}
