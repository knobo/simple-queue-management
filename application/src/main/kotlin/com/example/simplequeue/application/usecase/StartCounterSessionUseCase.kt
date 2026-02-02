package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.Counter
import com.example.simplequeue.domain.model.CounterSession
import com.example.simplequeue.domain.port.CounterRepository
import com.example.simplequeue.domain.port.CounterSessionRepository
import java.util.UUID

class StartCounterSessionUseCase(
    private val counterRepository: CounterRepository,
    private val counterSessionRepository: CounterSessionRepository,
) {
    data class Result(
        val session: CounterSession,
        val counter: Counter,
    )

    /**
     * Start a session at a counter for an operator.
     * 
     * This will:
     * 1. End any existing active session for this operator
     * 2. Check that the counter exists and belongs to the queue
     * 3. Check that no other operator is currently at this counter
     * 4. Create a new session
     * 5. Update the counter's current operator
     */
    fun execute(queueId: UUID, counterId: UUID, operatorId: String): Result {
        // Get the counter and verify it belongs to this queue
        val counter = counterRepository.findById(counterId)
            ?: throw IllegalArgumentException("Counter not found")

        if (counter.queueId != queueId) {
            throw IllegalArgumentException("Counter does not belong to this queue")
        }

        // Check if counter is already occupied by another operator
        val existingSession = counterSessionRepository.findActiveByCounterId(counterId)
        if (existingSession != null && existingSession.operatorId != operatorId) {
            throw IllegalStateException("Counter is already in use by another operator")
        }

        // End any existing session for this operator (they might be switching counters)
        counterSessionRepository.endAllActiveSessionsForOperator(operatorId)

        // Also clear the operator from any other counters they might have been at
        val previousCounter = counterRepository.findByCurrentOperatorId(operatorId)
        if (previousCounter != null && previousCounter.id != counterId) {
            previousCounter.releaseOperator()
            counterRepository.save(previousCounter)
        }

        // Create new session
        val session = CounterSession.start(counterId, operatorId)
        counterSessionRepository.save(session)

        // Update counter with current operator
        counter.assignOperator(operatorId)
        counterRepository.save(counter)

        return Result(session, counter)
    }
}
