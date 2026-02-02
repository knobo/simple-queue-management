package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.Seller
import com.example.simplequeue.domain.model.SellerPayout
import com.example.simplequeue.domain.port.CommissionEntryRepository
import com.example.simplequeue.domain.port.OrganizationRepository
import com.example.simplequeue.domain.port.SellerPayoutRepository
import com.example.simplequeue.domain.port.SellerReferralRepository
import com.example.simplequeue.domain.port.SellerRepository
import java.math.BigDecimal
import java.time.Instant

/**
 * Use case for getting the superadmin sales dashboard.
 */
class GetAdminSalesDashboardUseCase(
    private val sellerRepository: SellerRepository,
    private val sellerReferralRepository: SellerReferralRepository,
    private val organizationRepository: OrganizationRepository,
    private val commissionEntryRepository: CommissionEntryRepository,
    private val sellerPayoutRepository: SellerPayoutRepository,
) {
    data class AdminSalesDashboard(
        val totalSellers: Int,
        val activeSellers: Int,
        val totalOrganizations: Int,
        val activeOrganizations: Int,
        val totalReferrals: Int,
        val totalCommissionEarned: BigDecimal,
        val totalCommissionPaid: BigDecimal,
        val pendingPayouts: BigDecimal,
        val sellers: List<SellerSummary>,
        val pendingPayoutsList: List<SellerPayout>,
    )

    data class SellerSummary(
        val seller: Seller,
        val totalReferrals: Int,
        val activeReferrals: Int,
        val totalEarned: BigDecimal,
        val unpaidCommission: BigDecimal,
    )

    /**
     * Get the admin sales dashboard.
     */
    fun execute(): AdminSalesDashboard {
        val allSellers = sellerRepository.findAll()
        val allOrganizations = organizationRepository.findAll()
        val pendingPayouts = sellerPayoutRepository.findByStatus(SellerPayout.PayoutStatus.PENDING)

        val now = Instant.now()

        // Build seller summaries
        val sellerSummaries = allSellers.map { seller ->
            val referrals = sellerReferralRepository.findBySellerId(seller.id)
            val activeReferrals = referrals.count { it.isCommissionActive() }
            val totalEarned = referrals.sumOf { it.totalCommissionEarned }
            val unpaid = commissionEntryRepository.sumUnpaidBySellerId(seller.id)

            SellerSummary(
                seller = seller,
                totalReferrals = referrals.size,
                activeReferrals = activeReferrals,
                totalEarned = totalEarned,
                unpaidCommission = unpaid,
            )
        }

        // Calculate totals
        val totalReferrals = sellerSummaries.sumOf { it.totalReferrals }
        val totalEarned = sellerSummaries.sumOf { it.totalEarned }
        val totalUnpaid = sellerSummaries.sumOf { it.unpaidCommission }

        // Get total paid from completed payouts
        val allPayouts = allSellers.flatMap { sellerPayoutRepository.findBySellerId(it.id) }
        val totalPaid = allPayouts
            .filter { it.status == SellerPayout.PayoutStatus.COMPLETED }
            .sumOf { it.amount }

        return AdminSalesDashboard(
            totalSellers = allSellers.size,
            activeSellers = allSellers.count { it.isActive() },
            totalOrganizations = allOrganizations.size,
            activeOrganizations = allOrganizations.count { it.isActive() },
            totalReferrals = totalReferrals,
            totalCommissionEarned = totalEarned,
            totalCommissionPaid = totalPaid,
            pendingPayouts = totalUnpaid,
            sellers = sellerSummaries.sortedByDescending { it.totalEarned },
            pendingPayoutsList = pendingPayouts,
        )
    }
}
