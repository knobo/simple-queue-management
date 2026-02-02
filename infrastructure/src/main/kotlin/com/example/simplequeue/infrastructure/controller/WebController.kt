package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.service.SubscriptionLimits
import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.application.usecase.CreateSellerUseCase
import com.example.simplequeue.application.usecase.GetAdminSalesDashboardUseCase
import com.example.simplequeue.application.usecase.GetMyQueuesUseCase
import com.example.simplequeue.application.usecase.GetSellerDashboardUseCase
import com.example.simplequeue.application.usecase.IssueTicketUseCase
import com.example.simplequeue.application.usecase.SendTicketEmailUseCase
import com.example.simplequeue.domain.model.Seller
import com.example.simplequeue.domain.model.Ticket
import com.fasterxml.jackson.databind.ObjectMapper
import com.example.simplequeue.domain.port.CounterRepository
import com.example.simplequeue.domain.port.InviteRepository
import com.example.simplequeue.domain.port.QueueMemberRepository
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.QueueStateRepository
import com.example.simplequeue.domain.port.SellerPaymentGateway
import com.example.simplequeue.domain.port.SellerRepository
import com.example.simplequeue.domain.port.TicketRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.math.BigDecimal
import java.util.UUID

@Controller
class WebController(
    private val queueRepository: QueueRepository,
    private val ticketRepository: TicketRepository,
    private val issueTicketUseCase: IssueTicketUseCase,
    private val sendTicketEmailUseCase: SendTicketEmailUseCase,
    private val queueStateRepository: QueueStateRepository,
    private val queueMemberRepository: QueueMemberRepository,
    private val inviteRepository: InviteRepository,
    private val counterRepository: CounterRepository,
    private val subscriptionService: SubscriptionService,
    private val getAdminSalesDashboardUseCase: GetAdminSalesDashboardUseCase,
    private val createSellerUseCase: CreateSellerUseCase,
    private val getMyQueuesUseCase: GetMyQueuesUseCase,
    private val getSellerDashboardUseCase: GetSellerDashboardUseCase,
    private val sellerRepository: SellerRepository,
    private val sellerPaymentGateway: SellerPaymentGateway,
    private val objectMapper: ObjectMapper,
    @Value("\${ntfy.url:https://ntfy.sh}") private val ntfyUrl: String,
    @Value("\${app.base-url:http://localhost:8080}") private val baseUrl: String,
) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(WebController::class.java)
    }

    @GetMapping("/debug")
    fun debugPage(): String = "debug"

    @GetMapping("/")
    fun home(
        model: Model,
        @AuthenticationPrincipal user: OAuth2User?,
    ): String {
        // If user is authenticated, check if they have queues
        if (user != null) {
            val queues = getMyQueuesUseCase.execute(user.name)
            return if (queues.isNotEmpty()) {
                "redirect:/dashboard"
            } else {
                "redirect:/portal"
            }
        }
        // Show landing page for non-authenticated users
        return "landing"
    }

    /**
     * Signup endpoint that redirects to Keycloak with prompt=create
     * This triggers the registration form instead of login form.
     * See: https://www.keycloak.org/docs/latest/securing_apps/#_params_forwarding
     */
    @GetMapping("/signup")
    fun signup(): String {
        return "redirect:/oauth2/authorization/keycloak?prompt=create"
    }

    @GetMapping("/dashboard")
    fun dashboard(
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        val username = user.attributes["preferred_username"] as String
        val queues = queueRepository.findByOwnerId(user.name)

        val queuesView =
            queues.map { queue ->
                val waitingTickets = ticketRepository.findByQueueIdAndStatus(queue.id, Ticket.TicketStatus.WAITING)
                val activeTickets = ticketRepository.findByQueueIdAndStatus(queue.id, Ticket.TicketStatus.CALLED)

                mapOf(
                    "id" to queue.id,
                    "name" to queue.name,
                    "open" to queue.open,
                    "waitingTickets" to waitingTickets.map { toTicketMap(it) },
                    "activeTickets" to activeTickets.map { toTicketMap(it) },
                    "ntfyTopic" to "simple-queue-${queue.id}",
                    "qrCodeSecret" to queue.qrCodeSecret,
                    "displayToken" to queue.displayToken,
                    "accessTokenMode" to queue.accessTokenMode.name,
                    "isStaticMode" to (queue.accessTokenMode == com.example.simplequeue.domain.model.AccessTokenMode.STATIC),
                    "staticJoinUrl" to "$baseUrl/public/q/${queue.id}/join?secret=${queue.qrCodeSecret}",
                )
            }

        model.addAttribute("queues", queuesView)
        model.addAttribute("username", username)
        model.addAttribute("ntfyUrl", ntfyUrl)
        model.addAttribute("isSuperadmin", hasSuperadminRole(user))
        return "dashboard"
    }

    private fun toTicketMap(ticket: Ticket): Map<String, Any?> =
        mapOf(
            "id" to ticket.id,
            "code" to String.format("A-%03d", ticket.number),
            "name" to ticket.name,
            "number" to ticket.number,
        )

    @GetMapping("/create-queue")
    fun createQueuePage(): String = "create-queue"

    // Public QR Code Page
    @GetMapping("/public/q/{queueId}/qr")
    fun displayQr(
        @PathVariable queueId: UUID,
        model: Model,
    ): String {
        val queue = queueRepository.findById(queueId) ?: throw IllegalArgumentException("Queue not found")
        model.addAttribute("queue", queue)
        model.addAttribute("queueId", queueId)
        return "qr-code"
    }

    // Public Join Page
    @GetMapping("/public/q/{queueId}/join")
    fun joinQueuePage(
        @PathVariable queueId: UUID,
        @RequestParam("secret") secret: String,
        @RequestParam(defaultValue = "false") kiosk: Boolean,
        model: Model,
    ): String {
        logger.info("Joining queue page: {}, kiosk mode: {}", queueId, kiosk)

        val queue = queueRepository.findById(queueId) ?: throw IllegalArgumentException("Queue not found")
        logger.info("Joining queue page: {}", queue)
        model.addAttribute("queue", queue)
        model.addAttribute("queueId", queueId)
        model.addAttribute("secret", secret)
        model.addAttribute("kioskMode", kiosk)
        return "join-queue"
    }

    @PostMapping("/public/q/{queueId}/ticket")
    fun issueTicket(
        @PathVariable queueId: UUID,
        @RequestParam("secret") secret: String,
        @RequestParam("name", required = false) name: String?,
        @RequestParam("email", required = false) email: String?,
        @RequestParam(defaultValue = "false") kiosk: Boolean,
        model: Model,
    ): String {
        val cleanEmail = email?.takeIf { it.isNotBlank() }
        val ticket = issueTicketUseCase.execute(queueId, secret, name, cleanEmail)
        
        // Auto-send email if provided
        if (!cleanEmail.isNullOrBlank()) {
            try {
                sendTicketEmailUseCase.execute(ticket.id, cleanEmail)
                logger.info("Auto-sent ticket email to {} for ticket {}", cleanEmail, ticket.id)
            } catch (e: Exception) {
                logger.warn("Failed to auto-send ticket email to {}: {}", cleanEmail, e.message)
                // Don't fail the request if email sending fails
            }
        }
        
        // Include kiosk parameter in redirect if enabled
        return if (kiosk) {
            "redirect:/public/tickets/${ticket.id}?kiosk=true"
        } else {
            "redirect:/public/tickets/${ticket.id}"
        }
    }

    @GetMapping("/queue/{queueId}/admin")
    fun adminQueuePage(
        @PathVariable queueId: UUID,
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        val queue = queueRepository.findById(queueId) ?: throw IllegalArgumentException("Queue not found")
        val username = user.attributes["preferred_username"] as String
        
        // Check ownership
        if (queue.ownerId != user.name) {
            throw IllegalStateException("Access denied")
        }

        val waitingCount = ticketRepository.countByQueueId(queueId)
        val states = queueStateRepository.findByQueueId(queueId)
        val members = queueMemberRepository.findByQueueId(queueId)
        val pendingInvites = inviteRepository.findPendingByQueueId(queueId)
        val counters = counterRepository.findByQueueId(queueId)
        val tierLimits = subscriptionService.getTierLimitForUser(queue.ownerId)

        model.addAttribute("queue", queue)
        model.addAttribute("waitingCount", waitingCount)
        model.addAttribute("states", states)
        model.addAttribute("members", members)
        model.addAttribute("pendingInvites", pendingInvites)
        model.addAttribute("counters", counters)
        model.addAttribute("tierLimits", tierLimits)
        model.addAttribute("username", username)
        return "admin-queue"
    }

    @GetMapping("/public/q/{queueId}/display")
    fun displayStand(
        @PathVariable queueId: UUID,
        model: Model,
    ): String {
        val queue = queueRepository.findById(queueId) ?: throw IllegalArgumentException("Queue not found")
        val waitingTickets = ticketRepository.findByQueueIdAndStatus(queue.id, Ticket.TicketStatus.WAITING)
        val activeTickets = ticketRepository.findByQueueIdAndStatus(queue.id, Ticket.TicketStatus.CALLED)

        model.addAttribute("queue", queue)
        model.addAttribute("waitingTickets", waitingTickets.map { toTicketMap(it) })
        model.addAttribute("activeTickets", activeTickets.map { toTicketMap(it) })

        return "display-stand"
    }

    @GetMapping("/public/tickets/{ticketId}")
    fun ticketStatus(
        @PathVariable ticketId: UUID,
        model: Model,
    ): String {
        val ticket = ticketRepository.findById(ticketId) ?: throw IllegalArgumentException("Ticket not found")
        val queue = queueRepository.findById(ticket.queueId) ?: throw IllegalArgumentException("Queue not found")
        
        // Check if queue is closed
        if (!queue.open) {
            throw QueueClosedException("Queue is currently closed")
        }
        
        val queueId = ticket.queueId

        // Calculate position?
        // Last called number
        val lastCalled = ticketRepository.getLastCalledNumber(queueId)
        val avgTime = ticketRepository.getAverageProcessingTimeSeconds(queueId)

        model.addAttribute("ticket", ticket)
        model.addAttribute("queue", queue)
        model.addAttribute("lastCalled", lastCalled)
        model.addAttribute("avgTime", avgTime)
        model.addAttribute("queueTopic", "simple-queue-$queueId")
        model.addAttribute("autoCloseSeconds", queue.autoCloseSeconds)

        return "ticket-status"
    }

    @PostMapping("/public/tickets/{ticketId}/send-email")
    fun sendTicketToEmail(
        @PathVariable ticketId: UUID,
        @RequestParam email: String,
        redirectAttributes: RedirectAttributes,
    ): String {
        sendTicketEmailUseCase.execute(ticketId, email)
        redirectAttributes.addFlashAttribute("emailSent", true)
        return "redirect:/public/tickets/$ticketId"
    }

    @GetMapping("/subscription")
    fun subscriptionPage(
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        val subscription = subscriptionService.getOrCreateSubscription(user.name)
        val limits = subscriptionService.getLimits(user.name)
        val queueCount = subscriptionService.getQueueCount(user.name)

        model.addAttribute("subscription", subscription)
        model.addAttribute(
            "limits",
            SubscriptionLimitsView(
                tier = limits.tier.name,
                maxQueues = limits.maxQueues,
                currentQueues = queueCount,
                maxOperatorsPerQueue = limits.maxOperatorsPerQueue,
                maxTicketsPerDay = limits.maxTicketsPerDay,
                canUseEmailNotifications = limits.canUseEmailNotifications,
                canUseCustomBranding = limits.canUseCustomBranding,
            ),
        )
        return "subscription"
    }

    @GetMapping("/subscription/success")
    fun subscriptionSuccess(
        @RequestParam("session_id") sessionId: String,
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        // Redirect to subscription page - webhook handles the actual upgrade
        return "redirect:/subscription?upgraded=true"
    }

    @GetMapping("/subscription/cancel")
    fun subscriptionCancel(): String {
        return "redirect:/subscription"
    }

    data class SubscriptionLimitsView(
        val tier: String,
        val maxQueues: Int,
        val currentQueues: Int,
        val maxOperatorsPerQueue: Int,
        val maxTicketsPerDay: Int,
        val canUseEmailNotifications: Boolean,
        val canUseCustomBranding: Boolean,
    )

    @GetMapping("/admin/sales")
    fun adminSalesDashboard(
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        logger.info("========== /admin/sales DEBUG START ==========")
        logger.info("OAuth2User type: ${user::class.java.name}")
        logger.info("OAuth2User name (subject): ${user.name}")
        
        // CRITICAL: Check if this is an OidcUser (from OIDC login)
        logger.info("--- OIDC USER CHECK ---")
        logger.info("Is OidcUser: ${user is OidcUser}")
        if (user is OidcUser) {
            logger.info("User IS an OidcUser - checking OIDC-specific data")
            logger.info("  ID Token subject: ${user.idToken?.subject}")
            logger.info("  ID Token claims keys: ${user.idToken?.claims?.keys}")
            logger.info("  UserInfo claims: ${user.userInfo?.claims}")
            
            logger.info("--- ALL OIDC CLAIMS ---")
            user.claims.forEach { (key, value) ->
                logger.info("  OIDC [$key] = $value (type: ${value?.javaClass?.name})")
            }
            
            // Check realm_access in OIDC claims
            val oidcRealmAccess = user.claims["realm_access"]
            logger.info("realm_access from OIDC claims: $oidcRealmAccess")
            logger.info("realm_access type: ${oidcRealmAccess?.javaClass?.name}")
            
            if (oidcRealmAccess == null) {
                logger.error("!!! realm_access is NOT in OIDC claims !!!")
                logger.error("Keycloak is not including realm_access in the ID token.")
            }
        } else {
            logger.warn("User is NOT an OidcUser - this is unusual for OIDC login")
        }
        
        // Check OAuth2User attributes (might be different from OIDC claims)
        logger.info("--- ALL OAuth2User ATTRIBUTES ---")
        logger.info("Attributes keys: ${user.attributes.keys}")
        user.attributes.forEach { (key, value) ->
            logger.info("  ATTR [$key] = $value (type: ${value?.javaClass?.name})")
        }
        
        // Check authorities from Spring Security
        logger.info("--- SPRING SECURITY AUTHORITIES ---")
        user.authorities.forEach { auth ->
            logger.info("  AUTHORITY: ${auth.authority}")
        }
        
        // Detailed realm_access inspection from attributes
        logger.info("--- REALM_ACCESS from attributes ---")
        val realmAccessRaw = user.attributes["realm_access"]
        logger.info("realm_access value: $realmAccessRaw")
        logger.info("realm_access type: ${realmAccessRaw?.javaClass?.name}")
        
        if (realmAccessRaw == null) {
            logger.error("!!! realm_access is NULL in OAuth2User.attributes !!!")
            logger.error("ROOT CAUSE: Keycloak is not including realm_access in ID token or userinfo")
            logger.error("")
            logger.error("SOLUTION - Configure Keycloak:")
            logger.error("  1. Go to Keycloak Admin Console")
            logger.error("  2. Select realm: simple-queue")
            logger.error("  3. Go to: Clients > web > Client scopes > web-dedicated")
            logger.error("  4. Add mapper: 'realm roles'")
            logger.error("     - Mapper type: User Realm Role")
            logger.error("     - Token Claim Name: realm_access.roles")
            logger.error("     - Add to ID token: ON")
            logger.error("     - Add to access token: ON")
            logger.error("     - Add to userinfo: ON")
            logger.error("")
        }
        
        // Check hasSuperadminRole result
        val isSuperadmin = hasSuperadminRole(user)
        logger.info("hasSuperadminRole() result: $isSuperadmin")

        // Check if user has superadmin role
        if (!isSuperadmin) {
            logger.warn("ACCESS DENIED: User ${user.name} does not have superadmin role")
            logger.info("========== /admin/sales DEBUG END (DENIED) ==========")
            return "redirect:/dashboard"
        }

        logger.info("ACCESS GRANTED: User has superadmin role!")
        
        val dashboard = getAdminSalesDashboardUseCase.execute()

        model.addAttribute("totalSellers", dashboard.totalSellers)
        model.addAttribute("activeSellers", dashboard.activeSellers)
        model.addAttribute("totalOrganizations", dashboard.totalOrganizations)
        model.addAttribute("activeOrganizations", dashboard.activeOrganizations)
        model.addAttribute("totalReferrals", dashboard.totalReferrals)
        model.addAttribute("totalCommissionEarned", dashboard.totalCommissionEarned)
        model.addAttribute("totalCommissionPaid", dashboard.totalCommissionPaid)
        model.addAttribute("pendingPayouts", dashboard.pendingPayouts)
        model.addAttribute("sellers", dashboard.sellers)

        logger.info("Returning template: admin-sales")
        logger.info("========== /admin/sales DEBUG END (SUCCESS) ==========")
        return "admin-sales"
    }

    @GetMapping("/admin/sales/sellers/new")
    fun adminSalesSellerNew(
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        // Check if user has superadmin role
        if (!hasSuperadminRole(user)) {
            logger.warn("ACCESS DENIED: User ${user.name} does not have superadmin role for /admin/sales/sellers/new")
            return "redirect:/dashboard"
        }

        // Add PayoutMethod enum values to model for dropdown
        model.addAttribute("payoutMethods", Seller.PayoutMethod.entries)
        return "admin-sales-seller-new"
    }

    @PostMapping("/admin/sales/sellers")
    fun adminSalesSellerCreate(
        @AuthenticationPrincipal user: OAuth2User,
        @RequestParam("userId") userId: String,
        @RequestParam("name") name: String,
        @RequestParam("email") email: String,
        @RequestParam("phone", required = false) phone: String?,
        @RequestParam("commissionPercent", required = false) commissionPercent: BigDecimal?,
        @RequestParam("commissionCapPerCustomer", required = false) commissionCapPerCustomer: BigDecimal?,
        @RequestParam("commissionPeriodMonths", required = false) commissionPeriodMonths: Int?,
        @RequestParam("minSalesToMaintain", required = false) minSalesToMaintain: Int?,
        @RequestParam("statusPeriodMonths", required = false) statusPeriodMonths: Int?,
        @RequestParam("payoutMethod", required = false) payoutMethod: String?,
        redirectAttributes: RedirectAttributes,
    ): String {
        // Check if user has superadmin role
        if (!hasSuperadminRole(user)) {
            logger.warn("ACCESS DENIED: User ${user.name} does not have superadmin role for POST /admin/sales/sellers")
            return "redirect:/dashboard"
        }

        return try {
            createSellerUseCase.execute(
                CreateSellerUseCase.CreateSellerRequest(
                    userId = userId,
                    name = name,
                    email = email,
                    phone = phone?.takeIf { it.isNotBlank() },
                    commissionPercent = commissionPercent ?: BigDecimal("20.00"),
                    commissionCapPerCustomer = commissionCapPerCustomer,
                    commissionPeriodMonths = commissionPeriodMonths ?: 12,
                    minSalesToMaintain = minSalesToMaintain ?: 5,
                    statusPeriodMonths = statusPeriodMonths ?: 6,
                    payoutMethod = payoutMethod?.let { Seller.PayoutMethod.valueOf(it) }
                        ?: Seller.PayoutMethod.MANUAL,
                    payoutDetails = null,
                ),
                createdBy = user.name,
            )
            redirectAttributes.addFlashAttribute("success", "Seller created successfully!")
            "redirect:/admin/sales"
        } catch (e: IllegalStateException) {
            logger.error("Failed to create seller: ${e.message}", e)
            redirectAttributes.addFlashAttribute("error", "Failed to create seller: ${e.message}")
            "redirect:/admin/sales/sellers/new"
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid seller data: ${e.message}", e)
            redirectAttributes.addFlashAttribute("error", "Invalid data: ${e.message}")
            "redirect:/admin/sales/sellers/new"
        }
    }

    @GetMapping("/admin/tier-limits")
    fun adminTierLimits(
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        // Check if user has superadmin role
        if (!hasSuperadminRole(user)) {
            logger.warn("ACCESS DENIED: User ${user.name} does not have superadmin role for /admin/tier-limits")
            return "redirect:/dashboard"
        }

        val limits = subscriptionService.getAllTierLimits().map { limit ->
            mapOf(
                "tier" to limit.tier.name,
                "maxQueues" to limit.maxQueues,
                "maxOperatorsPerQueue" to limit.maxOperatorsPerQueue,
                "maxTicketsPerDay" to limit.maxTicketsPerDay,
                "maxActiveTickets" to limit.maxActiveTickets,
                "maxInvitesPerMonth" to limit.maxInvitesPerMonth,
                "canUseEmailNotifications" to limit.canUseEmailNotifications,
                "canUseCustomBranding" to limit.canUseCustomBranding,
                "canUseAnalytics" to limit.canUseAnalytics,
                "canUseApiAccess" to limit.canUseApiAccess,
                "isMaxQueuesUnlimited" to limit.isUnlimited(limit.maxQueues),
                "isMaxOperatorsUnlimited" to limit.isUnlimited(limit.maxOperatorsPerQueue),
                "isMaxTicketsUnlimited" to limit.isUnlimited(limit.maxTicketsPerDay),
                "isMaxActiveTicketsUnlimited" to limit.isUnlimited(limit.maxActiveTickets),
                "isMaxInvitesUnlimited" to limit.isUnlimited(limit.maxInvitesPerMonth),
                "updatedAt" to limit.updatedAt,
                "updatedBy" to limit.updatedBy,
            )
        }

        model.addAttribute("limits", limits)
        // Also pass as JSON for JavaScript
        model.addAttribute("limitsJson", objectMapper.writeValueAsString(limits))

        return "admin-tier-limits"
    }

    // =============================================================================
    // Seller Dashboard
    // =============================================================================

    @GetMapping("/seller/dashboard")
    fun sellerDashboard(
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        // Check if user is a seller
        val seller = sellerRepository.findByUserId(user.name)
        if (seller == null) {
            logger.warn("User ${user.name} tried to access seller dashboard but is not a seller")
            return "redirect:/dashboard"
        }

        return try {
            val dashboard = getSellerDashboardUseCase.execute(user.name)

            // Determine Stripe Connect status
            val stripeStatus = when {
                seller.stripeAccountId == null -> "NOT_CONNECTED"
                seller.stripeChargesEnabled && seller.stripePayoutsEnabled -> "ACTIVE"
                else -> "PENDING"
            }

            // Build referral link
            val referralLink = "$baseUrl/signup?ref=${seller.referralCode}"

            model.addAttribute("seller", seller)
            model.addAttribute("totalReferrals", dashboard.totalReferrals)
            model.addAttribute("activeReferrals", dashboard.activeReferrals)
            model.addAttribute("totalCommissionEarned", dashboard.totalCommissionEarned)
            model.addAttribute("unpaidCommission", dashboard.unpaidCommission)
            model.addAttribute("salesThisPeriod", dashboard.salesThisPeriod)
            model.addAttribute("daysUntilStatusExpires", dashboard.daysUntilStatusExpires)
            model.addAttribute("stripeStatus", stripeStatus)
            model.addAttribute("referralLink", referralLink)

            "seller-dashboard"
        } catch (e: IllegalStateException) {
            logger.error("Error loading seller dashboard: ${e.message}", e)
            "redirect:/dashboard"
        }
    }

    @GetMapping("/seller/connect/return")
    fun sellerConnectReturn(
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        val seller = sellerRepository.findByUserId(user.name)
        if (seller == null) {
            logger.warn("User ${user.name} tried to access connect return but is not a seller")
            return "redirect:/dashboard"
        }

        // Fetch latest account status from Stripe if we have an account
        val accountId = seller.stripeAccountId
        if (accountId != null) {
            try {
                val status = sellerPaymentGateway.getAccountStatus(accountId)
                model.addAttribute("chargesEnabled", status.chargesEnabled)
                model.addAttribute("payoutsEnabled", status.payoutsEnabled)
                model.addAttribute("detailsSubmitted", status.detailsSubmitted)

                // Update seller's Stripe status in database
                val updatedSeller = seller.withStripeStatus(
                    chargesEnabled = status.chargesEnabled,
                    payoutsEnabled = status.payoutsEnabled,
                    onboardingCompleted = status.detailsSubmitted,
                )
                sellerRepository.save(updatedSeller)
            } catch (e: Exception) {
                logger.error("Failed to fetch Stripe account status: ${e.message}", e)
            }
        }

        return "seller-connect-return"
    }

    @GetMapping("/seller/connect/refresh")
    fun sellerConnectRefresh(
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        val seller = sellerRepository.findByUserId(user.name)
        if (seller == null) {
            logger.warn("User ${user.name} tried to access connect refresh but is not a seller")
            return "redirect:/dashboard"
        }

        return "seller-connect-refresh"
    }

    private fun hasSuperadminRole(user: OAuth2User): Boolean {
        logger.info("Checking superadmin role for user: ${user.name}")
        logger.debug("Attributes: ${user.attributes}")
        
        // Helper function to extract roles from realm_access (handles various types)
        fun extractRoles(realmAccess: Any?): List<*>? {
            logger.info("  realm_access raw: $realmAccess (type: ${realmAccess?.javaClass})")
            return when (realmAccess) {
                is Map<*, *> -> {
                    val rolesValue = realmAccess["roles"]
                    logger.info("  roles raw: $rolesValue (type: ${rolesValue?.javaClass})")
                    when (rolesValue) {
                        is List<*> -> rolesValue
                        is Collection<*> -> rolesValue.toList()
                        else -> {
                            logger.warn("  roles is not a List/Collection: ${rolesValue?.javaClass}")
                            null
                        }
                    }
                }
                else -> {
                    if (realmAccess != null) {
                        logger.warn("  realm_access is not a Map: ${realmAccess.javaClass}")
                    }
                    null
                }
            }
        }
        
        // First: Try OIDC claims if user is OidcUser
        if (user is OidcUser) {
            logger.info("Checking OIDC claims (user is OidcUser)")
            val oidcRealmAccess = user.claims["realm_access"]
            val roles = extractRoles(oidcRealmAccess)
            logger.info("  OIDC roles: $roles")
            if (roles?.any { it?.toString() == "superadmin" } == true) {
                logger.info("  FOUND superadmin in OIDC claims!")
                return true
            }
        }
        
        // Second: Try OAuth2User attributes
        logger.info("Checking OAuth2User attributes")
        val realmAccessRaw = user.attributes["realm_access"]
        val roles = extractRoles(realmAccessRaw)
        logger.info("  Attribute roles: $roles")
        if (roles?.any { it?.toString() == "superadmin" } == true) {
            logger.info("  FOUND superadmin in attributes!")
            return true
        }
        
        // Third: Check Spring Security authorities as fallback
        logger.info("Checking Spring Security authorities as fallback")
        val authoritiesContainSuperadmin = user.authorities.any { 
            val auth = it.authority
            auth == "ROLE_superadmin" || auth == "ROLE_SUPERADMIN" || auth == "superadmin"
        }
        if (authoritiesContainSuperadmin) {
            logger.info("  FOUND superadmin in authorities!")
            return true
        }
        
        logger.warn("superadmin role NOT FOUND anywhere!")
        return false
    }
}
