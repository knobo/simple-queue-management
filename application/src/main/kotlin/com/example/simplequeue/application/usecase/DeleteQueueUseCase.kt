package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.TicketRepository
import java.util.UUID

class DeleteQueueUseCase(
    private val queueRepository: QueueRepository,
    private val ticketRepository: TicketRepository,
) {
    fun execute(queueId: UUID) {
        val queue =
            queueRepository.findById(queueId)
                ?: throw IllegalArgumentException("Queue not found")

        val ticketCount = ticketRepository.countByQueueId(queueId)
        if (ticketCount > 0) {
            throw IllegalStateException("Cannot delete queue with existing tickets. Queue must be empty.")
        }

        queueRepository.delete(queueId)
    }
}
