package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.SubscriptionTier
import com.example.simplequeue.domain.model.TierLimit
import com.example.simplequeue.domain.port.TierLimitRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.concurrent.ConcurrentHashMap

@Repository
class JdbcTierLimitRepository(
    private val jdbc: JdbcTemplate,
) : TierLimitRepository {

    // Simple in-memory cache to avoid repeated DB queries
    private val cache = ConcurrentHashMap<SubscriptionTier, TierLimit>()
    @Volatile
    private var cacheLoaded = false

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        TierLimit(
            tier = SubscriptionTier.valueOf(rs.getString("tier")),
            maxQueues = rs.getInt("max_queues"),
            maxOperatorsPerQueue = rs.getInt("max_operators_per_queue"),
            maxTicketsPerDay = rs.getInt("max_tickets_per_day"),
            maxActiveTickets = rs.getInt("max_active_tickets"),
            maxInvitesPerMonth = rs.getInt("max_invites_per_month"),
            maxCountersPerQueue = rs.getInt("max_counters_per_queue"),
            canUseEmailNotifications = rs.getBoolean("can_use_email_notifications"),
            canUseCustomBranding = rs.getBoolean("can_use_custom_branding"),
            canUseAnalytics = rs.getBoolean("can_use_analytics"),
            canUseApiAccess = rs.getBoolean("can_use_api_access"),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
            updatedBy = rs.getString("updated_by"),
        )
    }

    override fun findByTier(tier: SubscriptionTier): TierLimit? {
        ensureCacheLoaded()
        return cache[tier] ?: loadFromDb(tier)
    }

    override fun findAll(): List<TierLimit> {
        ensureCacheLoaded()
        return cache.values.toList().sortedBy { it.tier.ordinal }
    }

    override fun save(limit: TierLimit) {
        jdbc.update(
            """
            INSERT INTO tier_limits (
                tier, max_queues, max_operators_per_queue, max_tickets_per_day,
                max_active_tickets, max_invites_per_month, max_counters_per_queue,
                can_use_email_notifications, can_use_custom_branding, can_use_analytics,
                can_use_api_access, updated_at, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (tier) DO UPDATE SET
                max_queues = EXCLUDED.max_queues,
                max_operators_per_queue = EXCLUDED.max_operators_per_queue,
                max_tickets_per_day = EXCLUDED.max_tickets_per_day,
                max_active_tickets = EXCLUDED.max_active_tickets,
                max_invites_per_month = EXCLUDED.max_invites_per_month,
                max_counters_per_queue = EXCLUDED.max_counters_per_queue,
                can_use_email_notifications = EXCLUDED.can_use_email_notifications,
                can_use_custom_branding = EXCLUDED.can_use_custom_branding,
                can_use_analytics = EXCLUDED.can_use_analytics,
                can_use_api_access = EXCLUDED.can_use_api_access,
                updated_at = EXCLUDED.updated_at,
                updated_by = EXCLUDED.updated_by
            """.trimIndent(),
            limit.tier.name,
            limit.maxQueues,
            limit.maxOperatorsPerQueue,
            limit.maxTicketsPerDay,
            limit.maxActiveTickets,
            limit.maxInvitesPerMonth,
            limit.maxCountersPerQueue,
            limit.canUseEmailNotifications,
            limit.canUseCustomBranding,
            limit.canUseAnalytics,
            limit.canUseApiAccess,
            Timestamp.from(limit.updatedAt),
            limit.updatedBy,
        )

        // Update cache
        cache[limit.tier] = limit
    }

    private fun ensureCacheLoaded() {
        if (!cacheLoaded) {
            synchronized(this) {
                if (!cacheLoaded) {
                    loadAllFromDb()
                    cacheLoaded = true
                }
            }
        }
    }

    private fun loadFromDb(tier: SubscriptionTier): TierLimit? {
        val result = jdbc.query(
            "SELECT * FROM tier_limits WHERE tier = ?",
            rowMapper,
            tier.name,
        ).firstOrNull()

        if (result != null) {
            cache[tier] = result
        }
        return result
    }

    private fun loadAllFromDb() {
        val results = jdbc.query("SELECT * FROM tier_limits", rowMapper)
        results.forEach { cache[it.tier] = it }
    }

    /**
     * Force reload cache from database.
     * Useful for testing or after manual DB changes.
     */
    fun refreshCache() {
        cache.clear()
        cacheLoaded = false
        ensureCacheLoaded()
    }
}
