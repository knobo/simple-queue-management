package com.example.simplequeue.application.usecase

import com.example.simplequeue.application.service.QueueNotificationService
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.CounterRepository
import com.example.simplequeue.domain.port.CounterSessionRepository
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.QueueStateRepository
import com.example.simplequeue.domain.port.TicketRepository
import java.time.Instant
import java.util.UUID

class ServeTicketUseCase(
    private val ticketRepository: TicketRepository,
    private val queueStateRepository: QueueStateRepository,
    private val queueRepository: QueueRepository,
    private val queueNotificationService: QueueNotificationService,
    private val counterRepository: CounterRepository? = null,
    private val counterSessionRepository: CounterSessionRepository? = null,
) {
    /**
     * Serve a ticket, optionally assigning it to a specific operator's counter.
     * 
     * @param queueId The queue ID
     * @param ticketId Optional specific ticket ID to serve (otherwise serves next waiting)
     * @param operatorId Optional operator ID - if provided, ticket will be assigned to their counter
     */
    fun execute(
        queueId: UUID,
        ticketId: UUID? = null,
        operatorId: String? = null,
    ) {
        val queue = queueRepository.findById(queueId)
            ?: throw IllegalArgumentException("Queue not found")

        val ticketToServe =
            if (ticketId != null) {
                ticketRepository.findById(ticketId)
                    ?: throw IllegalArgumentException("Ticket not found")
            } else {
                val waiting = ticketRepository.findByQueueIdAndStatus(queueId, Ticket.TicketStatus.WAITING)
                if (waiting.isEmpty()) return
                waiting[0]
            }

        if (ticketToServe.queueId != queueId) {
            throw IllegalArgumentException("Ticket does not belong to queue")
        }

        // Find "CALLED" state
        val calledStates = queueStateRepository.findByQueueIdAndStatus(queueId, Ticket.TicketStatus.CALLED)
        val calledState =
            calledStates.firstOrNull()
                ?: throw IllegalStateException("No serving state defined for queue")

        ticketToServe.status = Ticket.TicketStatus.CALLED
        ticketToServe.stateId = calledState.id
        ticketToServe.calledAt = Instant.now()

        // Assign counter and operator if available
        if (operatorId != null && counterSessionRepository != null && counterRepository != null) {
            val session = counterSessionRepository.findActiveByOperatorId(operatorId)
            if (session != null) {
                val counter = counterRepository.findById(session.counterId)
                if (counter != null && counter.queueId == queueId) {
                    ticketToServe.counterId = counter.id
                    ticketToServe.servedBy = operatorId
                    
                    // Update counter with current ticket
                    counter.startServing(ticketToServe.id)
                    counterRepository.save(counter)
                }
            }
        }

        ticketRepository.save(ticketToServe)

        // Send rich "It's your turn!" notification
        queueNotificationService.notifyTicketCalled(ticketToServe, queue)

        // Notify all remaining waiting tickets of their new positions
        queueNotificationService.notifyAllWaitingTickets(queueId, queue)

        // Notify queue topic for dashboard/display
        val counterName = ticketToServe.counterId?.let { cid ->
            counterRepository?.findById(cid)?.displayName
        }
        val statusMessage = if (counterName != null) {
            """{"status":"serving", "number":${ticketToServe.number}, "counter":"$counterName"}"""
        } else {
            """{"status":"serving", "number":${ticketToServe.number}}"""
        }
        queueNotificationService.notifyQueueTopic(queueId, statusMessage)
    }
}
