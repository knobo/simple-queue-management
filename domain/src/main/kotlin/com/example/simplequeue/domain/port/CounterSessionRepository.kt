package com.example.simplequeue.domain.port

import com.example.simplequeue.domain.model.CounterSession
import java.util.UUID

interface CounterSessionRepository {
    fun save(session: CounterSession)
    fun findById(id: UUID): CounterSession?
    fun findByCounterId(counterId: UUID): List<CounterSession>
    fun findActiveByCounterId(counterId: UUID): CounterSession?
    fun findActiveByOperatorId(operatorId: String): CounterSession?
    fun endSession(id: UUID)
    fun endAllActiveSessionsForOperator(operatorId: String)
    fun endAllActiveSessionsForCounter(counterId: UUID)
}
