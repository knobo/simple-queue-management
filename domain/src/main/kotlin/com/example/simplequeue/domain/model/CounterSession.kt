package com.example.simplequeue.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Represents a session where an operator works at a specific counter.
 * 
 * An operator can only have one active session at a time.
 * When they switch counters or log off, the session is ended.
 */
data class CounterSession(
    val id: UUID,
    val counterId: UUID,
    val operatorId: String,
    val startedAt: Instant,
    var endedAt: Instant?,
) {
    /**
     * Check if this session is currently active.
     */
    val isActive: Boolean
        get() = endedAt == null

    /**
     * End this session.
     */
    fun end() {
        if (endedAt == null) {
            endedAt = Instant.now()
        }
    }

    companion object {
        fun start(
            counterId: UUID,
            operatorId: String,
        ): CounterSession = CounterSession(
            id = UUID.randomUUID(),
            counterId = counterId,
            operatorId = operatorId,
            startedAt = Instant.now(),
            endedAt = null,
        )
    }
}
