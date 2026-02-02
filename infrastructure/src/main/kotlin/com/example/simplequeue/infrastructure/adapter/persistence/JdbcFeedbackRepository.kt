package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.Feedback
import com.example.simplequeue.domain.model.FeedbackCategory
import com.example.simplequeue.domain.model.FeedbackStatus
import com.example.simplequeue.domain.model.FeedbackType
import com.example.simplequeue.domain.port.FeedbackRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcFeedbackRepository(
    private val jdbcClient: JdbcClient,
) : FeedbackRepository {

    override fun save(feedback: Feedback): Feedback {
        val sql = """
            INSERT INTO feedback (
                id, user_id, user_email, type, category, title, description,
                screenshot_url, status, user_agent, current_url, subscription_tier,
                created_at, updated_at, resolved_at
            )
            VALUES (
                :id, :user_id, :user_email, :type, :category, :title, :description,
                :screenshot_url, :status, :user_agent, :current_url, :subscription_tier,
                :created_at, :updated_at, :resolved_at
            )
            ON CONFLICT (id) DO UPDATE SET
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at,
                resolved_at = EXCLUDED.resolved_at
        """
        jdbcClient
            .sql(sql)
            .param("id", feedback.id)
            .param("user_id", feedback.userId)
            .param("user_email", feedback.userEmail)
            .param("type", feedback.type.name)
            .param("category", feedback.category.name)
            .param("title", feedback.title)
            .param("description", feedback.description)
            .param("screenshot_url", feedback.screenshotUrl)
            .param("status", feedback.status.name)
            .param("user_agent", feedback.userAgent)
            .param("current_url", feedback.currentUrl)
            .param("subscription_tier", feedback.subscriptionTier)
            .param("created_at", Timestamp.from(feedback.createdAt))
            .param("updated_at", Timestamp.from(feedback.updatedAt))
            .param("resolved_at", feedback.resolvedAt?.let { Timestamp.from(it) })
            .update()
        return feedback
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): Feedback = Feedback(
        id = rs.getObject("id", UUID::class.java),
        userId = rs.getString("user_id"),
        userEmail = rs.getString("user_email"),
        type = FeedbackType.valueOf(rs.getString("type")),
        category = FeedbackCategory.valueOf(rs.getString("category")),
        title = rs.getString("title"),
        description = rs.getString("description"),
        screenshotUrl = rs.getString("screenshot_url"),
        status = FeedbackStatus.valueOf(rs.getString("status")),
        userAgent = rs.getString("user_agent"),
        currentUrl = rs.getString("current_url"),
        subscriptionTier = rs.getString("subscription_tier"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
        resolvedAt = rs.getTimestamp("resolved_at")?.toInstant(),
    )

    override fun findById(id: UUID): Feedback? =
        jdbcClient
            .sql("SELECT * FROM feedback WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByUserId(userId: String): List<Feedback> =
        jdbcClient
            .sql("SELECT * FROM feedback WHERE user_id = ? ORDER BY created_at DESC")
            .param(userId)
            .query(this::mapRow)
            .list()

    override fun findAll(): List<Feedback> =
        jdbcClient
            .sql("SELECT * FROM feedback ORDER BY created_at DESC")
            .query(this::mapRow)
            .list()

    override fun findFiltered(
        type: FeedbackType?,
        status: FeedbackStatus?,
        category: FeedbackCategory?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<Feedback> {
        val conditions = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()

        type?.let {
            conditions.add("type = :type")
            params["type"] = it.name
        }
        status?.let {
            conditions.add("status = :status")
            params["status"] = it.name
        }
        category?.let {
            conditions.add("category = :category")
            params["category"] = it.name
        }
        search?.takeIf { it.isNotBlank() }?.let {
            conditions.add("(title ILIKE :search OR description ILIKE :search)")
            params["search"] = "%$it%"
        }

        val whereClause = if (conditions.isNotEmpty()) {
            "WHERE " + conditions.joinToString(" AND ")
        } else {
            ""
        }

        val sql = "SELECT * FROM feedback $whereClause ORDER BY created_at DESC LIMIT :limit OFFSET :offset"
        params["limit"] = limit
        params["offset"] = offset

        var query = jdbcClient.sql(sql)
        params.forEach { (key, value) ->
            query = query.param(key, value)
        }

        return query.query(this::mapRow).list()
    }

    override fun countByStatus(): Map<FeedbackStatus, Int> {
        val sql = "SELECT status, COUNT(*) as count FROM feedback GROUP BY status"
        val results = jdbcClient
            .sql(sql)
            .query { rs, _ ->
                FeedbackStatus.valueOf(rs.getString("status")) to rs.getInt("count")
            }
            .list()

        // Ensure all statuses are represented
        return FeedbackStatus.entries.associateWith { status ->
            results.find { it.first == status }?.second ?: 0
        }
    }

    override fun countByType(): Map<FeedbackType, Int> {
        val sql = "SELECT type, COUNT(*) as count FROM feedback GROUP BY type"
        val results = jdbcClient
            .sql(sql)
            .query { rs, _ ->
                FeedbackType.valueOf(rs.getString("type")) to rs.getInt("count")
            }
            .list()

        // Ensure all types are represented
        return FeedbackType.entries.associateWith { type ->
            results.find { it.first == type }?.second ?: 0
        }
    }

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM feedback WHERE id = ?")
            .param(id)
            .update()
    }
}
