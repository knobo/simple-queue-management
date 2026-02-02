package com.example.simplequeue.infrastructure.filter

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Filter that captures referral codes from URL parameters and stores them in a cookie.
 * 
 * When a user visits any page with ?ref=CODE, the code is stored in a cookie
 * that persists for 30 days. This allows the referral to be processed when
 * the user eventually logs in.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ReferralCookieFilter : Filter {

    private val logger = LoggerFactory.getLogger(ReferralCookieFilter::class.java)

    companion object {
        const val REFERRAL_COOKIE_NAME = "referral_code"
        const val REFERRAL_PARAM_NAME = "ref"
        const val COOKIE_MAX_AGE_SECONDS = 30 * 24 * 60 * 60 // 30 days
    }

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain
    ) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        // Check if there's a referral code in the URL
        val referralCode = httpRequest.getParameter(REFERRAL_PARAM_NAME)
        
        if (!referralCode.isNullOrBlank()) {
            logger.info("Referral code found in URL: $referralCode")
            
            // Set the cookie
            val cookie = Cookie(REFERRAL_COOKIE_NAME, referralCode).apply {
                maxAge = COOKIE_MAX_AGE_SECONDS
                path = "/"
                isHttpOnly = true
                // secure = true // Enable in production with HTTPS
            }
            httpResponse.addCookie(cookie)
            
            logger.info("Referral cookie set for code: $referralCode")
        }

        chain.doFilter(request, response)
    }
}
