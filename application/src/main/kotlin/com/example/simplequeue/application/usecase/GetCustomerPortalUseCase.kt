package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.TicketRepository
import java.time.Instant
import java.util.UUID

/**
 * DTO for active ticket view in Customer Portal.
 */
data class ActiveTicketView(
    val ticketId: UUID,
    val queueId: UUID,
    val queueName: String,
    val ticketCode: String,
    val status: Ticket.TicketStatus,
    val positionInQueue: Int?,
    val estimatedWaitMinutes: Int?,
    val issuedAt: Instant,
    val calledAt: Instant?,
)

/**
 * DTO for ticket history item in Customer Portal.
 */
data class TicketHistoryItem(
    val ticketId: UUID,
    val queueName: String,
    val ticketCode: String,
    val status: Ticket.TicketStatus,
    val issuedAt: Instant,
    val completedAt: Instant?,
    val waitTimeMinutes: Int?,
)

/**
 * DTO for the full Customer Portal view.
 */
data class CustomerPortalView(
    val activeTicket: ActiveTicketView?,
    val history: List<TicketHistoryItem>,
    val hasMoreHistory: Boolean,
    val totalHistoryCount: Int,
)

/**
 * Use case for getting the Customer Portal view for a logged-in user.
 * This includes their active ticket (if any) and recent ticket history.
 */
class GetCustomerPortalUseCase(
    private val ticketRepository: TicketRepository,
    private val queueRepository: QueueRepository,
) {
    companion object {
        const val DEFAULT_HISTORY_SIZE = 5
    }

    /**
     * Get the Customer Portal view for a user.
     *
     * @param userId The Keycloak subject ID
     * @param historySize Number of history items to include (default 5)
     * @return CustomerPortalView with active ticket and history
     */
    fun execute(userId: String, historySize: Int = DEFAULT_HISTORY_SIZE): CustomerPortalView {
        // Get active ticket
        val activeTicket = ticketRepository.findActiveByUserId(userId)
        val activeTicketView = activeTicket?.let { ticket ->
            val queue = queueRepository.findById(ticket.queueId)
            val queueName = queue?.name ?: "Unknown Queue"

            val positionInQueue = if (ticket.status == Ticket.TicketStatus.WAITING) {
                ticketRepository.countPositionInQueue(ticket.queueId, ticket.number)
            } else {
                null
            }

            val estimatedWaitMinutes = if (positionInQueue != null && positionInQueue > 0) {
                val avgSeconds = ticketRepository.getAverageProcessingTimeSeconds(ticket.queueId)
                ((positionInQueue * avgSeconds) / 60).toInt()
            } else {
                null
            }

            ActiveTicketView(
                ticketId = ticket.id,
                queueId = ticket.queueId,
                queueName = queueName,
                ticketCode = ticket.code,
                status = ticket.status,
                positionInQueue = positionInQueue,
                estimatedWaitMinutes = estimatedWaitMinutes,
                issuedAt = ticket.createdAt,
                calledAt = ticket.calledAt,
            )
        }

        // Get history
        val historyTickets = ticketRepository.findHistoryByUserId(userId, historySize, 0)
        val totalHistoryCount = ticketRepository.countHistoryByUserId(userId)

        val history = historyTickets.map { ticket ->
            val queue = queueRepository.findById(ticket.queueId)
            val queueName = queue?.name ?: "Unknown Queue"

            val waitTimeMinutes = if (ticket.calledAt != null && ticket.completedAt != null) {
                ((ticket.completedAt!!.epochSecond - ticket.createdAt.epochSecond) / 60).toInt()
            } else if (ticket.completedAt != null) {
                ((ticket.completedAt!!.epochSecond - ticket.createdAt.epochSecond) / 60).toInt()
            } else {
                null
            }

            TicketHistoryItem(
                ticketId = ticket.id,
                queueName = queueName,
                ticketCode = ticket.code,
                status = ticket.status,
                issuedAt = ticket.createdAt,
                completedAt = ticket.completedAt,
                waitTimeMinutes = waitTimeMinutes,
            )
        }

        return CustomerPortalView(
            activeTicket = activeTicketView,
            history = history,
            hasMoreHistory = totalHistoryCount > historySize,
            totalHistoryCount = totalHistoryCount,
        )
    }
}
