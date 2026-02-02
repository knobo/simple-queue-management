package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.port.TicketRepository
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToLong

class GetQueueStatusUseCase(
    private val ticketRepository: TicketRepository
) {
    data class QueueStatus(
        val yourNumber: Int,
        val currentNumber: Int,
        val peopleAhead: Int,
        val estimatedWaitMinutes: Long
    )

    fun execute(queueId: UUID, ticketNumber: Int): QueueStatus {
        val lastCalled = ticketRepository.getLastCalledNumber(queueId)
        val peopleAhead = max(0, ticketNumber - lastCalled - 1)
        val avgSeconds = ticketRepository.getAverageProcessingTimeSeconds(queueId)

        val waitMinutes = ((peopleAhead + 1) * avgSeconds / 60.0).roundToLong()

        return QueueStatus(ticketNumber, lastCalled, peopleAhead, waitMinutes)
    }
}
