package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.Invite
import com.example.simplequeue.domain.model.MemberRole
import com.example.simplequeue.domain.port.InviteRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcInviteRepository(
    private val jdbcClient: JdbcClient,
) : InviteRepository {

    override fun save(invite: Invite) {
        val sql = """
            INSERT INTO invites (
                id, queue_id, email, role, token, status,
                created_at, expires_at, accepted_at, accepted_by_user_id
            )
            VALUES (
                :id, :queue_id, :email, :role, :token, :status,
                :created_at, :expires_at, :accepted_at, :accepted_by_user_id
            )
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                accepted_at = EXCLUDED.accepted_at,
                accepted_by_user_id = EXCLUDED.accepted_by_user_id
        """
        jdbcClient
            .sql(sql)
            .param("id", invite.id)
            .param("queue_id", invite.queueId)
            .param("email", invite.email)
            .param("role", invite.role.name)
            .param("token", invite.token)
            .param("status", invite.status.name)
            .param("created_at", Timestamp.from(invite.createdAt))
            .param("expires_at", Timestamp.from(invite.expiresAt))
            .param("accepted_at", invite.acceptedAt?.let { Timestamp.from(it) })
            .param("accepted_by_user_id", invite.acceptedByUserId)
            .update()
    }

    override fun findById(id: UUID): Invite? =
        jdbcClient
            .sql("SELECT * FROM invites WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByToken(token: String): Invite? =
        jdbcClient
            .sql("SELECT * FROM invites WHERE token = ?")
            .param(token)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByQueueId(queueId: UUID): List<Invite> =
        jdbcClient
            .sql("SELECT * FROM invites WHERE queue_id = ? ORDER BY created_at DESC")
            .param(queueId)
            .query(this::mapRow)
            .list()

    override fun findPendingByQueueId(queueId: UUID): List<Invite> =
        jdbcClient
            .sql("SELECT * FROM invites WHERE queue_id = ? AND status = 'PENDING' ORDER BY created_at DESC")
            .param(queueId)
            .query(this::mapRow)
            .list()

    override fun countPendingByQueueId(queueId: UUID): Int =
        jdbcClient
            .sql("SELECT COUNT(*) FROM invites WHERE queue_id = ? AND status = 'PENDING'")
            .param(queueId)
            .query(Int::class.java)
            .single()

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM invites WHERE id = ?")
            .param(id)
            .update()
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): Invite {
        val acceptedAtTimestamp = rs.getTimestamp("accepted_at")
        return Invite(
            id = rs.getObject("id", UUID::class.java),
            queueId = rs.getObject("queue_id", UUID::class.java),
            email = rs.getString("email"),
            role = MemberRole.valueOf(rs.getString("role")),
            token = rs.getString("token"),
            status = Invite.InviteStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            expiresAt = rs.getTimestamp("expires_at").toInstant(),
            acceptedAt = acceptedAtTimestamp?.toInstant(),
            acceptedByUserId = rs.getString("accepted_by_user_id"),
        )
    }
}
