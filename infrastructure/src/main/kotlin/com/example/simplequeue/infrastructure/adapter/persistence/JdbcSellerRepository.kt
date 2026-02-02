package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.Seller
import com.example.simplequeue.domain.port.SellerRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class JdbcSellerRepository(
    private val jdbcClient: JdbcClient,
    private val objectMapper: ObjectMapper,
) : SellerRepository {

    override fun save(seller: Seller) {
        val sql = """
            INSERT INTO sellers (
                id, user_id, name, email, phone, referral_code,
                commission_percent, commission_cap_per_customer, commission_period_months,
                min_sales_to_maintain, status_period_months,
                payout_method, payout_details,
                stripe_account_id, stripe_charges_enabled, stripe_payouts_enabled, stripe_onboarding_completed,
                status, status_valid_until,
                created_at, created_by, updated_at
            )
            VALUES (
                :id, :user_id, :name, :email, :phone, :referral_code,
                :commission_percent, :commission_cap_per_customer, :commission_period_months,
                :min_sales_to_maintain, :status_period_months,
                :payout_method, :payout_details::jsonb,
                :stripe_account_id, :stripe_charges_enabled, :stripe_payouts_enabled, :stripe_onboarding_completed,
                :status, :status_valid_until,
                :created_at, :created_by, :updated_at
            )
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                email = EXCLUDED.email,
                phone = EXCLUDED.phone,
                commission_percent = EXCLUDED.commission_percent,
                commission_cap_per_customer = EXCLUDED.commission_cap_per_customer,
                commission_period_months = EXCLUDED.commission_period_months,
                min_sales_to_maintain = EXCLUDED.min_sales_to_maintain,
                status_period_months = EXCLUDED.status_period_months,
                payout_method = EXCLUDED.payout_method,
                payout_details = EXCLUDED.payout_details,
                stripe_account_id = EXCLUDED.stripe_account_id,
                stripe_charges_enabled = EXCLUDED.stripe_charges_enabled,
                stripe_payouts_enabled = EXCLUDED.stripe_payouts_enabled,
                stripe_onboarding_completed = EXCLUDED.stripe_onboarding_completed,
                status = EXCLUDED.status,
                status_valid_until = EXCLUDED.status_valid_until,
                updated_at = EXCLUDED.updated_at
        """
        jdbcClient
            .sql(sql)
            .param("id", seller.id)
            .param("user_id", seller.userId)
            .param("name", seller.name)
            .param("email", seller.email)
            .param("phone", seller.phone)
            .param("referral_code", seller.referralCode)
            .param("commission_percent", seller.commissionPercent)
            .param("commission_cap_per_customer", seller.commissionCapPerCustomer)
            .param("commission_period_months", seller.commissionPeriodMonths)
            .param("min_sales_to_maintain", seller.minSalesToMaintain)
            .param("status_period_months", seller.statusPeriodMonths)
            .param("payout_method", seller.payoutMethod.name)
            .param("payout_details", seller.payoutDetails?.let { objectMapper.writeValueAsString(it) })
            .param("stripe_account_id", seller.stripeAccountId)
            .param("stripe_charges_enabled", seller.stripeChargesEnabled)
            .param("stripe_payouts_enabled", seller.stripePayoutsEnabled)
            .param("stripe_onboarding_completed", seller.stripeOnboardingCompleted)
            .param("status", seller.status.name)
            .param("status_valid_until", seller.statusValidUntil?.let { Timestamp.from(it) })
            .param("created_at", Timestamp.from(seller.createdAt))
            .param("created_by", seller.createdBy)
            .param("updated_at", Timestamp.from(seller.updatedAt))
            .update()
    }

    override fun findById(id: UUID): Seller? =
        jdbcClient
            .sql("SELECT * FROM sellers WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByUserId(userId: String): Seller? =
        jdbcClient
            .sql("SELECT * FROM sellers WHERE user_id = ?")
            .param(userId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByReferralCode(referralCode: String): Seller? =
        jdbcClient
            .sql("SELECT * FROM sellers WHERE referral_code = ?")
            .param(referralCode)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByStripeAccountId(stripeAccountId: String): Seller? =
        jdbcClient
            .sql("SELECT * FROM sellers WHERE stripe_account_id = ?")
            .param(stripeAccountId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findAll(): List<Seller> =
        jdbcClient
            .sql("SELECT * FROM sellers ORDER BY created_at DESC")
            .query(this::mapRow)
            .list()

    override fun findByStatus(status: Seller.SellerStatus): List<Seller> =
        jdbcClient
            .sql("SELECT * FROM sellers WHERE status = ? ORDER BY created_at DESC")
            .param(status.name)
            .query(this::mapRow)
            .list()

    override fun countByStatusSince(status: Seller.SellerStatus, since: Instant): Int =
        jdbcClient
            .sql("SELECT COUNT(*) FROM sellers WHERE status = ? AND created_at >= ?")
            .param(status.name)
            .param(Timestamp.from(since))
            .query(Int::class.java)
            .single() ?: 0

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM sellers WHERE id = ?")
            .param(id)
            .update()
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): Seller {
        val payoutDetailsJson = rs.getString("payout_details")
        val payoutDetails: Map<String, Any>? = payoutDetailsJson?.let {
            objectMapper.readValue(it, object : TypeReference<Map<String, Any>>() {})
        }

        return Seller(
            id = rs.getObject("id", UUID::class.java),
            userId = rs.getString("user_id"),
            name = rs.getString("name"),
            email = rs.getString("email"),
            phone = rs.getString("phone"),
            referralCode = rs.getString("referral_code"),
            commissionPercent = rs.getBigDecimal("commission_percent"),
            commissionCapPerCustomer = rs.getBigDecimal("commission_cap_per_customer"),
            commissionPeriodMonths = rs.getInt("commission_period_months"),
            minSalesToMaintain = rs.getInt("min_sales_to_maintain"),
            statusPeriodMonths = rs.getInt("status_period_months"),
            payoutMethod = Seller.PayoutMethod.valueOf(rs.getString("payout_method")),
            payoutDetails = payoutDetails,
            stripeAccountId = rs.getString("stripe_account_id"),
            stripeChargesEnabled = rs.getBoolean("stripe_charges_enabled"),
            stripePayoutsEnabled = rs.getBoolean("stripe_payouts_enabled"),
            stripeOnboardingCompleted = rs.getBoolean("stripe_onboarding_completed"),
            status = Seller.SellerStatus.valueOf(rs.getString("status")),
            statusValidUntil = rs.getTimestamp("status_valid_until")?.toInstant(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            createdBy = rs.getString("created_by"),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
    }
}
