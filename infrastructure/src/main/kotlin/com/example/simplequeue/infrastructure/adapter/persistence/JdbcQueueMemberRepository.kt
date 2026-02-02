package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.MemberRole
import com.example.simplequeue.domain.model.QueueMember
import com.example.simplequeue.domain.port.QueueMemberRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcQueueMemberRepository(
    private val jdbcClient: JdbcClient,
) : QueueMemberRepository {

    override fun save(member: QueueMember) {
        val sql = """
            INSERT INTO queue_members (id, queue_id, user_id, role, joined_at, invited_by)
            VALUES (:id, :queue_id, :user_id, :role, :joined_at, :invited_by)
            ON CONFLICT (id) DO UPDATE SET
                role = EXCLUDED.role
        """
        jdbcClient
            .sql(sql)
            .param("id", member.id)
            .param("queue_id", member.queueId)
            .param("user_id", member.userId)
            .param("role", member.role.name)
            .param("joined_at", Timestamp.from(member.joinedAt))
            .param("invited_by", member.invitedBy)
            .update()
    }

    override fun findById(id: UUID): QueueMember? =
        jdbcClient
            .sql("SELECT * FROM queue_members WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByQueueId(queueId: UUID): List<QueueMember> =
        jdbcClient
            .sql("SELECT * FROM queue_members WHERE queue_id = ?")
            .param(queueId)
            .query(this::mapRow)
            .list()

    override fun findByUserId(userId: String): List<QueueMember> =
        jdbcClient
            .sql("SELECT * FROM queue_members WHERE user_id = ?")
            .param(userId)
            .query(this::mapRow)
            .list()

    override fun findByQueueIdAndUserId(queueId: UUID, userId: String): QueueMember? =
        jdbcClient
            .sql("SELECT * FROM queue_members WHERE queue_id = ? AND user_id = ?")
            .param(queueId)
            .param(userId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM queue_members WHERE id = ?")
            .param(id)
            .update()
    }

    override fun countByQueueId(queueId: UUID): Int =
        jdbcClient
            .sql("SELECT COUNT(*) FROM queue_members WHERE queue_id = ?")
            .param(queueId)
            .query(Int::class.java)
            .single()

    private fun mapRow(rs: ResultSet, rowNum: Int): QueueMember =
        QueueMember(
            id = rs.getObject("id", UUID::class.java),
            queueId = rs.getObject("queue_id", UUID::class.java),
            userId = rs.getString("user_id"),
            role = MemberRole.valueOf(rs.getString("role")),
            joinedAt = rs.getTimestamp("joined_at").toInstant(),
            invitedBy = rs.getString("invited_by"),
        )
}
