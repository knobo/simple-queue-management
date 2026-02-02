package com.example.simplequeue.application.usecase

import com.example.simplequeue.application.service.QueueNotificationService
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.TicketRepository
import java.util.UUID

class RevokeTicketUseCase(
    private val ticketRepository: TicketRepository,
    private val queueRepository: QueueRepository,
    private val queueNotificationService: QueueNotificationService,
) {
    fun execute(
        queueId: UUID,
        ticketId: UUID,
    ) {
        val queue = queueRepository.findById(queueId)
            ?: throw IllegalArgumentException("Queue not found")

        val ticket =
            ticketRepository.findById(ticketId)
                ?: throw IllegalArgumentException("Ticket not found")

        if (ticket.queueId != queueId) {
            throw IllegalArgumentException("Ticket does not belong to this queue")
        }

        ticket.status = Ticket.TicketStatus.CANCELLED
        ticketRepository.save(ticket)

        // Notify the ticket holder their ticket was cancelled
        queueNotificationService.notifyTicketCancelled(ticket, queue)

        // Notify remaining waiting tickets of updated positions
        queueNotificationService.notifyAllWaitingTickets(queueId, queue)
    }
}
