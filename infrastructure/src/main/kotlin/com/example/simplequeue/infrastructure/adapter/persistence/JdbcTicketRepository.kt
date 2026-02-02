package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.TicketRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcTicketRepository(
    private val jdbcClient: JdbcClient,
) : TicketRepository {
    override fun save(ticket: Ticket) {
        val sql = """
            INSERT INTO tickets (id, queue_id, number, name, status, state_id, ntfy_topic, created_at, called_at, completed_at, user_id, guest_email, counter_id, served_by)
            VALUES (:id, :queue_id, :number, :name, :status, :state_id, :ntfy_topic, :created_at, :called_at, :completed_at, :user_id, :guest_email, :counter_id, :served_by)
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                state_id = EXCLUDED.state_id,
                name = EXCLUDED.name,
                called_at = EXCLUDED.called_at,
                completed_at = EXCLUDED.completed_at,
                user_id = EXCLUDED.user_id,
                guest_email = EXCLUDED.guest_email,
                counter_id = EXCLUDED.counter_id,
                served_by = EXCLUDED.served_by
        """
        jdbcClient
            .sql(sql)
            .param("id", ticket.id)
            .param("queue_id", ticket.queueId)
            .param("number", ticket.number)
            .param("name", ticket.name)
            .param("status", ticket.status.name)
            .param("state_id", ticket.stateId)
            .param("ntfy_topic", ticket.ntfyTopic)
            .param("created_at", Timestamp.from(ticket.createdAt))
            .param("called_at", ticket.calledAt?.let { Timestamp.from(it) })
            .param("completed_at", ticket.completedAt?.let { Timestamp.from(it) })
            .param("user_id", ticket.userId)
            .param("guest_email", ticket.guestEmail)
            .param("counter_id", ticket.counterId)
            .param("served_by", ticket.servedBy)
            .update()
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): Ticket =
        Ticket(
            id = rs.getObject("id", UUID::class.java),
            queueId = rs.getObject("queue_id", UUID::class.java),
            number = rs.getInt("number"),
            name = rs.getString("name"),
            status = Ticket.TicketStatus.valueOf(rs.getString("status")),
            stateId = rs.getObject("state_id", UUID::class.java),
            ntfyTopic = rs.getString("ntfy_topic"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            calledAt = rs.getTimestamp("called_at")?.toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
            userId = rs.getString("user_id"),
            guestEmail = rs.getString("guest_email"),
            counterId = rs.getObject("counter_id", UUID::class.java),
            servedBy = rs.getString("served_by"),
        )

    override fun findById(id: UUID): Ticket? =
        jdbcClient
            .sql("SELECT * FROM tickets WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun countByQueueId(queueId: UUID): Int =
        jdbcClient
            .sql("SELECT COUNT(*) FROM tickets WHERE queue_id = ?")
            .param(queueId)
            .query(Int::class.java)
            .single()

    override fun findByQueueIdAndStatus(
        queueId: UUID,
        status: Ticket.TicketStatus,
    ): List<Ticket> =
        jdbcClient
            .sql("SELECT * FROM tickets WHERE queue_id = ? AND status = ? ORDER BY number ASC")
            .param(queueId)
            .param(status.name)
            .query(this::mapRow)
            .list()

    override fun getNextNumber(queueId: UUID): Int =
        jdbcClient
            .sql("SELECT COALESCE(MAX(number), 0) + 1 FROM tickets WHERE queue_id = ?")
            .param(queueId)
            .query(Int::class.java)
            .single()

    override fun getLastCalledNumber(queueId: UUID): Int =
        jdbcClient
            .sql("SELECT COALESCE(MAX(number), 0) FROM tickets WHERE queue_id = ? AND status IN ('CALLED', 'COMPLETED')")
            .param(queueId)
            .query(Int::class.java)
            .single()

    override fun getAverageProcessingTimeSeconds(queueId: UUID): Double {
        val sql = """
            SELECT AVG(EXTRACT(EPOCH FROM (completed_at - called_at)))
            FROM tickets
            WHERE queue_id = ? AND status = 'COMPLETED' AND called_at IS NOT NULL AND completed_at IS NOT NULL
        """
        val avg =
            jdbcClient
                .sql(sql)
                .param(queueId)
                .query(Double::class.javaObjectType) // Use object type for nullable Double
                .optional()
                .orElse(null)

        return avg ?: 300.0 // Default 5 minutes if no data
    }

    // =============================================================================
    // Customer Portal methods
    // =============================================================================

    override fun findActiveByUserId(userId: String): Ticket? {
        val sql = """
            SELECT * FROM tickets 
            WHERE user_id = ? AND status IN ('WAITING', 'CALLED')
            ORDER BY created_at DESC
            LIMIT 1
        """
        return jdbcClient
            .sql(sql)
            .param(userId)
            .query(this::mapRow)
            .optional()
            .orElse(null)
    }

    override fun findHistoryByUserId(userId: String, limit: Int, offset: Int): List<Ticket> {
        val sql = """
            SELECT * FROM tickets 
            WHERE user_id = ? AND status IN ('COMPLETED', 'CANCELLED')
            ORDER BY COALESCE(completed_at, created_at) DESC
            LIMIT ? OFFSET ?
        """
        return jdbcClient
            .sql(sql)
            .param(userId)
            .param(limit)
            .param(offset)
            .query(this::mapRow)
            .list()
    }

    override fun countHistoryByUserId(userId: String): Int =
        jdbcClient
            .sql("SELECT COUNT(*) FROM tickets WHERE user_id = ? AND status IN ('COMPLETED', 'CANCELLED')")
            .param(userId)
            .query(Int::class.java)
            .single()

    override fun countPositionInQueue(queueId: UUID, ticketNumber: Int): Int =
        jdbcClient
            .sql("SELECT COUNT(*) FROM tickets WHERE queue_id = ? AND status = 'WAITING' AND number < ?")
            .param(queueId)
            .param(ticketNumber)
            .query(Int::class.java)
            .single()
}
