package com.example.simplequeue.domain.model

import java.time.Instant
import java.util.UUID

data class Ticket(
    val id: UUID,
    val queueId: UUID,
    val number: Int,
    val name: String? = null,
    var status: TicketStatus,
    var stateId: UUID? = null, // Temporary nullable for migration compatibility
    val ntfyTopic: String,
    val createdAt: Instant,
    var calledAt: Instant? = null,
    var completedAt: Instant? = null,
    val userId: String? = null,      // Keycloak subject ID (nullable)
    var guestEmail: String? = null,  // For anonymous users
    var counterId: UUID? = null,     // Counter where ticket is/was served
    var servedBy: String? = null,    // Operator who served/is serving the ticket
) {
    /**
     * Display-friendly ticket code.
     * Differences from [id] and [number]:
     * - [id]: Internal unique UUID (e.g. "ec70b7aa-..."). Used for API lookups and DB keys.
     * - [number]: Sequential integer (e.g. 105). Used for ordering in the queue.
     * - [code]: Formatted string (e.g. "A-105"). Used for display on screens and tickets.
     */
    val code: String
        get() = String.format("A-%03d", number)

    enum class TicketStatus {
        WAITING,
        CALLED,
        COMPLETED,
        CANCELLED,
    }

    companion object {
        fun issue(
            queueId: UUID,
            number: Int,
            name: String? = null,
            email: String? = null,
        ): Ticket =
            Ticket(
                id = UUID.randomUUID(),
                queueId = queueId,
                number = number,
                name = name,
                status = TicketStatus.WAITING,
                // stateId should be set by the caller (IssueTicketUseCase) looking up the default state
                stateId = null,
                ntfyTopic = UUID.randomUUID().toString(),
                createdAt = Instant.now(),
                guestEmail = email,
            )
    }
}
