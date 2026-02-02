package com.example.simplequeue.domain.model

import java.util.UUID

data class QueueState(
    val id: UUID,
    val queueId: UUID,
    val name: String,
    val status: Ticket.TicketStatus,
    val orderIndex: Int,
)
