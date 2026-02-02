package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.AccessTokenMode
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.port.QueueRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcQueueRepository(
    private val jdbcClient: JdbcClient,
) : QueueRepository {
    override fun save(queue: Queue) {
        val sql = """
            INSERT INTO queues (
                id, name, owner_id, open, qr_code_secret, qr_code_type,
                last_rotated_at, ticket_page_mode,
                description, location_hint, estimated_service_time_minutes, organization_id,
                access_token_mode, token_rotation_minutes, token_expiry_minutes, token_max_uses,
                auto_close_seconds, display_token
            )
            VALUES (
                :id, :name, :owner_id, :open, :qr_code_secret, :qr_code_type,
                :last_rotated_at, :ticket_page_mode,
                :description, :location_hint, :estimated_service_time_minutes, :organization_id,
                :access_token_mode, :token_rotation_minutes, :token_expiry_minutes, :token_max_uses,
                :auto_close_seconds, :display_token
            )
            ON CONFLICT (id) DO UPDATE SET
                open = EXCLUDED.open,
                qr_code_secret = EXCLUDED.qr_code_secret,
                qr_code_type = EXCLUDED.qr_code_type,
                last_rotated_at = EXCLUDED.last_rotated_at,
                ticket_page_mode = EXCLUDED.ticket_page_mode,
                description = EXCLUDED.description,
                location_hint = EXCLUDED.location_hint,
                estimated_service_time_minutes = EXCLUDED.estimated_service_time_minutes,
                organization_id = EXCLUDED.organization_id,
                access_token_mode = EXCLUDED.access_token_mode,
                token_rotation_minutes = EXCLUDED.token_rotation_minutes,
                token_expiry_minutes = EXCLUDED.token_expiry_minutes,
                token_max_uses = EXCLUDED.token_max_uses,
                auto_close_seconds = EXCLUDED.auto_close_seconds,
                display_token = EXCLUDED.display_token
        """
        jdbcClient
            .sql(sql)
            .param("id", queue.id)
            .param("name", queue.name)
            .param("owner_id", queue.ownerId)
            .param("open", queue.open)
            .param("qr_code_secret", queue.qrCodeSecret)
            .param("qr_code_type", queue.qrCodeType.name)
            .param("last_rotated_at", Timestamp.from(queue.lastRotatedAt))
            .param("ticket_page_mode", queue.ticketPageMode.name)
            .param("description", queue.description)
            .param("location_hint", queue.locationHint)
            .param("estimated_service_time_minutes", queue.estimatedServiceTimeMinutes)
            .param("organization_id", queue.organizationId)
            .param("access_token_mode", queue.accessTokenMode.name.lowercase())
            .param("token_rotation_minutes", queue.tokenRotationMinutes)
            .param("token_expiry_minutes", queue.tokenExpiryMinutes)
            .param("token_max_uses", queue.tokenMaxUses)
            .param("auto_close_seconds", queue.autoCloseSeconds)
            .param("display_token", queue.displayToken)
            .update()
    }

    private fun mapRow(
        rs: ResultSet,
        rowNum: Int,
    ): Queue =
        Queue(
            id = rs.getObject("id", UUID::class.java),
            name = rs.getString("name"),
            ownerId = rs.getString("owner_id"),
            open = rs.getBoolean("open"),
            qrCodeSecret = rs.getString("qr_code_secret"),
            qrCodeType = Queue.QrCodeType.valueOf(rs.getString("qr_code_type")),
            lastRotatedAt = rs.getTimestamp("last_rotated_at").toInstant(),
            ticketPageMode = Queue.TicketPageMode.valueOf(rs.getString("ticket_page_mode")),
            description = rs.getString("description"),
            locationHint = rs.getString("location_hint"),
            estimatedServiceTimeMinutes = rs.getObject("estimated_service_time_minutes") as? Int,
            organizationId = rs.getObject("organization_id", UUID::class.java),
            accessTokenMode = rs.getString("access_token_mode")?.uppercase()?.let {
                try { AccessTokenMode.valueOf(it) } catch (e: Exception) { AccessTokenMode.STATIC }
            } ?: AccessTokenMode.STATIC,
            tokenRotationMinutes = rs.getInt("token_rotation_minutes"),
            tokenExpiryMinutes = rs.getInt("token_expiry_minutes").takeIf { it > 0 } ?: 60,
            tokenMaxUses = rs.getObject("token_max_uses") as? Int,
            autoCloseSeconds = rs.getInt("auto_close_seconds"),
            displayToken = rs.getString("display_token"),
        )

    override fun findById(id: UUID): Queue? =
        jdbcClient
            .sql("SELECT * FROM queues WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByName(name: String): Queue? =
        jdbcClient
            .sql("SELECT * FROM queues WHERE name = ?")
            .param(name)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByOwnerId(ownerId: String): List<Queue> =
        jdbcClient
            .sql("SELECT * FROM queues WHERE owner_id = ?")
            .param(ownerId)
            .query(this::mapRow)
            .list()

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM queues WHERE id = ?")
            .param(id)
            .update()
    }

    override fun findQueuesNeedingTokenRotation(): List<Queue> {
        val sql = """
            SELECT * FROM queues
            WHERE access_token_mode = 'rotating'
            AND token_rotation_minutes > 0
            AND last_rotated_at + (token_rotation_minutes || ' minutes')::interval < NOW()
        """
        return jdbcClient
            .sql(sql)
            .query(this::mapRow)
            .list()
    }

    fun findByDisplayToken(displayToken: String): Queue? =
        jdbcClient
            .sql("SELECT * FROM queues WHERE display_token = ?")
            .param(displayToken)
            .query(this::mapRow)
            .optional()
            .orElse(null)
}
