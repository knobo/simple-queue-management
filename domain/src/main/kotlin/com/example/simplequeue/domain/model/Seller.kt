package com.example.simplequeue.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Seller(
    val id: UUID,
    val userId: String,
    val name: String,
    val email: String,
    val phone: String?,
    val referralCode: String,

    // Commission terms
    val commissionPercent: BigDecimal,
    val commissionCapPerCustomer: BigDecimal?,
    val commissionPeriodMonths: Int,

    // Status maintenance requirements
    val minSalesToMaintain: Int,
    val statusPeriodMonths: Int,

    // Payout
    val payoutMethod: PayoutMethod,
    val payoutDetails: Map<String, Any>?,

    // Stripe Connect fields
    val stripeAccountId: String?,
    val stripeChargesEnabled: Boolean,
    val stripePayoutsEnabled: Boolean,
    val stripeOnboardingCompleted: Boolean,

    val status: SellerStatus,
    val statusValidUntil: Instant?,

    val createdAt: Instant,
    val createdBy: String,
    val updatedAt: Instant,
) {
    enum class SellerStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
    }

    enum class PayoutMethod {
        MANUAL,
        STRIPE_CONNECT,
        VIPPS,
        BANK_TRANSFER,
    }

    fun isActive(): Boolean = status == SellerStatus.ACTIVE

    fun isStatusValid(): Boolean =
        status == SellerStatus.ACTIVE &&
            (statusValidUntil == null || statusValidUntil.isAfter(Instant.now()))

    fun canReceivePayouts(): Boolean =
        payoutMethod == PayoutMethod.STRIPE_CONNECT &&
            stripeAccountId != null &&
            stripeChargesEnabled &&
            stripePayoutsEnabled

    fun withStripeAccount(stripeAccountId: String): Seller =
        copy(
            stripeAccountId = stripeAccountId,
            payoutMethod = PayoutMethod.STRIPE_CONNECT,
            updatedAt = Instant.now(),
        )

    fun withStripeStatus(
        chargesEnabled: Boolean,
        payoutsEnabled: Boolean,
        onboardingCompleted: Boolean = this.stripeOnboardingCompleted,
    ): Seller =
        copy(
            stripeChargesEnabled = chargesEnabled,
            stripePayoutsEnabled = payoutsEnabled,
            stripeOnboardingCompleted = onboardingCompleted,
            updatedAt = Instant.now(),
        )

    companion object {
        fun generateReferralCode(): String {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            return (1..8).map { chars.random() }.joinToString("")
        }

        fun create(
            userId: String,
            name: String,
            email: String,
            phone: String?,
            createdBy: String,
            commissionPercent: BigDecimal = BigDecimal("20.00"),
            commissionCapPerCustomer: BigDecimal? = null,
            commissionPeriodMonths: Int = 12,
            minSalesToMaintain: Int = 5,
            statusPeriodMonths: Int = 6,
            payoutMethod: PayoutMethod = PayoutMethod.MANUAL,
            payoutDetails: Map<String, Any>? = null,
        ): Seller {
            val now = Instant.now()
            return Seller(
                id = UUID.randomUUID(),
                userId = userId,
                name = name,
                email = email,
                phone = phone,
                referralCode = generateReferralCode(),
                commissionPercent = commissionPercent,
                commissionCapPerCustomer = commissionCapPerCustomer,
                commissionPeriodMonths = commissionPeriodMonths,
                minSalesToMaintain = minSalesToMaintain,
                statusPeriodMonths = statusPeriodMonths,
                payoutMethod = payoutMethod,
                payoutDetails = payoutDetails,
                stripeAccountId = null,
                stripeChargesEnabled = false,
                stripePayoutsEnabled = false,
                stripeOnboardingCompleted = false,
                status = SellerStatus.ACTIVE,
                statusValidUntil = now.plusSeconds(statusPeriodMonths.toLong() * 30 * 24 * 60 * 60),
                createdAt = now,
                createdBy = createdBy,
                updatedAt = now,
            )
        }
    }
}
