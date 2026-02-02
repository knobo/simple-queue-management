package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.application.usecase.GetAllFeedbackUseCase
import com.example.simplequeue.application.usecase.GetMyFeedbackUseCase
import com.example.simplequeue.application.usecase.SubmitFeedbackUseCase
import com.example.simplequeue.domain.model.FeedbackCategory
import com.example.simplequeue.domain.model.FeedbackStatus
import com.example.simplequeue.domain.model.FeedbackType
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

@Controller
class FeedbackController(
    private val submitFeedbackUseCase: SubmitFeedbackUseCase,
    private val getMyFeedbackUseCase: GetMyFeedbackUseCase,
    private val getAllFeedbackUseCase: GetAllFeedbackUseCase,
    private val subscriptionService: SubscriptionService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(FeedbackController::class.java)
    }

    // =========================================================================
    // User Feedback Endpoints
    // =========================================================================

    /**
     * Display the feedback submission form.
     */
    @GetMapping("/feedback")
    fun feedbackForm(
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        model.addAttribute("feedbackTypes", FeedbackType.entries)
        model.addAttribute("feedbackCategories", FeedbackCategory.entries)
        model.addAttribute("username", user.attributes["preferred_username"])
        return "feedback-form"
    }

    /**
     * Submit new feedback.
     */
    @PostMapping("/feedback")
    fun submitFeedback(
        @AuthenticationPrincipal user: OAuth2User,
        @RequestParam("type") type: String,
        @RequestParam("category") category: String,
        @RequestParam("title") title: String,
        @RequestParam("description") description: String,
        @RequestParam("screenshotUrl", required = false) screenshotUrl: String?,
        @RequestHeader("User-Agent", required = false) userAgent: String?,
        @RequestHeader("Referer", required = false) referer: String?,
        redirectAttributes: RedirectAttributes,
    ): String {
        return try {
            val email = user.attributes["email"] as? String ?: "unknown@example.com"
            val subscription = subscriptionService.getOrCreateSubscription(user.name)

            val request = SubmitFeedbackUseCase.FeedbackRequest(
                type = FeedbackType.valueOf(type),
                category = FeedbackCategory.valueOf(category),
                title = title,
                description = description,
                screenshotUrl = screenshotUrl?.takeIf { it.isNotBlank() },
                userAgent = userAgent,
                currentUrl = referer,
            )

            submitFeedbackUseCase.execute(
                userId = user.name,
                userEmail = email,
                request = request,
                subscriptionTier = subscription.tier.name,
            )

            redirectAttributes.addFlashAttribute("success", true)
            redirectAttributes.addFlashAttribute("message", "Takk for din tilbakemelding!")
            "redirect:/feedback/mine"
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid feedback submission: ${e.message}")
            redirectAttributes.addFlashAttribute("error", e.message)
            "redirect:/feedback"
        } catch (e: Exception) {
            logger.error("Failed to submit feedback", e)
            redirectAttributes.addFlashAttribute("error", "En feil oppstod. Prøv igjen senere.")
            "redirect:/feedback"
        }
    }

    /**
     * Display the user's own feedback submissions.
     */
    @GetMapping("/feedback/mine")
    fun myFeedback(
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        val feedback = getMyFeedbackUseCase.execute(user.name)
        model.addAttribute("feedbackList", feedback)
        model.addAttribute("username", user.attributes["preferred_username"])
        return "my-feedback"
    }

    /**
     * View a specific feedback item (user can only see their own).
     */
    @GetMapping("/feedback/mine/{id}")
    fun viewMyFeedback(
        @PathVariable id: UUID,
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        val feedback = getMyFeedbackUseCase.getById(user.name, id)
            ?: return "redirect:/feedback/mine"

        model.addAttribute("feedback", feedback)
        model.addAttribute("username", user.attributes["preferred_username"])
        return "feedback-detail"
    }

    // =========================================================================
    // Admin Feedback Endpoints
    // =========================================================================

    /**
     * Admin: View all feedback.
     */
    @GetMapping("/admin/feedback")
    fun adminFeedbackList(
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
        @RequestParam("type", required = false) type: String?,
        @RequestParam("status", required = false) status: String?,
        @RequestParam("category", required = false) category: String?,
        @RequestParam("search", required = false) search: String?,
    ): String {
        if (!hasSuperadminRole(user)) {
            logger.warn("ACCESS DENIED: User ${user.name} does not have superadmin role for /admin/feedback")
            return "redirect:/dashboard"
        }

        val filter = GetAllFeedbackUseCase.FeedbackFilter(
            type = type?.let { FeedbackType.valueOf(it) },
            status = status?.let { FeedbackStatus.valueOf(it) },
            category = category?.let { FeedbackCategory.valueOf(it) },
            search = search,
        )

        val feedbackList = getAllFeedbackUseCase.execute(filter)
        val stats = getAllFeedbackUseCase.getStats()

        model.addAttribute("feedbackList", feedbackList)
        model.addAttribute("stats", stats)
        model.addAttribute("feedbackTypes", FeedbackType.entries)
        model.addAttribute("feedbackStatuses", FeedbackStatus.entries)
        model.addAttribute("feedbackCategories", FeedbackCategory.entries)
        model.addAttribute("selectedType", type)
        model.addAttribute("selectedStatus", status)
        model.addAttribute("selectedCategory", category)
        model.addAttribute("searchQuery", search)

        return "admin-feedback"
    }

    /**
     * Admin: View feedback detail.
     */
    @GetMapping("/admin/feedback/{id}")
    fun adminFeedbackDetail(
        @PathVariable id: UUID,
        model: Model,
        @AuthenticationPrincipal user: OAuth2User,
    ): String {
        if (!hasSuperadminRole(user)) {
            return "redirect:/dashboard"
        }

        val feedback = getAllFeedbackUseCase.getById(id)
            ?: return "redirect:/admin/feedback"

        val notes = getAllFeedbackUseCase.getNotes(id)

        model.addAttribute("feedback", feedback)
        model.addAttribute("notes", notes)
        model.addAttribute("feedbackStatuses", FeedbackStatus.entries)

        return "admin-feedback-detail"
    }

    /**
     * Admin: Update feedback status.
     */
    @PostMapping("/admin/feedback/{id}/status")
    fun updateFeedbackStatus(
        @PathVariable id: UUID,
        @RequestParam("status") status: String,
        @AuthenticationPrincipal user: OAuth2User,
        redirectAttributes: RedirectAttributes,
    ): String {
        if (!hasSuperadminRole(user)) {
            return "redirect:/dashboard"
        }

        try {
            getAllFeedbackUseCase.updateStatus(id, FeedbackStatus.valueOf(status))
            redirectAttributes.addFlashAttribute("success", "Status oppdatert")
        } catch (e: Exception) {
            logger.error("Failed to update feedback status", e)
            redirectAttributes.addFlashAttribute("error", "Kunne ikke oppdatere status")
        }

        return "redirect:/admin/feedback/$id"
    }

    /**
     * Admin: Add note to feedback.
     */
    @PostMapping("/admin/feedback/{id}/notes")
    fun addFeedbackNote(
        @PathVariable id: UUID,
        @RequestParam("note") note: String,
        @AuthenticationPrincipal user: OAuth2User,
        redirectAttributes: RedirectAttributes,
    ): String {
        if (!hasSuperadminRole(user)) {
            return "redirect:/dashboard"
        }

        try {
            getAllFeedbackUseCase.addNote(id, user.name, note)
            redirectAttributes.addFlashAttribute("success", "Notat lagt til")
        } catch (e: Exception) {
            logger.error("Failed to add feedback note", e)
            redirectAttributes.addFlashAttribute("error", "Kunne ikke legge til notat")
        }

        return "redirect:/admin/feedback/$id"
    }

    // =========================================================================
    // Helper Functions
    // =========================================================================

    private fun hasSuperadminRole(user: OAuth2User): Boolean {
        // Helper function to extract roles from realm_access
        fun extractRoles(realmAccess: Any?): List<*>? {
            return when (realmAccess) {
                is Map<*, *> -> {
                    when (val rolesValue = realmAccess["roles"]) {
                        is List<*> -> rolesValue
                        is Collection<*> -> rolesValue.toList()
                        else -> null
                    }
                }
                else -> null
            }
        }

        // Check OIDC claims if user is OidcUser
        if (user is OidcUser) {
            val oidcRealmAccess = user.claims["realm_access"]
            val roles = extractRoles(oidcRealmAccess)
            if (roles?.any { it?.toString() == "superadmin" } == true) {
                return true
            }
        }

        // Check OAuth2User attributes
        val realmAccessRaw = user.attributes["realm_access"]
        val roles = extractRoles(realmAccessRaw)
        if (roles?.any { it?.toString() == "superadmin" } == true) {
            return true
        }

        // Check Spring Security authorities as fallback
        return user.authorities.any {
            val auth = it.authority
            auth == "ROLE_superadmin" || auth == "ROLE_SUPERADMIN" || auth == "superadmin"
        }
    }
}
