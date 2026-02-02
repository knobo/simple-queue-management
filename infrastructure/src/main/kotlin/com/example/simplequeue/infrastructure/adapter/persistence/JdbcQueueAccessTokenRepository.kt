package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.QueueAccessToken
import com.example.simplequeue.domain.port.QueueAccessTokenRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcQueueAccessTokenRepository(
    private val jdbcClient: JdbcClient,
) : QueueAccessTokenRepository {

    override fun save(token: QueueAccessToken) {
        val sql = """
            INSERT INTO queue_access_tokens (
                id, queue_id, token, expires_at, max_uses, use_count, is_active, created_at
            )
            VALUES (
                :id, :queue_id, :token, :expires_at, :max_uses, :use_count, :is_active, :created_at
            )
            ON CONFLICT (id) DO UPDATE SET
                expires_at = EXCLUDED.expires_at,
                max_uses = EXCLUDED.max_uses,
                use_count = EXCLUDED.use_count,
                is_active = EXCLUDED.is_active
        """
        jdbcClient
            .sql(sql)
            .param("id", token.id)
            .param("queue_id", token.queueId)
            .param("token", token.token)
            .param("expires_at", token.expiresAt?.let { Timestamp.from(it) })
            .param("max_uses", token.maxUses)
            .param("use_count", token.useCount)
            .param("is_active", token.isActive)
            .param("created_at", Timestamp.from(token.createdAt))
            .update()
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): QueueAccessToken =
        QueueAccessToken(
            id = rs.getObject("id", UUID::class.java),
            queueId = rs.getObject("queue_id", UUID::class.java),
            token = rs.getString("token"),
            expiresAt = rs.getTimestamp("expires_at")?.toInstant(),
            maxUses = rs.getObject("max_uses") as? Int,
            useCount = rs.getInt("use_count"),
            isActive = rs.getBoolean("is_active"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )

    override fun findById(id: UUID): QueueAccessToken? =
        jdbcClient
            .sql("SELECT * FROM queue_access_tokens WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByToken(token: String): QueueAccessToken? =
        jdbcClient
            .sql("SELECT * FROM queue_access_tokens WHERE token = ?")
            .param(token)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByQueueId(queueId: UUID): List<QueueAccessToken> =
        jdbcClient
            .sql("SELECT * FROM queue_access_tokens WHERE queue_id = ? ORDER BY created_at DESC")
            .param(queueId)
            .query(this::mapRow)
            .list()

    override fun findActiveByQueueId(queueId: UUID): List<QueueAccessToken> =
        jdbcClient
            .sql("""
                SELECT * FROM queue_access_tokens 
                WHERE queue_id = ? 
                AND is_active = true
                AND (expires_at IS NULL OR expires_at > NOW())
                AND (max_uses IS NULL OR use_count < max_uses)
                ORDER BY created_at DESC
            """)
            .param(queueId)
            .query(this::mapRow)
            .list()

    override fun findCurrentToken(queueId: UUID): QueueAccessToken? =
        jdbcClient
            .sql("""
                SELECT * FROM queue_access_tokens 
                WHERE queue_id = ? 
                AND is_active = true
                AND (expires_at IS NULL OR expires_at > NOW())
                AND (max_uses IS NULL OR use_count < max_uses)
                ORDER BY created_at DESC
                LIMIT 1
            """)
            .param(queueId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun incrementUseCount(tokenId: UUID) {
        jdbcClient
            .sql("UPDATE queue_access_tokens SET use_count = use_count + 1 WHERE id = ?")
            .param(tokenId)
            .update()
    }

    override fun deactivateOldTokens(queueId: UUID) {
        // Deactivate ALL active tokens for this queue (new token will be created after)
        jdbcClient
            .sql("""
                UPDATE queue_access_tokens 
                SET is_active = false 
                WHERE queue_id = ? 
                AND is_active = true
            """)
            .param(queueId)
            .update()
    }

    override fun deactivate(tokenId: UUID) {
        jdbcClient
            .sql("UPDATE queue_access_tokens SET is_active = false WHERE id = ?")
            .param(tokenId)
            .update()
    }

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM queue_access_tokens WHERE id = ?")
            .param(id)
            .update()
    }
}
