package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.Seller
import com.example.simplequeue.domain.model.SellerPayout
import com.example.simplequeue.domain.port.SellerPayoutRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcSellerPayoutRepository(
    private val jdbcClient: JdbcClient,
) : SellerPayoutRepository {

    override fun save(payout: SellerPayout) {
        val sql = """
            INSERT INTO seller_payouts (
                id, seller_id, amount, payout_method, payout_reference,
                status, entries_from, entries_to,
                created_at, processed_at, completed_at, notes
            )
            VALUES (
                :id, :seller_id, :amount, :payout_method, :payout_reference,
                :status, :entries_from, :entries_to,
                :created_at, :processed_at, :completed_at, :notes
            )
            ON CONFLICT (id) DO UPDATE SET
                payout_reference = EXCLUDED.payout_reference,
                status = EXCLUDED.status,
                processed_at = EXCLUDED.processed_at,
                completed_at = EXCLUDED.completed_at,
                notes = EXCLUDED.notes
        """
        jdbcClient
            .sql(sql)
            .param("id", payout.id)
            .param("seller_id", payout.sellerId)
            .param("amount", payout.amount)
            .param("payout_method", payout.payoutMethod.name)
            .param("payout_reference", payout.payoutReference)
            .param("status", payout.status.name)
            .param("entries_from", Date.valueOf(payout.entriesFrom))
            .param("entries_to", Date.valueOf(payout.entriesTo))
            .param("created_at", Timestamp.from(payout.createdAt))
            .param("processed_at", payout.processedAt?.let { Timestamp.from(it) })
            .param("completed_at", payout.completedAt?.let { Timestamp.from(it) })
            .param("notes", payout.notes)
            .update()
    }

    override fun findById(id: UUID): SellerPayout? =
        jdbcClient
            .sql("SELECT * FROM seller_payouts WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findBySellerId(sellerId: UUID): List<SellerPayout> =
        jdbcClient
            .sql("SELECT * FROM seller_payouts WHERE seller_id = ? ORDER BY created_at DESC")
            .param(sellerId)
            .query(this::mapRow)
            .list()

    override fun findByStatus(status: SellerPayout.PayoutStatus): List<SellerPayout> =
        jdbcClient
            .sql("SELECT * FROM seller_payouts WHERE status = ? ORDER BY created_at ASC")
            .param(status.name)
            .query(this::mapRow)
            .list()

    override fun findPendingBySellerId(sellerId: UUID): List<SellerPayout> =
        jdbcClient
            .sql("SELECT * FROM seller_payouts WHERE seller_id = ? AND status = 'PENDING' ORDER BY created_at ASC")
            .param(sellerId)
            .query(this::mapRow)
            .list()

    private fun mapRow(rs: ResultSet, rowNum: Int): SellerPayout =
        SellerPayout(
            id = rs.getObject("id", UUID::class.java),
            sellerId = rs.getObject("seller_id", UUID::class.java),
            amount = rs.getBigDecimal("amount"),
            payoutMethod = Seller.PayoutMethod.valueOf(rs.getString("payout_method")),
            payoutReference = rs.getString("payout_reference"),
            status = SellerPayout.PayoutStatus.valueOf(rs.getString("status")),
            entriesFrom = rs.getDate("entries_from").toLocalDate(),
            entriesTo = rs.getDate("entries_to").toLocalDate(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            processedAt = rs.getTimestamp("processed_at")?.toInstant(),
            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
            notes = rs.getString("notes"),
        )
}
