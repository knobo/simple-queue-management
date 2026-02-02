package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.QueueClosedDate
import com.example.simplequeue.domain.port.QueueClosedDateRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

@Repository
class JdbcQueueClosedDateRepository(
    private val jdbcClient: JdbcClient,
) : QueueClosedDateRepository {

    override fun save(closedDate: QueueClosedDate) {
        val sql = """
            INSERT INTO queue_closed_dates (id, queue_id, closed_date, reason)
            VALUES (:id, :queue_id, :closed_date, :reason)
            ON CONFLICT (queue_id, closed_date) DO UPDATE SET
                reason = EXCLUDED.reason
        """
        jdbcClient
            .sql(sql)
            .param("id", closedDate.id)
            .param("queue_id", closedDate.queueId)
            .param("closed_date", Date.valueOf(closedDate.closedDate))
            .param("reason", closedDate.reason)
            .update()
    }

    override fun saveAll(closedDates: List<QueueClosedDate>) {
        closedDates.forEach { save(it) }
    }

    override fun findByQueueId(queueId: UUID): List<QueueClosedDate> =
        jdbcClient
            .sql("SELECT * FROM queue_closed_dates WHERE queue_id = ? ORDER BY closed_date")
            .param(queueId)
            .query(this::mapRow)
            .list()

    override fun findByQueueIdAndDate(queueId: UUID, date: LocalDate): QueueClosedDate? =
        jdbcClient
            .sql("SELECT * FROM queue_closed_dates WHERE queue_id = ? AND closed_date = ?")
            .param(queueId)
            .param(Date.valueOf(date))
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun deleteByQueueId(queueId: UUID) {
        jdbcClient
            .sql("DELETE FROM queue_closed_dates WHERE queue_id = ?")
            .param(queueId)
            .update()
    }

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM queue_closed_dates WHERE id = ?")
            .param(id)
            .update()
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): QueueClosedDate =
        QueueClosedDate(
            id = rs.getObject("id", UUID::class.java),
            queueId = rs.getObject("queue_id", UUID::class.java),
            closedDate = rs.getDate("closed_date").toLocalDate(),
            reason = rs.getString("reason"),
        )
}
