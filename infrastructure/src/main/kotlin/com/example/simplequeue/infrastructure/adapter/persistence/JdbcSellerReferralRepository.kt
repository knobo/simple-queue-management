package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.SellerReferral
import com.example.simplequeue.domain.port.SellerReferralRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class JdbcSellerReferralRepository(
    private val jdbcClient: JdbcClient,
) : SellerReferralRepository {

    override fun save(referral: SellerReferral) {
        val sql = """
            INSERT INTO seller_referrals (
                id, seller_id, customer_user_id, organization_id,
                subscription_id, referred_at, commission_ends_at,
                total_commission_earned, total_commission_paid
            )
            VALUES (
                :id, :seller_id, :customer_user_id, :organization_id,
                :subscription_id, :referred_at, :commission_ends_at,
                :total_commission_earned, :total_commission_paid
            )
            ON CONFLICT (id) DO UPDATE SET
                subscription_id = EXCLUDED.subscription_id,
                commission_ends_at = EXCLUDED.commission_ends_at,
                total_commission_earned = EXCLUDED.total_commission_earned,
                total_commission_paid = EXCLUDED.total_commission_paid
        """
        jdbcClient
            .sql(sql)
            .param("id", referral.id)
            .param("seller_id", referral.sellerId)
            .param("customer_user_id", referral.customerUserId)
            .param("organization_id", referral.organizationId)
            .param("subscription_id", referral.subscriptionId)
            .param("referred_at", Timestamp.from(referral.referredAt))
            .param("commission_ends_at", Timestamp.from(referral.commissionEndsAt))
            .param("total_commission_earned", referral.totalCommissionEarned)
            .param("total_commission_paid", referral.totalCommissionPaid)
            .update()
    }

    override fun findById(id: UUID): SellerReferral? =
        jdbcClient
            .sql("SELECT * FROM seller_referrals WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findBySellerId(sellerId: UUID): List<SellerReferral> =
        jdbcClient
            .sql("SELECT * FROM seller_referrals WHERE seller_id = ? ORDER BY referred_at DESC")
            .param(sellerId)
            .query(this::mapRow)
            .list()

    override fun findByCustomerUserId(userId: String): SellerReferral? =
        jdbcClient
            .sql("SELECT * FROM seller_referrals WHERE customer_user_id = ?")
            .param(userId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByOrganizationId(orgId: UUID): SellerReferral? =
        jdbcClient
            .sql("SELECT * FROM seller_referrals WHERE organization_id = ?")
            .param(orgId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findBySubscriptionId(subscriptionId: UUID): SellerReferral? =
        jdbcClient
            .sql("SELECT * FROM seller_referrals WHERE subscription_id = ?")
            .param(subscriptionId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findActiveBySellerIdSince(sellerId: UUID, since: Instant): List<SellerReferral> =
        jdbcClient
            .sql("""
                SELECT * FROM seller_referrals 
                WHERE seller_id = ? AND referred_at >= ? AND commission_ends_at > NOW()
                ORDER BY referred_at DESC
            """)
            .param(sellerId)
            .param(Timestamp.from(since))
            .query(this::mapRow)
            .list()

    override fun countBySellerId(sellerId: UUID): Int =
        jdbcClient
            .sql("SELECT COUNT(*) FROM seller_referrals WHERE seller_id = ?")
            .param(sellerId)
            .query(Int::class.java)
            .single() ?: 0

    override fun countBySellerIdSince(sellerId: UUID, since: Instant): Int =
        jdbcClient
            .sql("SELECT COUNT(*) FROM seller_referrals WHERE seller_id = ? AND referred_at >= ?")
            .param(sellerId)
            .param(Timestamp.from(since))
            .query(Int::class.java)
            .single() ?: 0

    private fun mapRow(rs: ResultSet, rowNum: Int): SellerReferral =
        SellerReferral(
            id = rs.getObject("id", UUID::class.java),
            sellerId = rs.getObject("seller_id", UUID::class.java),
            customerUserId = rs.getString("customer_user_id"),
            organizationId = rs.getObject("organization_id", UUID::class.java),
            subscriptionId = rs.getObject("subscription_id", UUID::class.java),
            referredAt = rs.getTimestamp("referred_at").toInstant(),
            commissionEndsAt = rs.getTimestamp("commission_ends_at").toInstant(),
            totalCommissionEarned = rs.getBigDecimal("total_commission_earned"),
            totalCommissionPaid = rs.getBigDecimal("total_commission_paid"),
        )
}
