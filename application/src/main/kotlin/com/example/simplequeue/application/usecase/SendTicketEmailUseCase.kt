package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.port.EmailPort
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.TicketRepository
import java.util.UUID

class SendTicketEmailUseCase(
    private val ticketRepository: TicketRepository,
    private val queueRepository: QueueRepository,
    private val emailPort: EmailPort,
) {
    fun execute(ticketId: UUID, email: String) {
        val ticket = ticketRepository.findById(ticketId)
            ?: throw IllegalArgumentException("Ticket not found: $ticketId")

        val queue = queueRepository.findById(ticket.queueId)
            ?: throw IllegalArgumentException("Queue not found: ${ticket.queueId}")

        // Update ticket with guest email
        ticket.guestEmail = email
        ticketRepository.save(ticket)

        // Send the email
        emailPort.sendTicketEmail(email, ticket, queue)
    }
}
