package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.usecase.CreateSellerUseCase
import com.example.simplequeue.application.usecase.GetAdminSalesDashboardUseCase
import com.example.simplequeue.domain.model.Seller
import com.example.simplequeue.domain.model.SellerReferral
import com.example.simplequeue.domain.port.SellerReferralRepository
import com.example.simplequeue.domain.port.SellerRepository
import com.example.simplequeue.infrastructure.service.KeycloakAdminService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
@RequestMapping("/api/admin/sales")
@PreAuthorize("hasRole('SUPERADMIN') or hasRole('superadmin')")
class SalesAdminController(
    private val createSellerUseCase: CreateSellerUseCase,
    private val getAdminSalesDashboardUseCase: GetAdminSalesDashboardUseCase,
    private val sellerRepository: SellerRepository,
    private val sellerReferralRepository: SellerReferralRepository,
    private val keycloakAdminService: KeycloakAdminService,
) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(SalesAdminController::class.java)
    }
    /**
     * Get the admin sales dashboard overview.
     */
    @GetMapping("/dashboard")
    fun getDashboard(): AdminDashboardDTO {
        val dashboard = getAdminSalesDashboardUseCase.execute()
        return AdminDashboardDTO(
            totalSellers = dashboard.totalSellers,
            activeSellers = dashboard.activeSellers,
            totalOrganizations = dashboard.totalOrganizations,
            activeOrganizations = dashboard.activeOrganizations,
            totalReferrals = dashboard.totalReferrals,
            totalCommissionEarned = dashboard.totalCommissionEarned,
            totalCommissionPaid = dashboard.totalCommissionPaid,
            pendingPayouts = dashboard.pendingPayouts,
            sellers = dashboard.sellers.map { SellerSummaryDTO.from(it) },
        )
    }

    /**
     * List all sellers.
     */
    @GetMapping("/sellers")
    fun listSellers(): List<SellerDTO> {
        val sellers = sellerRepository.findAll()
        return sellers.map { SellerDTO.from(it) }
    }

    /**
     * Get a specific seller by ID.
     */
    @GetMapping("/sellers/{id}")
    fun getSeller(@PathVariable id: UUID): ResponseEntity<SellerDTO> {
        val seller = sellerRepository.findById(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(SellerDTO.from(seller))
    }

    /**
     * Create a new seller.
     * Also assigns the SELLER role in Keycloak for dashboard access.
     */
    @PostMapping("/sellers")
    fun createSeller(
        authentication: Authentication,
        @RequestBody request: CreateSellerRequest,
    ): ResponseEntity<SellerDTO> {
        val createdBy = getUserId(authentication.principal)

        return try {
            val seller = createSellerUseCase.execute(
                CreateSellerUseCase.CreateSellerRequest(
                    userId = request.userId,
                    name = request.name,
                    email = request.email,
                    phone = request.phone,
                    commissionPercent = request.commissionPercent ?: BigDecimal("20.00"),
                    commissionCapPerCustomer = request.commissionCapPerCustomer,
                    commissionPeriodMonths = request.commissionPeriodMonths ?: 12,
                    minSalesToMaintain = request.minSalesToMaintain ?: 5,
                    statusPeriodMonths = request.statusPeriodMonths ?: 6,
                    payoutMethod = request.payoutMethod?.let { Seller.PayoutMethod.valueOf(it) }
                        ?: Seller.PayoutMethod.MANUAL,
                    payoutDetails = request.payoutDetails,
                ),
                createdBy = createdBy,
            )

            // Assign SELLER role in Keycloak (graceful failure - log but don't fail the operation)
            try {
                val roleAssigned = keycloakAdminService.assignRoleToUser(
                    userId = request.userId,
                    roleName = KeycloakAdminService.SELLER_ROLE,
                )
                if (roleAssigned) {
                    logger.info("Successfully assigned SELLER role to user ${request.userId}")
                } else {
                    logger.warn("Failed to assign SELLER role to user ${request.userId} - user may need manual role assignment")
                }
            } catch (e: Exception) {
                logger.error("Error assigning SELLER role to user ${request.userId}: ${e.message}", e)
                // Don't fail the seller creation - the role can be assigned manually
            }

            ResponseEntity.status(HttpStatus.CREATED).body(SellerDTO.from(seller))
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    /**
     * Update a seller.
     */
    @PutMapping("/sellers/{id}")
    fun updateSeller(
        @PathVariable id: UUID,
        @RequestBody request: UpdateSellerRequest,
    ): ResponseEntity<SellerDTO> {
        val existing = sellerRepository.findById(id)
            ?: return ResponseEntity.notFound().build()

        val updated = existing.copy(
            name = request.name ?: existing.name,
            email = request.email ?: existing.email,
            phone = request.phone ?: existing.phone,
            commissionPercent = request.commissionPercent ?: existing.commissionPercent,
            commissionCapPerCustomer = request.commissionCapPerCustomer ?: existing.commissionCapPerCustomer,
            commissionPeriodMonths = request.commissionPeriodMonths ?: existing.commissionPeriodMonths,
            minSalesToMaintain = request.minSalesToMaintain ?: existing.minSalesToMaintain,
            statusPeriodMonths = request.statusPeriodMonths ?: existing.statusPeriodMonths,
            payoutMethod = request.payoutMethod?.let { Seller.PayoutMethod.valueOf(it) } ?: existing.payoutMethod,
            payoutDetails = request.payoutDetails ?: existing.payoutDetails,
            status = request.status?.let { Seller.SellerStatus.valueOf(it) } ?: existing.status,
            updatedAt = Instant.now(),
        )

        sellerRepository.save(updated)
        return ResponseEntity.ok(SellerDTO.from(updated))
    }

    /**
     * Deactivate a seller.
     * Also removes the SELLER role from Keycloak.
     */
    @DeleteMapping("/sellers/{id}")
    fun deactivateSeller(@PathVariable id: UUID): ResponseEntity<Void> {
        val existing = sellerRepository.findById(id)
            ?: return ResponseEntity.notFound().build()

        val deactivated = existing.copy(
            status = Seller.SellerStatus.SUSPENDED,
            updatedAt = Instant.now(),
        )
        sellerRepository.save(deactivated)

        // Remove SELLER role from Keycloak (graceful failure)
        try {
            val roleRemoved = keycloakAdminService.removeRoleFromUser(
                userId = existing.userId,
                roleName = KeycloakAdminService.SELLER_ROLE,
            )
            if (roleRemoved) {
                logger.info("Successfully removed SELLER role from user ${existing.userId}")
            } else {
                logger.warn("Failed to remove SELLER role from user ${existing.userId}")
            }
        } catch (e: Exception) {
            logger.error("Error removing SELLER role from user ${existing.userId}: ${e.message}", e)
        }

        return ResponseEntity.noContent().build()
    }

    // ==========================================================================
    // Manual Referral Linking
    // ==========================================================================

    /**
     * Search for users (customers) by email or name.
     * Uses Keycloak Admin API to find users.
     */
    @GetMapping("/users/search")
    fun searchUsers(@RequestParam query: String): List<UserSearchResultDTO> {
        if (query.length < 2) {
            return emptyList()
        }

        val users = keycloakAdminService.searchUsers(query, maxResults = 20)
        return users.map { user ->
            // Check if user already has a referral
            val existingReferral = sellerReferralRepository.findByCustomerUserId(user.id)
            val existingSeller = if (existingReferral != null) {
                sellerRepository.findById(existingReferral.sellerId)
            } else null

            UserSearchResultDTO(
                id = user.id,
                email = user.email,
                name = user.displayName(),
                hasReferral = existingReferral != null,
                existingSellerName = existingSeller?.name,
            )
        }
    }

    /**
     * Manually create a referral link between a seller and a customer.
     * This is for edge cases where the cookie-based referral was lost.
     */
    @PostMapping("/referrals/manual")
    fun createManualReferral(
        @RequestBody request: CreateManualReferralRequest,
    ): ResponseEntity<ManualReferralResultDTO> {
        // Validate seller exists
        val seller = sellerRepository.findById(request.sellerId)
            ?: return ResponseEntity.badRequest().body(
                ManualReferralResultDTO(
                    success = false,
                    error = "Seller not found",
                )
            )

        // Check if seller is active
        if (!seller.isActive()) {
            return ResponseEntity.badRequest().body(
                ManualReferralResultDTO(
                    success = false,
                    error = "Seller is not active",
                )
            )
        }

        // Check if user exists in Keycloak
        val user = keycloakAdminService.getUserById(request.customerUserId)
        if (user == null) {
            return ResponseEntity.badRequest().body(
                ManualReferralResultDTO(
                    success = false,
                    error = "Customer user not found",
                )
            )
        }

        // Check for existing referral
        val existingReferral = sellerReferralRepository.findByCustomerUserId(request.customerUserId)
        if (existingReferral != null) {
            val existingSeller = sellerRepository.findById(existingReferral.sellerId)
            return ResponseEntity.badRequest().body(
                ManualReferralResultDTO(
                    success = false,
                    error = "Customer already has a referral (linked to ${existingSeller?.name ?: "unknown seller"})",
                )
            )
        }

        // Don't allow self-referrals
        if (seller.userId == request.customerUserId) {
            return ResponseEntity.badRequest().body(
                ManualReferralResultDTO(
                    success = false,
                    error = "Cannot create self-referral",
                )
            )
        }

        // Create the referral
        val referral = SellerReferral.createForUser(
            sellerId = seller.id,
            customerUserId = request.customerUserId,
            subscriptionId = null, // Will be set when user subscribes
            commissionPeriodMonths = seller.commissionPeriodMonths,
        )

        sellerReferralRepository.save(referral)

        logger.info("Manual referral created: seller=${seller.name}, customer=${user.displayName()}")

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ManualReferralResultDTO(
                success = true,
                referralId = referral.id,
                sellerName = seller.name,
                customerName = user.displayName(),
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

    data class CreateSellerRequest(
        val userId: String,
        val name: String,
        val email: String,
        val phone: String? = null,
        val commissionPercent: BigDecimal? = null,
        val commissionCapPerCustomer: BigDecimal? = null,
        val commissionPeriodMonths: Int? = null,
        val minSalesToMaintain: Int? = null,
        val statusPeriodMonths: Int? = null,
        val payoutMethod: String? = null,
        val payoutDetails: Map<String, Any>? = null,
    )

    data class UpdateSellerRequest(
        val name: String? = null,
        val email: String? = null,
        val phone: String? = null,
        val commissionPercent: BigDecimal? = null,
        val commissionCapPerCustomer: BigDecimal? = null,
        val commissionPeriodMonths: Int? = null,
        val minSalesToMaintain: Int? = null,
        val statusPeriodMonths: Int? = null,
        val payoutMethod: String? = null,
        val payoutDetails: Map<String, Any>? = null,
        val status: String? = null,
    )

    data class SellerDTO(
        val id: UUID,
        val userId: String,
        val name: String,
        val email: String,
        val phone: String?,
        val referralCode: String,
        val commissionPercent: BigDecimal,
        val commissionCapPerCustomer: BigDecimal?,
        val commissionPeriodMonths: Int,
        val minSalesToMaintain: Int,
        val statusPeriodMonths: Int,
        val payoutMethod: String,
        val status: String,
        val statusValidUntil: Instant?,
        val createdAt: Instant,
        val createdBy: String,
    ) {
        companion object {
            fun from(seller: Seller): SellerDTO = SellerDTO(
                id = seller.id,
                userId = seller.userId,
                name = seller.name,
                email = seller.email,
                phone = seller.phone,
                referralCode = seller.referralCode,
                commissionPercent = seller.commissionPercent,
                commissionCapPerCustomer = seller.commissionCapPerCustomer,
                commissionPeriodMonths = seller.commissionPeriodMonths,
                minSalesToMaintain = seller.minSalesToMaintain,
                statusPeriodMonths = seller.statusPeriodMonths,
                payoutMethod = seller.payoutMethod.name,
                status = seller.status.name,
                statusValidUntil = seller.statusValidUntil,
                createdAt = seller.createdAt,
                createdBy = seller.createdBy,
            )
        }
    }

    data class SellerSummaryDTO(
        val seller: SellerDTO,
        val totalReferrals: Int,
        val activeReferrals: Int,
        val totalEarned: BigDecimal,
        val unpaidCommission: BigDecimal,
    ) {
        companion object {
            fun from(summary: GetAdminSalesDashboardUseCase.SellerSummary): SellerSummaryDTO =
                SellerSummaryDTO(
                    seller = SellerDTO.from(summary.seller),
                    totalReferrals = summary.totalReferrals,
                    activeReferrals = summary.activeReferrals,
                    totalEarned = summary.totalEarned,
                    unpaidCommission = summary.unpaidCommission,
                )
        }
    }

    data class AdminDashboardDTO(
        val totalSellers: Int,
        val activeSellers: Int,
        val totalOrganizations: Int,
        val activeOrganizations: Int,
        val totalReferrals: Int,
        val totalCommissionEarned: BigDecimal,
        val totalCommissionPaid: BigDecimal,
        val pendingPayouts: BigDecimal,
        val sellers: List<SellerSummaryDTO>,
    )

    // Manual Referral DTOs

    data class UserSearchResultDTO(
        val id: String,
        val email: String?,
        val name: String,
        val hasReferral: Boolean,
        val existingSellerName: String?,
    )

    data class CreateManualReferralRequest(
        val sellerId: UUID,
        val customerUserId: String,
    )

    data class ManualReferralResultDTO(
        val success: Boolean,
        val error: String? = null,
        val referralId: UUID? = null,
        val sellerName: String? = null,
        val customerName: String? = null,
    )
}
