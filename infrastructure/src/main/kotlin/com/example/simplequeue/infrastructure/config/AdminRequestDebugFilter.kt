package com.example.simplequeue.infrastructure.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Debug filter to log authentication state on every request to /admin/
 */
@Component
@Profile("!test")
class AdminRequestDebugFilter : OncePerRequestFilter() {
    
    companion object {
        private val log: Logger = LoggerFactory.getLogger(AdminRequestDebugFilter::class.java)
    }
    
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val uri = request.requestURI
        
        // Only log for admin requests
        if (uri.startsWith("/admin")) {
            log.info("========== ADMIN REQUEST DEBUG FILTER ==========")
            log.info("Request URI: $uri")
            log.info("Request Method: ${request.method}")
            
            val auth = SecurityContextHolder.getContext().authentication
            log.info("Authentication: $auth")
            log.info("Authentication type: ${auth?.javaClass?.name}")
            log.info("Is authenticated: ${auth?.isAuthenticated}")
            
            if (auth != null) {
                log.info("Principal: ${auth.principal}")
                log.info("Principal type: ${auth.principal?.javaClass?.name}")
                log.info("Authorities: ${auth.authorities}")
                
                auth.authorities.forEach { grantedAuth ->
                    log.info("  AUTH: ${grantedAuth.authority}")
                }
                
                // If OAuth2AuthenticationToken, log more details
                if (auth is OAuth2AuthenticationToken) {
                    val oauth2User = auth.principal
                    log.info("--- OAuth2User in filter ---")
                    log.info("OAuth2User type: ${oauth2User?.javaClass?.name}")
                    log.info("OAuth2User attributes keys: ${oauth2User?.attributes?.keys}")
                    
                    // Check specifically for realm_access
                    val realmAccess = oauth2User?.attributes?.get("realm_access")
                    log.info("realm_access from attributes: $realmAccess")
                    log.info("realm_access type: ${realmAccess?.javaClass?.name}")
                    
                    // If OIDC user, check claims too
                    if (oauth2User is OidcUser) {
                        log.info("User is OidcUser - checking OIDC claims")
                        log.info("OIDC claims keys: ${oauth2User.claims.keys}")
                        val oidcRealmAccess = oauth2User.claims["realm_access"]
                        log.info("realm_access from OIDC claims: $oidcRealmAccess")
                        log.info("ID Token subject: ${oauth2User.idToken?.subject}")
                        log.info("ID Token claims keys: ${oauth2User.idToken?.claims?.keys}")
                    } else {
                        log.warn("User is NOT OidcUser - this might be the issue!")
                    }
                }
            }
            
            log.info("========== ADMIN REQUEST DEBUG FILTER END ==========")
        }
        
        filterChain.doFilter(request, response)
    }
}
