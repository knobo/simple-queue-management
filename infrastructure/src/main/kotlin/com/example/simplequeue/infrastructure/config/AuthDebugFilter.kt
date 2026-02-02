package com.example.simplequeue.infrastructure.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Debug filter that logs authentication details for admin API requests.
 * Helps diagnose authorization issues by showing:
 * - Principal type (OAuth2User, Jwt, etc.)
 * - All authorities/roles
 * - Keycloak realm_access claims if available
 * 
 * Only active in non-production environments.
 */
@Component
@Profile("!prod")
class AuthDebugFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(AuthDebugFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.requestURI.startsWith("/api/admin")) {
            logAuthDetails(request)
        }
        filterChain.doFilter(request, response)
    }

    private fun logAuthDetails(request: HttpServletRequest) {
        val auth = SecurityContextHolder.getContext().authentication

        log.info("========== AUTH DEBUG: ${request.method} ${request.requestURI} ==========")
        
        if (auth == null) {
            log.info("  Authentication: NULL (not authenticated)")
            return
        }

        log.info("  Authenticated: ${auth.isAuthenticated}")
        log.info("  Auth type: ${auth.javaClass.simpleName}")
        log.info("  Principal type: ${auth.principal?.javaClass?.simpleName ?: "null"}")
        log.info("  Authorities: ${auth.authorities?.map { it.authority } ?: "none"}")

        // Log additional details based on authentication type
        when (auth) {
            is OAuth2AuthenticationToken -> {
                log.info("  --- OAuth2 Web Session ---")
                val user = auth.principal
                if (user is OidcUser) {
                    // Log realm_access claims
                    @Suppress("UNCHECKED_CAST")
                    val realmAccess = user.claims["realm_access"] as? Map<String, Any>
                    if (realmAccess != null) {
                        val roles = realmAccess["roles"]
                        log.info("  realm_access.roles: $roles")
                    } else {
                        log.warn("  realm_access: NOT PRESENT IN CLAIMS")
                        log.info("  Available claims: ${user.claims.keys}")
                    }
                }
            }
            is JwtAuthenticationToken -> {
                log.info("  --- JWT Bearer Token ---")
                val jwt = auth.token
                @Suppress("UNCHECKED_CAST")
                val realmAccess = jwt.getClaim<Map<String, Any>>("realm_access")
                if (realmAccess != null) {
                    log.info("  realm_access.roles: ${realmAccess["roles"]}")
                } else {
                    log.warn("  realm_access: NOT PRESENT IN JWT")
                }
            }
        }

        // Check for specific roles
        val hasSuperadmin = auth.authorities?.any { 
            it.authority.equals("ROLE_SUPERADMIN", ignoreCase = true) ||
            it.authority.equals("ROLE_superadmin", ignoreCase = true)
        } ?: false
        log.info("  Has SUPERADMIN role: $hasSuperadmin")
        
        log.info("========== AUTH DEBUG END ==========")
    }
}
