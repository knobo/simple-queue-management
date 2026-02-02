package com.example.simplequeue.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class SellerReferral(
    val id: UUID,
    val sellerId: UUID,

    // Either user OR organization (not both)
    val customerUserId: String?,
    val organizationId: UUID?,

    val subscriptionId: UUID?,
    val referredAt: Instant,
    val commissionEndsAt: Instant,
    val totalCommissionEarned: BigDecimal,
    val totalCommissionPaid: BigDecimal,
) {
    init {
        require(
            (customerUserId != null && organizationId == null) ||
                (customerUserId == null && organizationId != null)
        ) { "Referral must have either customerUserId OR organizationId, not both" }
    }

    fun isForUser(): Boolean = customerUserId != null

    fun isForOrganization(): Boolean = organizationId != null

    fun isCommissionActive(): Boolean = Instant.now().isBefore(commissionEndsAt)

    fun unpaidCommission(): BigDecimal = totalCommissionEarned.subtract(totalCommissionPaid)

    fun addCommission(amount: BigDecimal): SellerReferral =
        copy(totalCommissionEarned = totalCommissionEarned.add(amount))

    fun recordPayment(amount: BigDecimal): SellerReferral =
        copy(totalCommissionPaid = totalCommissionPaid.add(amount))

    companion object {
        fun createForUser(
            sellerId: UUID,
            customerUserId: String,
            subscriptionId: UUID?,
            commissionPeriodMonths: Int,
        ): SellerReferral {
            val now = Instant.now()
            return SellerReferral(
                id = UUID.randomUUID(),
                sellerId = sellerId,
                customerUserId = customerUserId,
                organizationId = null,
                subscriptionId = subscriptionId,
                referredAt = now,
                commissionEndsAt = now.plusSeconds(commissionPeriodMonths.toLong() * 30 * 24 * 60 * 60),
                totalCommissionEarned = BigDecimal.ZERO,
                totalCommissionPaid = BigDecimal.ZERO,
            )
        }

        fun createForOrganization(
            sellerId: UUID,
            organizationId: UUID,
            subscriptionId: UUID?,
            commissionPeriodMonths: Int,
        ): SellerReferral {
            val now = Instant.now()
            return SellerReferral(
                id = UUID.randomUUID(),
                sellerId = sellerId,
                customerUserId = null,
                organizationId = organizationId,
                subscriptionId = subscriptionId,
                referredAt = now,
                commissionEndsAt = now.plusSeconds(commissionPeriodMonths.toLong() * 30 * 24 * 60 * 60),
                totalCommissionEarned = BigDecimal.ZERO,
                totalCommissionPaid = BigDecimal.ZERO,
            )
        }
    }
}
