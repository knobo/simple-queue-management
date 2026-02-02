package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.QueueOpeningHours
import com.example.simplequeue.domain.port.QueueOpeningHoursRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Time
import java.time.DayOfWeek
import java.util.UUID

@Repository
class JdbcQueueOpeningHoursRepository(
    private val jdbcClient: JdbcClient,
) : QueueOpeningHoursRepository {

    override fun save(hours: QueueOpeningHours) {
        val sql = """
            INSERT INTO queue_opening_hours (id, queue_id, day_of_week, open_time, close_time, is_closed)
            VALUES (:id, :queue_id, :day_of_week, :open_time, :close_time, :is_closed)
            ON CONFLICT (queue_id, day_of_week) DO UPDATE SET
                open_time = EXCLUDED.open_time,
                close_time = EXCLUDED.close_time,
                is_closed = EXCLUDED.is_closed
        """
        jdbcClient
            .sql(sql)
            .param("id", hours.id)
            .param("queue_id", hours.queueId)
            .param("day_of_week", hours.dayOfWeek.value - 1) // Convert 1-7 to 0-6
            .param("open_time", Time.valueOf(hours.openTime))
            .param("close_time", Time.valueOf(hours.closeTime))
            .param("is_closed", hours.isClosed)
            .update()
    }

    override fun saveAll(hours: List<QueueOpeningHours>) {
        hours.forEach { save(it) }
    }

    override fun findByQueueId(queueId: UUID): List<QueueOpeningHours> =
        jdbcClient
            .sql("SELECT * FROM queue_opening_hours WHERE queue_id = ? ORDER BY day_of_week")
            .param(queueId)
            .query(this::mapRow)
            .list()

    override fun findByQueueIdAndDay(queueId: UUID, dayOfWeek: DayOfWeek): QueueOpeningHours? =
        jdbcClient
            .sql("SELECT * FROM queue_opening_hours WHERE queue_id = ? AND day_of_week = ?")
            .param(queueId)
            .param(dayOfWeek.value - 1) // Convert 1-7 to 0-6
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun deleteByQueueId(queueId: UUID) {
        jdbcClient
            .sql("DELETE FROM queue_opening_hours WHERE queue_id = ?")
            .param(queueId)
            .update()
    }

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM queue_opening_hours WHERE id = ?")
            .param(id)
            .update()
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): QueueOpeningHours =
        QueueOpeningHours(
            id = rs.getObject("id", UUID::class.java),
            queueId = rs.getObject("queue_id", UUID::class.java),
            dayOfWeek = DayOfWeek.of(rs.getInt("day_of_week") + 1), // Convert 0-6 to 1-7
            openTime = rs.getTime("open_time").toLocalTime(),
            closeTime = rs.getTime("close_time").toLocalTime(),
            isClosed = rs.getBoolean("is_closed"),
        )
}
