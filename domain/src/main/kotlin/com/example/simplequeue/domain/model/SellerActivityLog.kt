package com.example.simplequeue.domain.model

import java.time.Instant
import java.util.UUID

data class SellerActivityLog(
    val id: UUID,
    val sellerId: UUID,
    val activityType: ActivityType,
    val referralId: UUID?,
    val createdAt: Instant,
    val details: Map<String, Any>?,
) {
    enum class ActivityType {
        SALE,
        STATUS_RENEWED,
        STATUS_EXPIRED,
        PAYOUT_CREATED,
        PAYOUT_COMPLETED,
    }

    companion object {
        fun sale(sellerId: UUID, referralId: UUID, details: Map<String, Any>? = null): SellerActivityLog =
            SellerActivityLog(
                id = UUID.randomUUID(),
                sellerId = sellerId,
                activityType = ActivityType.SALE,
                referralId = referralId,
                createdAt = Instant.now(),
                details = details,
            )

        fun statusRenewed(sellerId: UUID, details: Map<String, Any>? = null): SellerActivityLog =
            SellerActivityLog(
                id = UUID.randomUUID(),
                sellerId = sellerId,
                activityType = ActivityType.STATUS_RENEWED,
                referralId = null,
                createdAt = Instant.now(),
                details = details,
            )

        fun statusExpired(sellerId: UUID): SellerActivityLog =
            SellerActivityLog(
                id = UUID.randomUUID(),
                sellerId = sellerId,
                activityType = ActivityType.STATUS_EXPIRED,
                referralId = null,
                createdAt = Instant.now(),
                details = null,
            )
    }
}
