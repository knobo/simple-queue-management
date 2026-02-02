package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.TicketRepository

/**
 * Paginated result for ticket history.
 */
data class TicketHistoryPage(
    val items: List<TicketHistoryItem>,
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)

/**
 * Use case for getting paginated ticket history for a user.
 */
class GetMyTicketHistoryUseCase(
    private val ticketRepository: TicketRepository,
    private val queueRepository: QueueRepository,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }

    /**
     * Get paginated ticket history for a user.
     *
     * @param userId The Keycloak subject ID
     * @param page Page number (0-indexed)
     * @param pageSize Number of items per page
     * @return Paginated ticket history
     */
    fun execute(userId: String, page: Int = 0, pageSize: Int = DEFAULT_PAGE_SIZE): TicketHistoryPage {
        val validPage = maxOf(0, page)
        val validPageSize = minOf(maxOf(1, pageSize), 100) // Limit to 100 max

        val offset = validPage * validPageSize
        val tickets = ticketRepository.findHistoryByUserId(userId, validPageSize, offset)
        val totalItems = ticketRepository.countHistoryByUserId(userId)
        val totalPages = if (totalItems == 0) 1 else (totalItems + validPageSize - 1) / validPageSize

        val items = tickets.map { ticket ->
            val queue = queueRepository.findById(ticket.queueId)
            val queueName = queue?.name ?: "Unknown Queue"

            val waitTimeMinutes = if (ticket.completedAt != null) {
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

        return TicketHistoryPage(
            items = items,
            page = validPage,
            pageSize = validPageSize,
            totalItems = totalItems,
            totalPages = totalPages,
            hasNext = validPage < totalPages - 1,
            hasPrevious = validPage > 0,
        )
    }
}
