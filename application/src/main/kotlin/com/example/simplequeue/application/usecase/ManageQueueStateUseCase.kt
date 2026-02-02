package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.QueueState
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.QueueStateRepository
import java.util.UUID

class ManageQueueStateUseCase(
    private val queueStateRepository: QueueStateRepository,
) {
    fun addState(
        queueId: UUID,
        name: String,
        status: Ticket.TicketStatus,
    ): QueueState {
        val existingStates = queueStateRepository.findByQueueId(queueId)
        val maxOrder = existingStates.maxOfOrNull { it.orderIndex } ?: 0

        val newState =
            QueueState(
                id = UUID.randomUUID(),
                queueId = queueId,
                name = name,
                status = status,
                orderIndex = maxOrder + 1,
            )
        queueStateRepository.save(newState)
        return newState
    }

    fun removeState(
        queueId: UUID,
        stateId: UUID,
    ) {
        val states = queueStateRepository.findByQueueId(queueId)
        val stateToRemove =
            states.find { it.id == stateId }
                ?: throw IllegalArgumentException("State not found")

        // Validate minimum requirements
        val statesOfSameType = states.filter { it.status == stateToRemove.status }
        if (statesOfSameType.size <= 1) {
            throw IllegalStateException(
                "Cannot remove the last state of type ${stateToRemove.status}. Each queue must have at least one Waiting, Called, and Completed state.",
            )
        }

        // (Optional) Check if tickets are using this state.
        // For now, let's assume we can't remove if used, or we migrate tickets?
        // Simplicity: Let DB foreign key referential integrity handle it?
        // If 'tickets' table references 'queue_states' (which I added), the DB will throw constraint violation.
        // Good.

        queueStateRepository.delete(stateId)
    }
}
