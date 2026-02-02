package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.Counter
import com.example.simplequeue.domain.model.CounterSession
import com.example.simplequeue.domain.port.CounterRepository
import com.example.simplequeue.domain.port.CounterSessionRepository
import java.util.UUID

class GetOperatorSessionUseCase(
    private val counterRepository: CounterRepository,
    private val counterSessionRepository: CounterSessionRepository,
) {
    data class OperatorSessionInfo(
        val session: CounterSession?,
        val counter: Counter?,
        val hasActiveSession: Boolean,
    )

    /**
     * Get the current session info for an operator.
     */
    fun execute(operatorId: String): OperatorSessionInfo {
        val session = counterSessionRepository.findActiveByOperatorId(operatorId)
        
        if (session == null) {
            return OperatorSessionInfo(
                session = null,
                counter = null,
                hasActiveSession = false,
            )
        }

        val counter = counterRepository.findById(session.counterId)

        return OperatorSessionInfo(
            session = session,
            counter = counter,
            hasActiveSession = true,
        )
    }

    /**
     * Get the current session info for a specific queue and operator.
     * Returns null if operator has no active session for this queue.
     */
    fun executeForQueue(queueId: UUID, operatorId: String): OperatorSessionInfo? {
        val session = counterSessionRepository.findActiveByOperatorId(operatorId)
            ?: return OperatorSessionInfo(
                session = null,
                counter = null,
                hasActiveSession = false,
            )

        val counter = counterRepository.findById(session.counterId)
            ?: return OperatorSessionInfo(
                session = null,
                counter = null,
                hasActiveSession = false,
            )

        // Check if counter belongs to the requested queue
        if (counter.queueId != queueId) {
            return OperatorSessionInfo(
                session = null,
                counter = null,
                hasActiveSession = false,
            )
        }

        return OperatorSessionInfo(
            session = session,
            counter = counter,
            hasActiveSession = true,
        )
    }
}
