package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.QueueState
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.QueueStateRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class JdbcQueueStateRepository(
    private val jdbcClient: JdbcClient,
) : QueueStateRepository {
    override fun save(state: QueueState) {
        val sql = """
            INSERT INTO queue_states (id, queue_id, name, status, order_index)
            VALUES (:id, :queueId, :name, :status, :orderIndex)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                status = EXCLUDED.status,
                order_index = EXCLUDED.order_index
        """
        jdbcClient
            .sql(sql)
            .param("id", state.id)
            .param("queueId", state.queueId)
            .param("name", state.name)
            .param("status", state.status.name)
            .param("orderIndex", state.orderIndex)
            .update()
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): QueueState =
        QueueState(
            id = rs.getObject("id", UUID::class.java),
            queueId = rs.getObject("queue_id", UUID::class.java),
            name = rs.getString("name"),
            status = Ticket.TicketStatus.valueOf(rs.getString("status")),
            orderIndex = rs.getInt("order_index"),
        )

    override fun findByQueueId(queueId: UUID): List<QueueState> =
        jdbcClient
            .sql("SELECT * FROM queue_states WHERE queue_id = ? ORDER BY order_index ASC")
            .param(queueId)
            .query(this::mapRow)
            .list()

    override fun findByQueueIdAndStatus(
        queueId: UUID,
        status: Ticket.TicketStatus,
    ): List<QueueState> =
        jdbcClient
            .sql("SELECT * FROM queue_states WHERE queue_id = ? AND status = ? ORDER BY order_index ASC")
            .param(queueId)
            .param(status.name)
            .query(this::mapRow)
            .list()

    override fun deleteByQueueId(queueId: UUID) {
        jdbcClient
            .sql("DELETE FROM queue_states WHERE queue_id = ?")
            .param(queueId)
            .update()
    }

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM queue_states WHERE id = ?")
            .param(id)
            .update()
    }
}
