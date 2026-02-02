package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.Subscription
import com.example.simplequeue.domain.model.SubscriptionTier
import com.example.simplequeue.domain.port.SubscriptionRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcSubscriptionRepository(
    private val jdbcClient: JdbcClient,
) : SubscriptionRepository {

    override fun save(subscription: Subscription) {
        val sql = """
            INSERT INTO subscriptions (
                id, user_id, tier, status, stripe_customer_id, stripe_subscription_id,
                current_period_start, current_period_end, cancel_at_period_end,
                created_at, updated_at
            )
            VALUES (
                :id, :user_id, :tier, :status, :stripe_customer_id, :stripe_subscription_id,
                :current_period_start, :current_period_end, :cancel_at_period_end,
                :created_at, :updated_at
            )
            ON CONFLICT (id) DO UPDATE SET
                tier = EXCLUDED.tier,
                status = EXCLUDED.status,
                stripe_customer_id = EXCLUDED.stripe_customer_id,
                stripe_subscription_id = EXCLUDED.stripe_subscription_id,
                current_period_start = EXCLUDED.current_period_start,
                current_period_end = EXCLUDED.current_period_end,
                cancel_at_period_end = EXCLUDED.cancel_at_period_end,
                updated_at = EXCLUDED.updated_at
        """
        jdbcClient
            .sql(sql)
            .param("id", subscription.id)
            .param("user_id", subscription.userId)
            .param("tier", subscription.tier.name)
            .param("status", subscription.status.name)
            .param("stripe_customer_id", subscription.stripeCustomerId)
            .param("stripe_subscription_id", subscription.stripeSubscriptionId)
            .param("current_period_start", Timestamp.from(subscription.currentPeriodStart))
            .param("current_period_end", Timestamp.from(subscription.currentPeriodEnd))
            .param("cancel_at_period_end", subscription.cancelAtPeriodEnd)
            .param("created_at", Timestamp.from(subscription.createdAt))
            .param("updated_at", Timestamp.from(subscription.updatedAt))
            .update()
    }

    override fun findById(id: UUID): Subscription? =
        jdbcClient
            .sql("SELECT * FROM subscriptions WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByUserId(userId: String): Subscription? =
        jdbcClient
            .sql("SELECT * FROM subscriptions WHERE user_id = ?")
            .param(userId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByStripeCustomerId(customerId: String): Subscription? =
        jdbcClient
            .sql("SELECT * FROM subscriptions WHERE stripe_customer_id = ?")
            .param(customerId)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    private fun mapRow(rs: ResultSet, rowNum: Int): Subscription =
        Subscription(
            id = rs.getObject("id", UUID::class.java),
            userId = rs.getString("user_id"),
            tier = SubscriptionTier.valueOf(rs.getString("tier")),
            status = Subscription.SubscriptionStatus.valueOf(rs.getString("status")),
            stripeCustomerId = rs.getString("stripe_customer_id"),
            stripeSubscriptionId = rs.getString("stripe_subscription_id"),
            currentPeriodStart = rs.getTimestamp("current_period_start").toInstant(),
            currentPeriodEnd = rs.getTimestamp("current_period_end").toInstant(),
            cancelAtPeriodEnd = rs.getBoolean("cancel_at_period_end"),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
}
