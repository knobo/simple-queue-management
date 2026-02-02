package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.usecase.CreateOrganizationUseCase
import com.example.simplequeue.application.usecase.GetSellerDashboardUseCase
import com.example.simplequeue.domain.model.Organization
import com.example.simplequeue.domain.model.Seller
import com.example.simplequeue.domain.model.SellerReferral
import com.example.simplequeue.domain.port.OrganizationRepository
import com.example.simplequeue.domain.port.SellerRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/seller")
@PreAuthorize("hasRole('SELLER') or hasRole('seller') or hasRole('SUPERADMIN') or hasRole('superadmin')")
class SellerController(
    private val sellerRepository: SellerRepository,
    private val organizationRepository: OrganizationRepository,
    private val getSellerDashboardUseCase: GetSellerDashboardUseCase,
    private val createOrganizationUseCase: CreateOrganizationUseCase,
) {
    /**
     * Get the current seller's profile.
     */
    @GetMapping("/me")
    fun getMyProfile(authentication: Authentication): ResponseEntity<SellerProfileDTO> {
        val userId = getUserId(authentication.principal)
        val seller = sellerRepository.findByUserId(userId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(SellerProfileDTO.from(seller))
    }

    /**
     * Get the current seller's dashboard.
     */
    @GetMapping("/dashboard")
    fun getMyDashboard(authentication: Authentication): ResponseEntity<SellerDashboardDTO> {
        val userId = getUserId(authentication.principal)

        return try {
            val dashboard = getSellerDashboardUseCase.execute(userId)
            ResponseEntity.ok(SellerDashboardDTO.from(dashboard))
        } catch (e: IllegalStateException) {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Get organizations created by this seller.
     */
    @GetMapping("/organizations")
    fun getMyOrganizations(authentication: Authentication): ResponseEntity<List<OrganizationDTO>> {
        val userId = getUserId(authentication.principal)
        val seller = sellerRepository.findByUserId(userId)
            ?: return ResponseEntity.notFound().build()

        val organizations = organizationRepository.findBySellerId(seller.id)
        return ResponseEntity.ok(organizations.map { OrganizationDTO.from(it) })
    }

    /**
     * Create a new organization (business account).
     */
    @PostMapping("/organizations")
    fun createOrganization(
        authentication: Authentication,
        @RequestBody request: CreateOrganizationRequest,
    ): ResponseEntity<OrganizationDTO> {
        val userId = getUserId(authentication.principal)

        return try {
            val org = createOrganizationUseCase.executeAsSeller(
                CreateOrganizationUseCase.CreateOrganizationRequest(
                    name = request.name,
                    orgNumber = request.orgNumber,
                    adminEmail = request.adminEmail,
                ),
                sellerUserId = userId,
            )
            ResponseEntity.status(HttpStatus.CREATED).body(OrganizationDTO.from(org))
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().build()
        }
    }

    /**
     * Get the seller's referral link.
     */
    @GetMapping("/referral-link")
    fun getReferralLink(authentication: Authentication): ResponseEntity<ReferralLinkDTO> {
        val userId = getUserId(authentication.principal)
        val seller = sellerRepository.findByUserId(userId)
            ?: return ResponseEntity.notFound().build()

        // TODO: Make base URL configurable
        val baseUrl = "https://simplequeue.no"
        val referralLink = "$baseUrl/signup?ref=${seller.referralCode}"

        return ResponseEntity.ok(
            ReferralLinkDTO(
                referralCode = seller.referralCode,
                referralLink = referralLink,
            )
        )
    }

    private fun getUserId(principal: Any?): String =
        when (principal) {
            is Jwt -> principal.subject
            is OAuth2User -> principal.name
            else -> throw IllegalStateException("Unknown principal type: ${principal?.javaClass}")
        }

    // DTOs

    data class CreateOrganizationRequest(
        val name: String,
        val orgNumber: String? = null,
        val adminEmail: String,
    )

    data class ReferralLinkDTO(
        val referralCode: String,
        val referralLink: String,
    )

    data class SellerProfileDTO(
        val id: UUID,
        val name: String,
        val email: String,
        val referralCode: String,
        val commissionPercent: BigDecimal,
        val commissionPeriodMonths: Int,
        val status: String,
        val statusValidUntil: Instant?,
    ) {
        companion object {
            fun from(seller: Seller): SellerProfileDTO = SellerProfileDTO(
                id = seller.id,
                name = seller.name,
                email = seller.email,
                referralCode = seller.referralCode,
                commissionPercent = seller.commissionPercent,
                commissionPeriodMonths = seller.commissionPeriodMonths,
                status = seller.status.name,
                statusValidUntil = seller.statusValidUntil,
            )
        }
    }

    data class SellerDashboardDTO(
        val seller: SellerProfileDTO,
        val totalReferrals: Int,
        val activeReferrals: Int,
        val totalCommissionEarned: BigDecimal,
        val totalCommissionPaid: BigDecimal,
        val unpaidCommission: BigDecimal,
        val recentReferrals: List<ReferralSummaryDTO>,
        val salesThisPeriod: Int,
        val salesRequiredToMaintainStatus: Int,
        val daysUntilStatusExpires: Long?,
    ) {
        companion object {
            fun from(dashboard: GetSellerDashboardUseCase.SellerDashboard): SellerDashboardDTO =
                SellerDashboardDTO(
                    seller = SellerProfileDTO.from(dashboard.seller),
                    totalReferrals = dashboard.totalReferrals,
                    activeReferrals = dashboard.activeReferrals,
                    totalCommissionEarned = dashboard.totalCommissionEarned,
                    totalCommissionPaid = dashboard.totalCommissionPaid,
                    unpaidCommission = dashboard.unpaidCommission,
                    recentReferrals = dashboard.recentReferrals.map { ReferralSummaryDTO.from(it) },
                    salesThisPeriod = dashboard.salesThisPeriod,
                    salesRequiredToMaintainStatus = dashboard.salesRequiredToMaintainStatus,
                    daysUntilStatusExpires = dashboard.daysUntilStatusExpires,
                )
        }
    }

    data class ReferralSummaryDTO(
        val id: UUID,
        val organizationName: String?,
        val referredAt: Instant,
        val commissionEndsAt: Instant,
        val isCommissionActive: Boolean,
        val totalCommissionEarned: BigDecimal,
    ) {
        companion object {
            fun from(summary: GetSellerDashboardUseCase.ReferralSummary): ReferralSummaryDTO =
                ReferralSummaryDTO(
                    id = summary.referral.id,
                    organizationName = summary.organizationName,
                    referredAt = summary.referral.referredAt,
                    commissionEndsAt = summary.referral.commissionEndsAt,
                    isCommissionActive = summary.isCommissionActive,
                    totalCommissionEarned = summary.referral.totalCommissionEarned,
                )
        }
    }

    data class OrganizationDTO(
        val id: UUID,
        val name: String,
        val orgNumber: String?,
        val adminEmail: String,
        val status: String,
        val createdAt: Instant,
        val activatedAt: Instant?,
    ) {
        companion object {
            fun from(org: Organization): OrganizationDTO = OrganizationDTO(
                id = org.id,
                name = org.name,
                orgNumber = org.orgNumber,
                adminEmail = org.adminEmail,
                status = org.status.name,
                createdAt = org.createdAt,
                activatedAt = org.activatedAt,
            )
        }
    }
}
