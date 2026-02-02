package com.example.simplequeue.application.usecase

import com.example.simplequeue.application.service.QueueNotificationService
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.CounterRepository
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.QueueStateRepository
import com.example.simplequeue.domain.port.TicketRepository
import java.time.Instant
import java.util.UUID

class CompleteTicketUseCase(
    private val ticketRepository: TicketRepository,
    private val queueStateRepository: QueueStateRepository,
    private val queueRepository: QueueRepository,
    private val queueNotificationService: QueueNotificationService,
    private val counterRepository: CounterRepository? = null,
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
            throw IllegalArgumentException("Ticket does not belong to queue")
        }

        // Find "COMPLETED" state
        val completedStates = queueStateRepository.findByQueueIdAndStatus(queueId, Ticket.TicketStatus.COMPLETED)
        val completedState =
            completedStates.firstOrNull()
                ?: throw IllegalStateException("No completed state defined for queue")

        // Clear the counter's current ticket if this ticket was being served there
        if (ticket.counterId != null && counterRepository != null) {
            val counter = counterRepository.findById(ticket.counterId!!)
            if (counter != null && counter.currentTicketId == ticketId) {
                counter.finishServing()
                counterRepository.save(counter)
            }
        }

        ticket.status = Ticket.TicketStatus.COMPLETED
        ticket.stateId = completedState.id
        ticket.completedAt = Instant.now()
        ticketRepository.save(ticket)

        // Send thank-you notification
        queueNotificationService.notifyTicketCompleted(ticket, queue)
    }
}
