package com.example.simplequeue.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CommissionEntry(
    val id: UUID,
    val sellerId: UUID,
    val referralId: UUID,

    val sourceType: SourceType,
    val sourceReference: String?,

    val grossAmount: BigDecimal,
    val commissionPercent: BigDecimal,
    val commissionAmount: BigDecimal,

    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val createdAt: Instant,
) {
    enum class SourceType {
        SUBSCRIPTION_PAYMENT,
        UPGRADE,
        ONE_TIME_PAYMENT,
    }

    companion object {
        fun create(
            sellerId: UUID,
            referralId: UUID,
            sourceType: SourceType,
            sourceReference: String?,
            grossAmount: BigDecimal,
            commissionPercent: BigDecimal,
            periodStart: LocalDate,
            periodEnd: LocalDate,
            capPerCustomer: BigDecimal? = null,
            alreadyEarned: BigDecimal = BigDecimal.ZERO,
        ): CommissionEntry {
            var commission = grossAmount
                .multiply(commissionPercent)
                .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)

            // Apply cap if set
            if (capPerCustomer != null) {
                val remaining = capPerCustomer.subtract(alreadyEarned)
                if (remaining <= BigDecimal.ZERO) {
                    commission = BigDecimal.ZERO
                } else if (commission > remaining) {
                    commission = remaining
                }
            }

            return CommissionEntry(
                id = UUID.randomUUID(),
                sellerId = sellerId,
                referralId = referralId,
                sourceType = sourceType,
                sourceReference = sourceReference,
                grossAmount = grossAmount,
                commissionPercent = commissionPercent,
                commissionAmount = commission,
                periodStart = periodStart,
                periodEnd = periodEnd,
                createdAt = Instant.now(),
            )
        }
    }
}
