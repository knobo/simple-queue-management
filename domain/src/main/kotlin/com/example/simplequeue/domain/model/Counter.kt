package com.example.simplequeue.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Represents a service counter/station where customers are served.
 * 
 * Each queue can have multiple counters, and each counter can be
 * operated by one operator at a time. The counter tracks which
 * ticket is currently being served.
 */
data class Counter(
    val id: UUID,
    val queueId: UUID,
    val number: Int,                    // Counter 1, 2, 3...
    val name: String?,                  // Optional custom name (e.g., "Skranke A", "Window 1")
    var currentOperatorId: String?,     // User ID of operator currently working here
    var currentTicketId: UUID?,         // Ticket currently being served
    val createdAt: Instant,
    var updatedAt: Instant,
) {
    /**
     * Display name for the counter.
     * Uses custom name if set, otherwise "Counter {number}" or "Skranke {number}".
     */
    val displayName: String
        get() = name ?: "Skranke $number"

    /**
     * Check if counter is currently occupied by an operator.
     */
    val isOccupied: Boolean
        get() = currentOperatorId != null

    /**
     * Check if counter is currently serving a ticket.
     */
    val isServing: Boolean
        get() = currentTicketId != null

    /**
     * Assign an operator to this counter.
     */
    fun assignOperator(operatorId: String) {
        currentOperatorId = operatorId
        updatedAt = Instant.now()
    }

    /**
     * Remove operator from this counter.
     */
    fun releaseOperator() {
        currentOperatorId = null
        currentTicketId = null
        updatedAt = Instant.now()
    }

    /**
     * Start serving a ticket at this counter.
     */
    fun startServing(ticketId: UUID) {
        currentTicketId = ticketId
        updatedAt = Instant.now()
    }

    /**
     * Finish serving the current ticket.
     */
    fun finishServing() {
        currentTicketId = null
        updatedAt = Instant.now()
    }

    companion object {
        fun create(
            queueId: UUID,
            number: Int,
            name: String? = null,
        ): Counter = Counter(
            id = UUID.randomUUID(),
            queueId = queueId,
            number = number,
            name = name,
            currentOperatorId = null,
            currentTicketId = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
    }
}
