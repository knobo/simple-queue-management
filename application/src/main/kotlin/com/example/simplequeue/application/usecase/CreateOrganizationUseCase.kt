package com.example.simplequeue.application.usecase

import com.example.simplequeue.domain.model.Organization
import com.example.simplequeue.domain.model.SellerActivityLog
import com.example.simplequeue.domain.model.SellerReferral
import com.example.simplequeue.domain.port.OrganizationRepository
import com.example.simplequeue.domain.port.SellerActivityLogRepository
import com.example.simplequeue.domain.port.SellerReferralRepository
import com.example.simplequeue.domain.port.SellerRepository

/**
 * Use case for creating a new organization (business account).
 * Can be created by a seller (who gets referral credit) or directly by an admin.
 */
class CreateOrganizationUseCase(
    private val organizationRepository: OrganizationRepository,
    private val sellerRepository: SellerRepository,
    private val sellerReferralRepository: SellerReferralRepository,
    private val sellerActivityLogRepository: SellerActivityLogRepository,
) {
    data class CreateOrganizationRequest(
        val name: String,
        val orgNumber: String? = null,
        val adminEmail: String,
    )

    /**
     * Create a new organization as a seller.
     *
     * @param request The organization details
     * @param sellerUserId The seller's user ID
     * @return The created organization
     */
    fun executeAsSeller(request: CreateOrganizationRequest, sellerUserId: String): Organization {
        // Find the seller
        val seller = sellerRepository.findByUserId(sellerUserId)
            ?: throw IllegalStateException("User is not registered as a seller")

        if (!seller.isActive()) {
            throw IllegalStateException("Seller account is not active")
        }

        // Check if organization with this admin email already exists
        val existingOrg = organizationRepository.findByAdminEmail(request.adminEmail)
        if (existingOrg != null) {
            throw IllegalStateException("An organization with this admin email already exists")
        }

        // Create the organization
        val organization = Organization.create(
            name = request.name,
            orgNumber = request.orgNumber,
            adminEmail = request.adminEmail,
            createdBySellerId = seller.id,
        )
        organizationRepository.save(organization)

        // Create the referral record
        val referral = SellerReferral.createForOrganization(
            sellerId = seller.id,
            organizationId = organization.id,
            subscriptionId = null, // Will be linked when org subscribes
            commissionPeriodMonths = seller.commissionPeriodMonths,
        )
        sellerReferralRepository.save(referral)

        // Log the activity
        val activityLog = SellerActivityLog.sale(
            sellerId = seller.id,
            referralId = referral.id,
            details = mapOf(
                "organizationName" to organization.name,
                "adminEmail" to organization.adminEmail,
            ),
        )
        sellerActivityLogRepository.save(activityLog)

        return organization
    }

    /**
     * Create a new organization directly (without seller).
     * Used when admin creates organization directly.
     *
     * @param request The organization details
     * @return The created organization
     */
    fun executeDirectly(request: CreateOrganizationRequest): Organization {
        // Check if organization with this admin email already exists
        val existingOrg = organizationRepository.findByAdminEmail(request.adminEmail)
        if (existingOrg != null) {
            throw IllegalStateException("An organization with this admin email already exists")
        }

        val organization = Organization.create(
            name = request.name,
            orgNumber = request.orgNumber,
            adminEmail = request.adminEmail,
            createdBySellerId = null, // No seller referral
        )
        organizationRepository.save(organization)

        return organization
    }
}
