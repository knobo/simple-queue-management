package com.example.simplequeue.application.service

import com.example.simplequeue.domain.model.CommissionEntry
import com.example.simplequeue.domain.model.SellerReferral
import com.example.simplequeue.domain.port.CommissionEntryRepository
import com.example.simplequeue.domain.port.SellerReferralRepository
import com.example.simplequeue.domain.port.SellerRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Service for processing commission calculations.
 */
class CommissionService(
    private val sellerRepository: SellerRepository,
    private val sellerReferralRepository: SellerReferralRepository,
    private val commissionEntryRepository: CommissionEntryRepository,
) {
    /**
     * Process a payment and create commission entries for applicable referrals.
     *
     * @param subscriptionId The subscription that was paid
     * @param grossAmount The payment amount
     * @param sourceType The type of payment
     * @param sourceReference External reference (e.g., Stripe invoice ID)
     * @param periodStart Start of the billing period
     * @param periodEnd End of the billing period
     * @return The created commission entry, if any
     */
    fun processPayment(
        subscriptionId: UUID,
        grossAmount: BigDecimal,
        sourceType: CommissionEntry.SourceType,
        sourceReference: String?,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): CommissionEntry? {
        // Find the referral for this subscription
        val referral = sellerReferralRepository.findBySubscriptionId(subscriptionId)
            ?: return null // No referral for this subscription

        // Check if commission period is still active
        if (!referral.isCommissionActive()) {
            return null // Commission period has ended
        }

        // Get the seller to check their commission settings
        val seller = sellerRepository.findById(referral.sellerId)
            ?: return null // Seller not found (shouldn't happen)

        if (!seller.isActive()) {
            return null // Seller is not active
        }

        // Create commission entry with cap consideration
        val entry = CommissionEntry.create(
            sellerId = seller.id,
            referralId = referral.id,
            sourceType = sourceType,
            sourceReference = sourceReference,
            grossAmount = grossAmount,
            commissionPercent = seller.commissionPercent,
            periodStart = periodStart,
            periodEnd = periodEnd,
            capPerCustomer = seller.commissionCapPerCustomer,
            alreadyEarned = referral.totalCommissionEarned,
        )

        // Only save if there's actual commission
        if (entry.commissionAmount > BigDecimal.ZERO) {
            commissionEntryRepository.save(entry)

            // Update the referral's total earned
            val updatedReferral = referral.addCommission(entry.commissionAmount)
            sellerReferralRepository.save(updatedReferral)

            return entry
        }

        return null
    }

    /**
     * Calculate total unpaid commission for a seller.
     */
    fun getUnpaidCommission(sellerId: UUID): BigDecimal =
        commissionEntryRepository.sumUnpaidBySellerId(sellerId)

    /**
     * Get all unpaid commission entries for a seller.
     */
    fun getUnpaidEntries(sellerId: UUID): List<CommissionEntry> =
        commissionEntryRepository.findUnpaidBySellerId(sellerId)
}
