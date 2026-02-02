package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.CommissionEntry
import com.example.simplequeue.domain.port.CommissionEntryRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.util.UUID

@Repository
class JdbcCommissionEntryRepository(
    private val jdbcClient: JdbcClient,
) : CommissionEntryRepository {

    override fun save(entry: CommissionEntry) {
        val sql = """
            INSERT INTO commission_entries (
                id, seller_id, referral_id,
                source_type, source_reference,
                gross_amount, commission_percent, commission_amount,
                period_start, period_end, created_at
            )
            VALUES (
                :id, :seller_id, :referral_id,
                :source_type, :source_reference,
                :gross_amount, :commission_percent, :commission_amount,
                :period_start, :period_end, :created_at
            )
            ON CONFLICT (id) DO NOTHING
        """
        jdbcClient
            .sql(sql)
            .param("id", entry.id)
            .param("seller_id", entry.sellerId)
            .param("referral_id", entry.referralId)
            .param("source_type", entry.sourceType.name)
            .param("source_reference", entry.sourceReference)
            .param("gross_amount", entry.grossAmount)
            .param("commission_percent", entry.commissionPercent)
            .param("commission_amount", entry.commissionAmount)
            .param("period_start", Date.valueOf(entry.periodStart))
            .param("period_end", Date.valueOf(entry.periodEnd))
            .param("created_at", Timestamp.from(entry.createdAt))
            .update()
    }

    override fun findById(id: UUID): CommissionEntry? =
        jdbcClient
            .sql("SELECT * FROM commission_entries WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findBySellerId(sellerId: UUID): List<CommissionEntry> =
        jdbcClient
            .sql("SELECT * FROM commission_entries WHERE seller_id = ? ORDER BY created_at DESC")
            .param(sellerId)
            .query(this::mapRow)
            .list()

    override fun findByReferralId(referralId: UUID): List<CommissionEntry> =
        jdbcClient
            .sql("SELECT * FROM commission_entries WHERE referral_id = ? ORDER BY created_at DESC")
            .param(referralId)
            .query(this::mapRow)
            .list()

    override fun findUnpaidBySellerId(sellerId: UUID): List<CommissionEntry> =
        jdbcClient
            .sql("""
                SELECT ce.* FROM commission_entries ce
                LEFT JOIN payout_entries pe ON ce.id = pe.entry_id
                WHERE ce.seller_id = ? AND pe.payout_id IS NULL
                ORDER BY ce.created_at ASC
            """)
            .param(sellerId)
            .query(this::mapRow)
            .list()

    override fun findBySellerIdAndPeriod(sellerId: UUID, from: LocalDate, to: LocalDate): List<CommissionEntry> =
        jdbcClient
            .sql("""
                SELECT * FROM commission_entries 
                WHERE seller_id = ? AND period_start >= ? AND period_end <= ?
                ORDER BY created_at ASC
            """)
            .param(sellerId)
            .param(Date.valueOf(from))
            .param(Date.valueOf(to))
            .query(this::mapRow)
            .list()

    override fun sumUnpaidBySellerId(sellerId: UUID): BigDecimal =
        jdbcClient
            .sql("""
                SELECT COALESCE(SUM(ce.commission_amount), 0) FROM commission_entries ce
                LEFT JOIN payout_entries pe ON ce.id = pe.entry_id
                WHERE ce.seller_id = ? AND pe.payout_id IS NULL
            """)
            .param(sellerId)
            .query(BigDecimal::class.java)
            .single() ?: BigDecimal.ZERO

    private fun mapRow(rs: ResultSet, rowNum: Int): CommissionEntry =
        CommissionEntry(
            id = rs.getObject("id", UUID::class.java),
            sellerId = rs.getObject("seller_id", UUID::class.java),
            referralId = rs.getObject("referral_id", UUID::class.java),
            sourceType = CommissionEntry.SourceType.valueOf(rs.getString("source_type")),
            sourceReference = rs.getString("source_reference"),
            grossAmount = rs.getBigDecimal("gross_amount"),
            commissionPercent = rs.getBigDecimal("commission_percent"),
            commissionAmount = rs.getBigDecimal("commission_amount"),
            periodStart = rs.getDate("period_start").toLocalDate(),
            periodEnd = rs.getDate("period_end").toLocalDate(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
}
