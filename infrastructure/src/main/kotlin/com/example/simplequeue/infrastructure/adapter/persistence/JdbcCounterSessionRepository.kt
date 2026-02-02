package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.CounterSession
import com.example.simplequeue.domain.port.CounterSessionRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class JdbcCounterSessionRepository(
    private val jdbcClient: JdbcClient,
) : CounterSessionRepository {

    override fun save(session: CounterSession) {
        val sql = """
            INSERT INTO counter_sessions (id, counter_id, operator_id, started_at, ended_at)
            VALUES (:id, :counter_id, :operator_id, :started_at, :ended_at)
            ON CONFLICT (id) DO UPDATE SET
                ended_at = EXCLUDED.ended_at
        """
        jdbcClient
            .sql(sql)
            .param("id", session.id)
            .param("counter_id", session.counterId)
            .param("operator_id", session.operatorId)
            .param("started_at", Timestamp.from(session.startedAt))
            .param("ended_at", session.endedAt?.let { Timestamp.from(it) })
            .update()
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): CounterSession = CounterSession(
        id = rs.getObject("id", UUID::class.java),
        counterId = rs.getObject("counter_id", UUID::class.java),
        operatorId = rs.getString("operator_id"),
        startedAt = rs.getTimestamp("started_at").toInstant(),
        endedAt = rs.getTimestamp("ended_at")?.toInstant(),
    )

    override fun findById(id: UUID): CounterSession? =
        jdbcClient
            .sql("SELECT * FROM counter_sessions WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByCounterId(counterId: UUID): List<CounterSession> =
        jdbcClient
            .sql("SELECT * FROM counter_sessions WHERE counter_id = ? ORDER BY started_at DESC")
            .param(counterId)
            .query(this::mapRow)
            .list()

    override fun findActiveByCounterId(counterId: UUID): CounterSession? =
        jdbcClient
            .sql("SELECT * FROM counter_sessions WHERE counter_id = ? AND ended_at IS NULL")
            .param(counterId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findActiveByOperatorId(operatorId: String): CounterSession? =
        jdbcClient
            .sql("SELECT * FROM counter_sessions WHERE operator_id = ? AND ended_at IS NULL")
            .param(operatorId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun endSession(id: UUID) {
        jdbcClient
            .sql("UPDATE counter_sessions SET ended_at = ? WHERE id = ? AND ended_at IS NULL")
            .param(Timestamp.from(Instant.now()))
            .param(id)
            .update()
    }

    override fun endAllActiveSessionsForOperator(operatorId: String) {
        jdbcClient
            .sql("UPDATE counter_sessions SET ended_at = ? WHERE operator_id = ? AND ended_at IS NULL")
            .param(Timestamp.from(Instant.now()))
            .param(operatorId)
            .update()
    }

    override fun endAllActiveSessionsForCounter(counterId: UUID) {
        jdbcClient
            .sql("UPDATE counter_sessions SET ended_at = ? WHERE counter_id = ? AND ended_at IS NULL")
            .param(Timestamp.from(Instant.now()))
            .param(counterId)
            .update()
    }
}
