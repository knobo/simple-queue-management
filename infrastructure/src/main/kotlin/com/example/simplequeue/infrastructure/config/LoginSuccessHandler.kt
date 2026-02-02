package com.example.simplequeue.infrastructure.config

import com.example.simplequeue.application.service.ReferralService
import com.example.simplequeue.infrastructure.filter.ReferralCookieFilter
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

/**
 * Custom login success handler for Management Platform that:
 * 1. Processes any referral code from cookie (links user to seller)
 * 2. Redirects users based on their role
 *
 * Redirect priority order:
 * 1. SUPERADMIN → /admin/sales
 * 2. SELLER → /seller/dashboard
 * 3. Default → /subscription
 */
@Component
class LoginSuccessHandler(
    private val referralService: ReferralService,
) : AuthenticationSuccessHandler {

    private val logger = LoggerFactory.getLogger(LoginSuccessHandler::class.java)

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val roles = authentication.authorities.map { it.authority }
        val principal = authentication.principal as OAuth2User
        val userId = principal.name

        logger.info("Login success for user: $userId with authorities: $roles")

        // Process referral code if present in cookie
        processReferralCookie(request, response, userId)

        val redirectUrl = when {
            roles.any { it.equals("ROLE_SUPERADMIN", ignoreCase = true) || it.equals("ROLE_superadmin", ignoreCase = true) } -> {
                logger.info("Redirecting SUPERADMIN to /admin/sales")
                "/admin/sales"
            }
            roles.any { it.equals("ROLE_SELLER", ignoreCase = true) || it.equals("ROLE_seller", ignoreCase = true) } -> {
                logger.info("Redirecting SELLER to /seller/dashboard")
                "/seller/dashboard"
            }
            else -> {
                logger.info("Redirecting to /subscription")
                "/subscription"
            }
        }

        response.sendRedirect(redirectUrl)
    }

    /**
     * Process referral code from cookie if present.
     * This links the user to the seller who referred them.
     * The cookie is deleted after processing (regardless of success).
     */
    private fun processReferralCookie(
        request: HttpServletRequest,
        response: HttpServletResponse,
        userId: String
    ) {
        val referralCode = request.cookies
            ?.find { it.name == ReferralCookieFilter.REFERRAL_COOKIE_NAME }
            ?.value

        if (referralCode != null) {
            logger.info("Found referral code cookie for user $userId: $referralCode")
            
            try {
                val success = referralService.processReferralForUser(userId, referralCode)
                if (success) {
                    logger.info("Successfully processed referral for user $userId")
                }
            } catch (e: Exception) {
                logger.error("Error processing referral for user $userId: ${e.message}", e)
            }

            // Always delete the cookie after processing (success or failure)
            deleteCookie(response, ReferralCookieFilter.REFERRAL_COOKIE_NAME)
        }
    }

    /**
     * Delete a cookie by setting its maxAge to 0.
     */
    private fun deleteCookie(response: HttpServletResponse, cookieName: String) {
        val cookie = Cookie(cookieName, "").apply {
            maxAge = 0
            path = "/"
            isHttpOnly = true
        }
        response.addCookie(cookie)
        logger.debug("Deleted cookie: $cookieName")
    }
}
