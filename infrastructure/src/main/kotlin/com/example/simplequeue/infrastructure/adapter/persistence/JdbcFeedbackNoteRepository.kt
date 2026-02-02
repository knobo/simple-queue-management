package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.FeedbackNote
import com.example.simplequeue.domain.port.FeedbackNoteRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcFeedbackNoteRepository(
    private val jdbcClient: JdbcClient,
) : FeedbackNoteRepository {

    override fun save(note: FeedbackNote): FeedbackNote {
        val sql = """
            INSERT INTO feedback_notes (id, feedback_id, admin_user_id, note, created_at)
            VALUES (:id, :feedback_id, :admin_user_id, :note, :created_at)
            ON CONFLICT (id) DO UPDATE SET
                note = EXCLUDED.note
        """
        jdbcClient
            .sql(sql)
            .param("id", note.id)
            .param("feedback_id", note.feedbackId)
            .param("admin_user_id", note.adminUserId)
            .param("note", note.note)
            .param("created_at", Timestamp.from(note.createdAt))
            .update()
        return note
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): FeedbackNote = FeedbackNote(
        id = rs.getObject("id", UUID::class.java),
        feedbackId = rs.getObject("feedback_id", UUID::class.java),
        adminUserId = rs.getString("admin_user_id"),
        note = rs.getString("note"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
    )

    override fun findById(id: UUID): FeedbackNote? =
        jdbcClient
            .sql("SELECT * FROM feedback_notes WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByFeedbackId(feedbackId: UUID): List<FeedbackNote> =
        jdbcClient
            .sql("SELECT * FROM feedback_notes WHERE feedback_id = ? ORDER BY created_at DESC")
            .param(feedbackId)
            .query(this::mapRow)
            .list()

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM feedback_notes WHERE id = ?")
            .param(id)
            .update()
    }
}
