package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.Seller
import com.example.simplequeue.domain.model.SellerReferral
import com.example.simplequeue.domain.port.CommissionEntryRepository
import com.example.simplequeue.domain.port.OrganizationRepository
import com.example.simplequeue.domain.port.SellerReferralRepository
import com.example.simplequeue.domain.port.SellerRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Use case for getting seller dashboard data.
 */
class GetSellerDashboardUseCase(
    private val sellerRepository: SellerRepository,
    private val sellerReferralRepository: SellerReferralRepository,
    private val organizationRepository: OrganizationRepository,
    private val commissionEntryRepository: CommissionEntryRepository,
) {
    data class SellerDashboard(
        val seller: Seller,
        val totalReferrals: Int,
        val activeReferrals: Int,
        val totalCommissionEarned: BigDecimal,
        val totalCommissionPaid: BigDecimal,
        val unpaidCommission: BigDecimal,
        val recentReferrals: List<ReferralSummary>,
        val salesThisPeriod: Int,
        val salesRequiredToMaintainStatus: Int,
        val daysUntilStatusExpires: Long?,
    )

    data class ReferralSummary(
        val referral: SellerReferral,
        val organizationName: String?,
        val isCommissionActive: Boolean,
    )

    /**
     * Get dashboard data for a seller.
     *
     * @param sellerUserId The seller's user ID
     * @return Dashboard data
     */
    fun execute(sellerUserId: String): SellerDashboard {
        val seller = sellerRepository.findByUserId(sellerUserId)
            ?: throw IllegalStateException("User is not registered as a seller")

        val allReferrals = sellerReferralRepository.findBySellerId(seller.id)
        val now = Instant.now()

        // Calculate status period
        val statusPeriodStart = now.minusSeconds(seller.statusPeriodMonths.toLong() * 30 * 24 * 60 * 60)
        val salesThisPeriod = sellerReferralRepository.countBySellerIdSince(seller.id, statusPeriodStart)

        // Get recent referrals with org names
        val recentReferrals = allReferrals.take(10).map { referral ->
            val orgName = referral.organizationId?.let { orgId ->
                organizationRepository.findById(orgId)?.name
            }
            ReferralSummary(
                referral = referral,
                organizationName = orgName,
                isCommissionActive = referral.isCommissionActive(),
            )
        }

        // Calculate totals
        val totalEarned = allReferrals.sumOf { it.totalCommissionEarned }
        val totalPaid = allReferrals.sumOf { it.totalCommissionPaid }
        val unpaid = commissionEntryRepository.sumUnpaidBySellerId(seller.id)

        // Days until status expires
        val daysUntilExpires = seller.statusValidUntil?.let { validUntil ->
            val secondsUntil = validUntil.epochSecond - now.epochSecond
            if (secondsUntil > 0) secondsUntil / (24 * 60 * 60) else 0
        }

        return SellerDashboard(
            seller = seller,
            totalReferrals = allReferrals.size,
            activeReferrals = allReferrals.count { it.isCommissionActive() },
            totalCommissionEarned = totalEarned,
            totalCommissionPaid = totalPaid,
            unpaidCommission = unpaid,
            recentReferrals = recentReferrals,
            salesThisPeriod = salesThisPeriod,
            salesRequiredToMaintainStatus = seller.minSalesToMaintain,
            daysUntilStatusExpires = daysUntilExpires,
        )
    }
}
