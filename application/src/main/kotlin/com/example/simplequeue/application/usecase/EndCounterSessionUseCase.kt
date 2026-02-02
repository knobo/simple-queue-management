package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.port.CounterRepository
import com.example.simplequeue.domain.port.CounterSessionRepository
import java.util.UUID

class EndCounterSessionUseCase(
    private val counterRepository: CounterRepository,
    private val counterSessionRepository: CounterSessionRepository,
) {
    /**
     * End the current session for an operator.
     * 
     * This will:
     * 1. Find and end the active session
     * 2. Release the operator from the counter
     */
    fun execute(operatorId: String) {
        // Find the active session
        val session = counterSessionRepository.findActiveByOperatorId(operatorId)
            ?: return // No active session, nothing to do

        // End the session
        counterSessionRepository.endSession(session.id)

        // Release the counter
        val counter = counterRepository.findById(session.counterId)
        if (counter != null && counter.currentOperatorId == operatorId) {
            counter.releaseOperator()
            counterRepository.save(counter)
        }
    }

    /**
     * End a specific session by ID (for admin use).
     */
    fun executeById(sessionId: UUID) {
        val session = counterSessionRepository.findById(sessionId)
            ?: throw IllegalArgumentException("Session not found")

        if (session.isActive) {
            counterSessionRepository.endSession(sessionId)

            // Release the counter
            val counter = counterRepository.findById(session.counterId)
            if (counter != null && counter.currentOperatorId == session.operatorId) {
                counter.releaseOperator()
                counterRepository.save(counter)
            }
        }
    }
}
