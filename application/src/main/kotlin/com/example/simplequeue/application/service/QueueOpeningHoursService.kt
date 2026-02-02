package com.example.simplequeue.application.service

import com.example.simplequeue.domain.port.QueueClosedDateRepository
import com.example.simplequeue.domain.port.QueueOpeningHoursRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Service for checking queue opening hours and closed dates.
 */
class QueueOpeningHoursService(
    private val queueOpeningHoursRepository: QueueOpeningHoursRepository,
    private val queueClosedDateRepository: QueueClosedDateRepository,
) {
    /**
     * Check if a queue is currently open.
     * 
     * @param queueId The queue to check
     * @param now The current date/time (defaults to now in Europe/Oslo timezone)
     * @return true if the queue is open, false otherwise
     */
    fun isQueueOpenNow(
        queueId: UUID,
        now: LocalDateTime = LocalDateTime.now(ZoneId.of("Europe/Oslo"))
    ): Boolean {
        val date = now.toLocalDate()
        val time = now.toLocalTime()
        val dayOfWeek = now.dayOfWeek

        // Check if today is a closed date
        val closedDate = queueClosedDateRepository.findByQueueIdAndDate(queueId, date)
        if (closedDate != null) {
            return false
        }

        // Check opening hours for this day of week
        val openingHours = queueOpeningHoursRepository.findByQueueIdAndDay(queueId, dayOfWeek)
        
        // If no specific hours configured, assume open 24/7
        if (openingHours == null) {
            return true
        }

        // Check if the queue is marked as closed for this day
        if (openingHours.isClosed) {
            return false
        }

        // Check if current time is within opening hours
        return openingHours.isOpenAt(time)
    }

    /**
     * Check if a queue is open at a specific date and time.
     */
    fun isQueueOpenAt(
        queueId: UUID,
        dateTime: LocalDateTime
    ): Boolean {
        return isQueueOpenNow(queueId, dateTime)
    }

    /**
     * Get the next opening time for a queue.
     * Returns null if the queue has no configured hours (always open).
     */
    fun getNextOpeningTime(queueId: UUID): LocalDateTime? {
        val now = LocalDateTime.now(ZoneId.of("Europe/Oslo"))
        
        // Check up to 7 days ahead
        for (daysAhead in 0..6) {
            val checkDate = now.plusDays(daysAhead.toLong())
            val dayOfWeek = checkDate.dayOfWeek
            
            // Skip if it's a closed date
            val closedDate = queueClosedDateRepository.findByQueueIdAndDate(queueId, checkDate.toLocalDate())
            if (closedDate != null) {
                continue
            }
            
            // Get opening hours for this day
            val openingHours = queueOpeningHoursRepository.findByQueueIdAndDay(queueId, dayOfWeek)
            
            // If no hours configured, queue is always open
            if (openingHours == null) {
                return null
            }
            
            // Skip if closed this day
            if (openingHours.isClosed) {
                continue
            }
            
            // If checking today and we're before opening time
            if (daysAhead == 0 && now.toLocalTime() < openingHours.openTime) {
                return LocalDateTime.of(checkDate.toLocalDate(), openingHours.openTime)
            }
            
            // If checking a future day, return opening time
            if (daysAhead > 0) {
                return LocalDateTime.of(checkDate.toLocalDate(), openingHours.openTime)
            }
        }
        
        // No opening found in next 7 days
        return null
    }
}
