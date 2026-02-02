package com.example.simplequeue.domain.model

import java.time.LocalDate
import java.util.UUID

/**
 * Represents a specific date when a queue is closed (e.g., holidays, special events).
 * This overrides regular opening hours for the specified date.
 */
data class QueueClosedDate(
    val id: UUID,
    val queueId: UUID,
    val closedDate: LocalDate,
    val reason: String?,
) {
    companion object {
        /**
         * Create a new closed date for a queue.
         */
        fun create(
            queueId: UUID,
            closedDate: LocalDate,
            reason: String? = null,
        ): QueueClosedDate {
            return QueueClosedDate(
                id = UUID.randomUUID(),
                queueId = queueId,
                closedDate = closedDate,
                reason = reason,
            )
        }

        /**
         * Create closed dates for common Norwegian holidays in a given year.
         */
        fun norwegianHolidays(queueId: UUID, year: Int): List<QueueClosedDate> {
            return listOf(
                create(queueId, LocalDate.of(year, 1, 1), "Nyttårsdag"),
                create(queueId, LocalDate.of(year, 5, 1), "1. mai"),
                create(queueId, LocalDate.of(year, 5, 17), "17. mai"),
                create(queueId, LocalDate.of(year, 12, 25), "1. juledag"),
                create(queueId, LocalDate.of(year, 12, 26), "2. juledag"),
            )
        }
    }
}
