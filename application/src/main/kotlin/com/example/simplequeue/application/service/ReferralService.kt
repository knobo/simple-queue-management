package com.example.simplequeue.application.service

import com.example.simplequeue.domain.model.SellerReferral
import com.example.simplequeue.domain.port.SellerReferralRepository
import com.example.simplequeue.domain.port.SellerRepository

/**
 * Service for processing referral codes and linking users to sellers.
 * 
 * When a user signs up via a referral link:
 * 1. The referral code was captured in a cookie by ReferralCookieFilter
 * 2. On first login, this service is called to link the user to the seller
 * 3. A SellerReferral record is created if the referral is valid
 * 
 * This allows sellers to earn commission on referred customers.
 */
class ReferralService(
    private val sellerRepository: SellerRepository,
    private val sellerReferralRepository: SellerReferralRepository,
) {

    /**
     * Process a referral code for a user on login.
     * This is called from LoginSuccessHandler when a user logs in after
     * clicking a referral link (referral_code cookie was set).
     *
     * @param userId The user ID
     * @param referralCode The seller's referral code from cookie
     * @return true if referral was created, false otherwise (already has referral, invalid code, etc.)
     */
    fun processReferralForUser(userId: String, referralCode: String?): Boolean {
        if (referralCode.isNullOrBlank()) {
            return false
        }

        // Check if user already has a referral (avoid duplicates)
        val existingReferral = sellerReferralRepository.findByCustomerUserId(userId)
        if (existingReferral != null) {
            return false
        }

        // Find the seller by referral code
        val seller = sellerRepository.findByReferralCode(referralCode)
            ?: return false

        // Don't allow self-referrals
        if (seller.userId == userId) {
            return false
        }

        // Check if seller is active
        if (!seller.isActive()) {
            return false
        }

        // Create the referral
        val referral = SellerReferral.createForUser(
            sellerId = seller.id,
            customerUserId = userId,
            subscriptionId = null, // Will be set when user subscribes
            commissionPeriodMonths = seller.commissionPeriodMonths,
        )

        sellerReferralRepository.save(referral)
        return true
    }
}
