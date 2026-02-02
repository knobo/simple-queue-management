package com.example.simplequeue.domain.model

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * Queue-specific opening hours that override organization defaults.
 * Allows individual queues to have different hours than their parent organization.
 */
data class QueueOpeningHours(
    val id: UUID,
    val queueId: UUID,
    val dayOfWeek: DayOfWeek,
    val openTime: LocalTime,
    val closeTime: LocalTime,
    val isClosed: Boolean = false,
) {
    init {
        require(openTime < closeTime || isClosed) {
            "Open time must be before close time (unless marked as closed)"
        }
    }

    /**
     * Check if the queue is open at the given time on this day.
     */
    fun isOpenAt(time: LocalTime): Boolean {
        if (isClosed) return false
        return time >= openTime && time < closeTime
    }

    /**
     * Format hours for display.
     */
    fun formatHours(): String {
        return if (isClosed) {
            "Stengt"
        } else {
            "${openTime.format(TIME_FORMAT)} - ${closeTime.format(TIME_FORMAT)}"
        }
    }

    companion object {
        private val TIME_FORMAT = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

        /**
         * Create new queue opening hours.
         */
        fun create(
            queueId: UUID,
            dayOfWeek: DayOfWeek,
            openTime: LocalTime,
            closeTime: LocalTime,
            isClosed: Boolean = false,
        ): QueueOpeningHours {
            return QueueOpeningHours(
                id = UUID.randomUUID(),
                queueId = queueId,
                dayOfWeek = dayOfWeek,
                openTime = openTime,
                closeTime = closeTime,
                isClosed = isClosed,
            )
        }

        /**
         * Create a closed day for the queue.
         */
        fun closed(queueId: UUID, dayOfWeek: DayOfWeek): QueueOpeningHours {
            return QueueOpeningHours(
                id = UUID.randomUUID(),
                queueId = queueId,
                dayOfWeek = dayOfWeek,
                openTime = LocalTime.of(0, 0),
                closeTime = LocalTime.of(23, 59),
                isClosed = true,
            )
        }

        /**
         * Create standard business hours (08:00-16:00) for all weekdays.
         */
        fun standardWeekdays(queueId: UUID): List<QueueOpeningHours> {
            return listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ).map { day ->
                create(
                    queueId = queueId,
                    dayOfWeek = day,
                    openTime = LocalTime.of(8, 0),
                    closeTime = LocalTime.of(16, 0),
                )
            } + listOf(
                closed(queueId, DayOfWeek.SATURDAY),
                closed(queueId, DayOfWeek.SUNDAY),
            )
        }
    }
}
