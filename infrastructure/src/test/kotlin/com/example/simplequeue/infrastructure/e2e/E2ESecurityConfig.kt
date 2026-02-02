package com.example.simplequeue.infrastructure.e2e

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

/**
 * Security configuration for E2E tests that:
 * 1. Permits all requests (no OAuth redirect)
 * 2. Automatically sets up a mock authenticated user for all routes
 * 
 * This allows Playwright to access authenticated pages without real OAuth flow.
 */
@TestConfiguration
@EnableWebSecurity
class E2ESecurityConfig {

    companion object {
        const val TEST_USER_ID = "e2e-test-user-id"
        const val TEST_USERNAME = "e2e-test-user"
        const val TEST_EMAIL = "e2e-test@example.com"
    }

    @Bean
    @Primary
    @Order(1)
    fun e2eFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            // Permit ALL requests - no authentication required at filter chain level
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }
            // Disable OAuth2 login redirect
            .oauth2Login { it.disable() }
            // Disable OAuth2 resource server (JWT validation)
            .oauth2ResourceServer { it.disable() }
            // Add filter to set mock user for all requests
            .addFilterBefore(
                E2EMockUserFilter(),
                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter::class.java
            )

        return http.build()
    }

    @Bean
    @Primary
    fun e2eMockJwtDecoder(): JwtDecoder {
        return JwtDecoder { token ->
            Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject(TEST_USER_ID)
                .claim("preferred_username", TEST_USERNAME)
                .claim("email", TEST_EMAIL)
                .build()
        }
    }
}

/**
 * Filter that sets up a mock OidcUser for ALL requests.
 * This mimics a logged-in user without requiring actual OAuth flow.
 */
class E2EMockUserFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // Always set mock user authentication
        val mockUser = createMockOidcUser()
        
        val auth = PreAuthenticatedAuthenticationToken(
            mockUser,
            null,
            mockUser.authorities
        )
        SecurityContextHolder.getContext().authentication = auth
        
        filterChain.doFilter(request, response)
    }

    private fun createMockOidcUser(): OidcUser {
        val authorities = listOf(
            SimpleGrantedAuthority("ROLE_USER"),
            SimpleGrantedAuthority("ROLE_OWNER")
        )
        
        val idToken = OidcIdToken.withTokenValue("mock-id-token")
            .subject(E2ESecurityConfig.TEST_USER_ID)
            .claim("preferred_username", E2ESecurityConfig.TEST_USERNAME)
            .claim("email", E2ESecurityConfig.TEST_EMAIL)
            .claim("name", "E2E Test User")
            .claim("realm_access", mapOf("roles" to listOf("user", "owner")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
        
        return DefaultOidcUser(authorities, idToken)
    }
}
