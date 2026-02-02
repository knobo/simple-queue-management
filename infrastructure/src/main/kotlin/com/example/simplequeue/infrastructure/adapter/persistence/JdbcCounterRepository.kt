package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.Counter
import com.example.simplequeue.domain.port.CounterRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcCounterRepository(
    private val jdbcClient: JdbcClient,
) : CounterRepository {
    
    override fun save(counter: Counter) {
        val sql = """
            INSERT INTO counters (id, queue_id, number, name, current_operator_id, current_ticket_id, created_at, updated_at)
            VALUES (:id, :queue_id, :number, :name, :current_operator_id, :current_ticket_id, :created_at, :updated_at)
            ON CONFLICT (id) DO UPDATE SET
                number = EXCLUDED.number,
                name = EXCLUDED.name,
                current_operator_id = EXCLUDED.current_operator_id,
                current_ticket_id = EXCLUDED.current_ticket_id,
                updated_at = EXCLUDED.updated_at
        """
        jdbcClient
            .sql(sql)
            .param("id", counter.id)
            .param("queue_id", counter.queueId)
            .param("number", counter.number)
            .param("name", counter.name)
            .param("current_operator_id", counter.currentOperatorId)
            .param("current_ticket_id", counter.currentTicketId)
            .param("created_at", Timestamp.from(counter.createdAt))
            .param("updated_at", Timestamp.from(counter.updatedAt))
            .update()
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): Counter = Counter(
        id = rs.getObject("id", UUID::class.java),
        queueId = rs.getObject("queue_id", UUID::class.java),
        number = rs.getInt("number"),
        name = rs.getString("name"),
        currentOperatorId = rs.getString("current_operator_id"),
        currentTicketId = rs.getObject("current_ticket_id", UUID::class.java),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )

    override fun findById(id: UUID): Counter? =
        jdbcClient
            .sql("SELECT * FROM counters WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByQueueId(queueId: UUID): List<Counter> =
        jdbcClient
            .sql("SELECT * FROM counters WHERE queue_id = ? ORDER BY number ASC")
            .param(queueId)
            .query(this::mapRow)
            .list()

    override fun findByQueueIdAndNumber(queueId: UUID, number: Int): Counter? =
        jdbcClient
            .sql("SELECT * FROM counters WHERE queue_id = ? AND number = ?")
            .param(queueId)
            .param(number)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByCurrentOperatorId(operatorId: String): Counter? =
        jdbcClient
            .sql("SELECT * FROM counters WHERE current_operator_id = ?")
            .param(operatorId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByCurrentTicketId(ticketId: UUID): Counter? =
        jdbcClient
            .sql("SELECT * FROM counters WHERE current_ticket_id = ?")
            .param(ticketId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM counters WHERE id = ?")
            .param(id)
            .update()
    }

    override fun countByQueueId(queueId: UUID): Int =
        jdbcClient
            .sql("SELECT COUNT(*) FROM counters WHERE queue_id = ?")
            .param(queueId)
            .query(Int::class.java)
            .single()

    override fun getNextNumber(queueId: UUID): Int =
        jdbcClient
            .sql("SELECT COALESCE(MAX(number), 0) + 1 FROM counters WHERE queue_id = ?")
            .param(queueId)
            .query(Int::class.java)
            .single()
}
