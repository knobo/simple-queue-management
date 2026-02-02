package com.example.simplequeue.application.usecase

import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.domain.model.Counter
import com.example.simplequeue.domain.port.CounterRepository
import com.example.simplequeue.domain.port.QueueRepository
import java.util.UUID

/**
 * Use case for creating a new counter (service desk) in a queue.
 */
class CreateCounterUseCase(
    private val counterRepository: CounterRepository,
    private val queueRepository: QueueRepository,
    private val subscriptionService: SubscriptionService,
) {
    /**
     * Create a new counter in the specified queue.
     *
     * @param queueId The ID of the queue to add the counter to
     * @param name Optional custom name for the counter
     * @param userId The user creating the counter (for authorization)
     * @return The newly created counter
     * @throws IllegalArgumentException if the queue doesn't exist
     * @throws FeatureNotAllowedException if the user has reached their counter limit
     */
    fun execute(queueId: UUID, name: String?, userId: String): Counter {
        // Verify queue exists
        val queue = queueRepository.findById(queueId)
            ?: throw IllegalArgumentException("Queue not found: $queueId")

        // Check tier limit
        val currentCounterCount = counterRepository.countByQueueId(queueId)
        if (!subscriptionService.canAddCounter(queue.ownerId, currentCounterCount)) {
            val limits = subscriptionService.getTierLimitForUser(queue.ownerId)
            throw FeatureNotAllowedException(
                "You have reached the maximum number of counters (${limits.maxCountersPerQueue}) " +
                "for your ${limits.tier} plan. Upgrade your subscription to add more counters."
            )
        }

        // Get next counter number
        val nextNumber = counterRepository.getNextNumber(queueId)

        // Create and save counter
        val counter = Counter.create(
            queueId = queueId,
            number = nextNumber,
            name = name,
        )
        counterRepository.save(counter)

        return counter
    }
}
