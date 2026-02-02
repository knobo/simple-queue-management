package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.OpeningHours
import com.example.simplequeue.domain.port.OpeningHoursRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Time
import java.time.DayOfWeek
import java.util.UUID

@Repository
class JdbcOpeningHoursRepository(
    private val jdbcClient: JdbcClient,
) : OpeningHoursRepository {

    override fun save(hours: OpeningHours) {
        val sql = """
            INSERT INTO opening_hours (id, organization_id, day_of_week, opens_at, closes_at, is_closed)
            VALUES (:id, :organization_id, :day_of_week, :opens_at, :closes_at, :is_closed)
            ON CONFLICT (organization_id, day_of_week) DO UPDATE SET
                opens_at = EXCLUDED.opens_at,
                closes_at = EXCLUDED.closes_at,
                is_closed = EXCLUDED.is_closed
        """
        jdbcClient
            .sql(sql)
            .param("id", hours.id)
            .param("organization_id", hours.organizationId)
            .param("day_of_week", hours.dayOfWeek.value)
            .param("opens_at", Time.valueOf(hours.opensAt))
            .param("closes_at", Time.valueOf(hours.closesAt))
            .param("is_closed", hours.isClosed)
            .update()
    }

    override fun saveAll(hours: List<OpeningHours>) {
        hours.forEach { save(it) }
    }

    override fun findByOrganizationId(organizationId: UUID): List<OpeningHours> =
        jdbcClient
            .sql("SELECT * FROM opening_hours WHERE organization_id = ? ORDER BY day_of_week")
            .param(organizationId)
            .query(this::mapRow)
            .list()

    override fun findByOrganizationIdAndDay(organizationId: UUID, dayOfWeek: DayOfWeek): OpeningHours? =
        jdbcClient
            .sql("SELECT * FROM opening_hours WHERE organization_id = ? AND day_of_week = ?")
            .param(organizationId)
            .param(dayOfWeek.value)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun deleteByOrganizationId(organizationId: UUID) {
        jdbcClient
            .sql("DELETE FROM opening_hours WHERE organization_id = ?")
            .param(organizationId)
            .update()
    }

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM opening_hours WHERE id = ?")
            .param(id)
            .update()
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): OpeningHours =
        OpeningHours(
            id = rs.getObject("id", UUID::class.java),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            dayOfWeek = DayOfWeek.of(rs.getInt("day_of_week")),
            opensAt = rs.getTime("opens_at").toLocalTime(),
            closesAt = rs.getTime("closes_at").toLocalTime(),
            isClosed = rs.getBoolean("is_closed"),
        )
}
