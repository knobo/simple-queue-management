package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.Seller
import com.example.simplequeue.domain.port.SellerRepository
import java.math.BigDecimal

/**
 * Use case for creating a new seller.
 * Only superadmin can create sellers.
 */
class CreateSellerUseCase(
    private val sellerRepository: SellerRepository,
) {
    data class CreateSellerRequest(
        val userId: String,
        val name: String,
        val email: String,
        val phone: String? = null,
        val commissionPercent: BigDecimal = BigDecimal("20.00"),
        val commissionCapPerCustomer: BigDecimal? = null,
        val commissionPeriodMonths: Int = 12,
        val minSalesToMaintain: Int = 5,
        val statusPeriodMonths: Int = 6,
        val payoutMethod: Seller.PayoutMethod = Seller.PayoutMethod.MANUAL,
        val payoutDetails: Map<String, Any>? = null,
    )

    /**
     * Create a new seller.
     *
     * @param request The seller details
     * @param createdBy The superadmin user ID creating this seller
     * @return The created seller
     * @throws IllegalStateException if user is already a seller
     */
    fun execute(request: CreateSellerRequest, createdBy: String): Seller {
        // Check if user is already a seller
        val existingSeller = sellerRepository.findByUserId(request.userId)
        if (existingSeller != null) {
            throw IllegalStateException("User is already registered as a seller")
        }

        // Validate commission percent
        require(request.commissionPercent >= BigDecimal("20.00")) {
            "Commission percent must be at least 20%"
        }
        require(request.commissionPercent <= BigDecimal("100.00")) {
            "Commission percent cannot exceed 100%"
        }

        // Create seller
        val seller = Seller.create(
            userId = request.userId,
            name = request.name,
            email = request.email,
            phone = request.phone,
            createdBy = createdBy,
            commissionPercent = request.commissionPercent,
            commissionCapPerCustomer = request.commissionCapPerCustomer,
            commissionPeriodMonths = request.commissionPeriodMonths,
            minSalesToMaintain = request.minSalesToMaintain,
            statusPeriodMonths = request.statusPeriodMonths,
            payoutMethod = request.payoutMethod,
            payoutDetails = request.payoutDetails,
        )

        sellerRepository.save(seller)
        return seller
    }
}
