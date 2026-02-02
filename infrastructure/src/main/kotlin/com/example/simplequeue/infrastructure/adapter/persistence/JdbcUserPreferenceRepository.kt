package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.UserPreference
import com.example.simplequeue.domain.port.UserPreferenceRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp

@Repository
class JdbcUserPreferenceRepository(
    private val jdbcClient: JdbcClient,
) : UserPreferenceRepository {

    override fun save(preference: UserPreference) {
        jdbcClient
            .sql("""
                INSERT INTO user_preferences (user_id, preferred_language, created_at, updated_at)
                VALUES (:user_id, :preferred_language, :created_at, :updated_at)
                ON CONFLICT (user_id) DO UPDATE SET
                    preferred_language = EXCLUDED.preferred_language,
                    updated_at = EXCLUDED.updated_at
            """)
            .param("user_id", preference.userId)
            .param("preferred_language", preference.preferredLanguage)
            .param("created_at", Timestamp.from(preference.createdAt))
            .param("updated_at", Timestamp.from(preference.updatedAt))
            .update()
    }

    override fun findByUserId(userId: String): UserPreference? =
        jdbcClient
            .sql("SELECT * FROM user_preferences WHERE user_id = ?")
            .param(userId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun delete(userId: String) {
        jdbcClient
            .sql("DELETE FROM user_preferences WHERE user_id = ?")
            .param(userId)
            .update()
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): UserPreference =
        UserPreference(
            userId = rs.getString("user_id"),
            preferredLanguage = rs.getString("preferred_language"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
