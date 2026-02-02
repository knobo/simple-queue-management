package com.example.simplequeue.domain.model

import java.time.Instant
import java.util.UUID

data class QueueMember(
    val id: UUID,
    val queueId: UUID,
    val userId: String,
    val role: MemberRole,
    val joinedAt: Instant,
    val invitedBy: String?,
) {
    companion object {
        fun create(
            queueId: UUID,
            userId: String,
            role: MemberRole,
            invitedBy: String? = null,
        ): QueueMember =
            QueueMember(
                id = UUID.randomUUID(),
                queueId = queueId,
                userId = userId,
                role = role,
                joinedAt = Instant.now(),
                invitedBy = invitedBy,
            )
    }
}
