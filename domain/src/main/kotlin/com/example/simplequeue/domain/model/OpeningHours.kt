package com.example.simplequeue.domain.model

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

data class OpeningHours(
    val id: UUID,
    val organizationId: UUID,
    val dayOfWeek: DayOfWeek,
    val opensAt: LocalTime,
    val closesAt: LocalTime,
    val isClosed: Boolean = false,
) {
    init {
        require(opensAt < closesAt || isClosed) {
            "Opens at must be before closes at (unless marked as closed)"
        }
    }

    fun isOpenAt(time: LocalTime): Boolean {
        if (isClosed) return false
        return time >= opensAt && time < closesAt
    }

    fun formatHours(): String {
        return if (isClosed) {
            "Stengt"
        } else {
            "${opensAt.format(TIME_FORMAT)} - ${closesAt.format(TIME_FORMAT)}"
        }
    }

    companion object {
        private val TIME_FORMAT = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

        fun create(
            organizationId: UUID,
            dayOfWeek: DayOfWeek,
            opensAt: LocalTime,
            closesAt: LocalTime,
            isClosed: Boolean = false,
        ): OpeningHours {
            return OpeningHours(
                id = UUID.randomUUID(),
                organizationId = organizationId,
                dayOfWeek = dayOfWeek,
                opensAt = opensAt,
                closesAt = closesAt,
                isClosed = isClosed,
            )
        }

        fun closed(organizationId: UUID, dayOfWeek: DayOfWeek): OpeningHours {
            return OpeningHours(
                id = UUID.randomUUID(),
                organizationId = organizationId,
                dayOfWeek = dayOfWeek,
                opensAt = LocalTime.of(0, 0),
                closesAt = LocalTime.of(23, 59),
                isClosed = true,
            )
        }

        /**
         * Create standard business hours (08:00-16:00) for all weekdays.
         */
        fun standardWeekdays(organizationId: UUID): List<OpeningHours> {
            return listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ).map { day ->
                create(
                    organizationId = organizationId,
                    dayOfWeek = day,
                    opensAt = LocalTime.of(8, 0),
                    closesAt = LocalTime.of(16, 0),
                )
            } + listOf(
                closed(organizationId, DayOfWeek.SATURDAY),
                closed(organizationId, DayOfWeek.SUNDAY),
            )
        }
    }
}
