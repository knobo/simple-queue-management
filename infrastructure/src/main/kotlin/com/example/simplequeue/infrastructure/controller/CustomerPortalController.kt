package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.usecase.GetCustomerPortalUseCase
import com.example.simplequeue.application.usecase.GetMyQueuesUseCase
import com.example.simplequeue.application.usecase.GetMyTicketHistoryUseCase
import com.example.simplequeue.application.usecase.TicketHistoryPage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

/**
 * Controller for the Customer Portal.
 * This is the landing page for authenticated users who don't own any queues.
 */
@Controller
class CustomerPortalController(
    private val getCustomerPortalUseCase: GetCustomerPortalUseCase,
    private val getMyQueuesUseCase: GetMyQueuesUseCase,
    private val getMyTicketHistoryUseCase: GetMyTicketHistoryUseCase,
) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(CustomerPortalController::class.java)
    }

    /**
     * Customer Portal page.
     * Shows active ticket (if any) and ticket history.
     * Redirects to dashboard if user has queues.
     */
    @GetMapping("/portal")
    fun portal(
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        val userId = user.name
        logger.debug("Loading Customer Portal for user: {}", userId)

        // Check if user has queues - if so, redirect to dashboard
        val queues = getMyQueuesUseCase.execute(userId)
        if (queues.isNotEmpty()) {
            logger.debug("User {} has {} queues, redirecting to dashboard", userId, queues.size)
            return "redirect:/dashboard"
        }

        // Get portal data
        val portalView = getCustomerPortalUseCase.execute(userId)

        model.addAttribute("activeTicket", portalView.activeTicket)
        model.addAttribute("history", portalView.history)
        model.addAttribute("hasMoreHistory", portalView.hasMoreHistory)
        model.addAttribute("totalHistoryCount", portalView.totalHistoryCount)
        model.addAttribute("username", user.attributes["preferred_username"] ?: user.name)

        return "customer-portal"
    }

    /**
     * Ticket history page with pagination.
     */
    @GetMapping("/portal/history")
    fun history(
        @RequestParam(defaultValue = "0") page: Int,
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        val userId = user.name
        val historyPage = getMyTicketHistoryUseCase.execute(userId, page)

        model.addAttribute("historyPage", historyPage)
        model.addAttribute("username", user.attributes["preferred_username"] ?: user.name)

        return "customer-portal-history"
    }

    // =============================================================================
    // API Endpoints
    // =============================================================================

    /**
     * API endpoint to get portal data as JSON.
     */
    @GetMapping("/api/me/portal")
    @ResponseBody
    fun getPortalApi(
        @AuthenticationPrincipal user: OAuth2User,
    ): Map<String, Any?> {
        val portalView = getCustomerPortalUseCase.execute(user.name)
        return mapOf(
            "activeTicket" to portalView.activeTicket,
            "history" to portalView.history,
            "hasMoreHistory" to portalView.hasMoreHistory,
            "totalHistoryCount" to portalView.totalHistoryCount,
        )
    }

    /**
     * API endpoint to get active ticket.
     */
    @GetMapping("/api/me/active-ticket")
    @ResponseBody
    fun getActiveTicketApi(
        @AuthenticationPrincipal user: OAuth2User,
    ): Map<String, Any?> {
        val portalView = getCustomerPortalUseCase.execute(user.name, historySize = 0)
        return mapOf(
            "activeTicket" to portalView.activeTicket,
        )
    }

    /**
     * API endpoint to get ticket history with pagination.
     */
    @GetMapping("/api/me/ticket-history")
    @ResponseBody
    fun getTicketHistoryApi(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal user: OAuth2User,
    ): TicketHistoryPage {
        return getMyTicketHistoryUseCase.execute(user.name, page, size)
    }
}
