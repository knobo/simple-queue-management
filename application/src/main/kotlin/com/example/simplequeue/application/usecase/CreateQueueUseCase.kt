package com.example.simplequeue.application.usecase
import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.QueueState
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.QueueStateRepository
import java.util.UUID

/**
 * Exception thrown when a user tries to use a feature that is not allowed by their subscription.
 */
class FeatureNotAllowedException(message: String) : RuntimeException(message)

class CreateQueueUseCase(
    private val queueRepository: QueueRepository,
    private val queueStateRepository: QueueStateRepository,
    private val subscriptionService: SubscriptionService
) {
    fun execute(name: String, ownerId: String): Queue {
        if (!subscriptionService.canCreateQueue(ownerId)) {
            val limits = subscriptionService.getLimits(ownerId)
            throw FeatureNotAllowedException(
                "You have reached the maximum number of queues (${limits.maxQueues}) for your ${limits.tier} plan. " +
                "Upgrade your subscription to create more queues."
            )
        }
        val queue = Queue.create(name, ownerId)
        queueRepository.save(queue)
        // Create default states
        createDefaultState(queue.id, "Waiting", Ticket.TicketStatus.WAITING, 1)
        createDefaultState(queue.id, "Serving", Ticket.TicketStatus.CALLED, 2)
        createDefaultState(queue.id, "Done", Ticket.TicketStatus.COMPLETED, 3)
        createDefaultState(queue.id, "Cancelled", Ticket.TicketStatus.CANCELLED, 4)
        return queue
    }
    private fun createDefaultState(queueId: UUID, name: String, status: Ticket.TicketStatus, orderIndex: Int) {
        val state = QueueState(
            id = UUID.randomUUID(),
            queueId = queueId,
            name = name,
            status = status,
            orderIndex = orderIndex
        )
        queueStateRepository.save(state)
    }
}
