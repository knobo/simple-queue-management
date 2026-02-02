package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.NotificationPort
import com.example.simplequeue.domain.port.TicketRepository
import java.time.Instant
import java.util.UUID

class CallNextTicketUseCase(
    private val ticketRepository: TicketRepository,
    private val notificationPort: NotificationPort
) {
    fun execute(queueId: UUID) {
        // Complete current ticket if any
        val calledTickets = ticketRepository.findByQueueIdAndStatus(queueId, Ticket.TicketStatus.CALLED)
        calledTickets.forEach { t ->
            t.status = Ticket.TicketStatus.COMPLETED
            t.completedAt = Instant.now()
            ticketRepository.save(t)
        }

        // Find next waiting ticket
        val waiting = ticketRepository.findByQueueIdAndStatus(queueId, Ticket.TicketStatus.WAITING)
        if (waiting.isNotEmpty()) {
            val next = waiting[0] // Assuming sorted by number
            next.status = Ticket.TicketStatus.CALLED
            next.calledAt = Instant.now()
            ticketRepository.save(next)

            // Notify via ntfy
            notificationPort.notify(next.ntfyTopic, "You are now being called! Please proceed to the counter.")

            // Notify next few people about their position?
            if (waiting.size > 1) {
                val second = waiting[1]
                notificationPort.notify(second.ntfyTopic, "You are now number 1 in the queue.")
            }
        }
    }
}
