package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.SellerActivityLog
import com.example.simplequeue.domain.port.SellerActivityLogRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class JdbcSellerActivityLogRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) : SellerActivityLogRepository {

    override fun save(log: SellerActivityLog) {
        val sql = """
            INSERT INTO seller_activity_log (
                id, seller_id, activity_type, referral_id, created_at, details
            )
            VALUES (
                :id, :seller_id, :activity_type, :referral_id, :created_at, :details::jsonb
            )
        """
        jdbcClient
            .sql(sql)
            .param("id", log.id)
            .param("seller_id", log.sellerId)
            .param("activity_type", log.activityType.name)
            .param("referral_id", log.referralId)
            .param("created_at", Timestamp.from(log.createdAt))
            .param("details", log.details?.let { objectMapper.writeValueAsString(it) })
            .update()
    }

    override fun findBySellerId(sellerId: UUID): List<SellerActivityLog> =
        jdbcClient
            .sql("SELECT * FROM seller_activity_log WHERE seller_id = ? ORDER BY created_at DESC")
            .param(sellerId)
            .query(this::mapRow)
            .list()

    override fun findBySellerIdAndType(
        sellerId: UUID,
        type: SellerActivityLog.ActivityType,
    ): List<SellerActivityLog> =
        jdbcClient
            .sql("SELECT * FROM seller_activity_log WHERE seller_id = ? AND activity_type = ? ORDER BY created_at DESC")
            .param(sellerId)
            .param(type.name)
            .query(this::mapRow)
            .list()

    override fun countSalesBySellerIdSince(sellerId: UUID, since: Instant): Int =
        jdbcClient
            .sql("SELECT COUNT(*) FROM seller_activity_log WHERE seller_id = ? AND activity_type = 'SALE' AND created_at >= ?")
            .param(sellerId)
            .param(Timestamp.from(since))
            .query(Int::class.java)
            .single() ?: 0

    private fun mapRow(rs: ResultSet, rowNum: Int): SellerActivityLog {
        val detailsJson = rs.getString("details")
        val details: Map<String, Any>? = detailsJson?.let {
            objectMapper.readValue(it, object : TypeReference<Map<String, Any>>() {})
        }

        return SellerActivityLog(
            id = rs.getObject("id", UUID::class.java),
            sellerId = rs.getObject("seller_id", UUID::class.java),
            activityType = SellerActivityLog.ActivityType.valueOf(rs.getString("activity_type")),
            referralId = rs.getObject("referral_id", UUID::class.java),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            details = details,
        )
    }
}
