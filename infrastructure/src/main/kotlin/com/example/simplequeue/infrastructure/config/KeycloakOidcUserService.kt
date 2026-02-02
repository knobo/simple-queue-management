package com.example.simplequeue.infrastructure.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Component

/**
 * Custom OIDC User Service that extracts Keycloak roles from realm_access.roles
 * and maps them to Spring Security authorities.
 * 
 * This is needed because the default OidcUserService only includes SCOPE_* authorities,
 * not Keycloak realm roles. Without this, @PreAuthorize("hasRole('SUPERADMIN')") will fail
 * for web sessions (OAuth2 login) even though the user has the role in Keycloak.
 */
@Component
class KeycloakOidcUserService : OidcUserService() {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(KeycloakOidcUserService::class.java)
    }

    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        // Load user from default service
        val oidcUser = super.loadUser(userRequest)
        
        logger.info("========== KEYCLOAK OIDC USER SERVICE ==========")
        logger.info("Loading user: ${oidcUser.name}")
        
        // Extract Keycloak roles and combine with existing authorities
        val authorities = mutableSetOf<GrantedAuthority>()
        
        // Keep existing authorities (SCOPE_*, OIDC_USER, etc.)
        authorities.addAll(oidcUser.authorities)
        logger.info("Existing authorities: ${oidcUser.authorities.map { it.authority }}")
        
        // Extract realm roles from realm_access claim
        val keycloakRoles = extractKeycloakRoles(oidcUser)
        authorities.addAll(keycloakRoles)
        
        logger.info("Final authorities: ${authorities.map { it.authority }}")
        logger.info("========== KEYCLOAK OIDC USER SERVICE END ==========")
        
        // Create new OidcUser with combined authorities
        return DefaultOidcUser(
            authorities,
            oidcUser.idToken,
            oidcUser.userInfo,
            "sub" // name attribute claim
        )
    }

    private fun extractKeycloakRoles(oidcUser: OidcUser): Set<GrantedAuthority> {
        val roles = mutableSetOf<GrantedAuthority>()
        
        // Try to get realm_access from ID token claims
        @Suppress("UNCHECKED_CAST")
        val realmAccess = oidcUser.claims["realm_access"] as? Map<String, Any>
        
        if (realmAccess != null) {
            @Suppress("UNCHECKED_CAST")
            val realmRoles = realmAccess["roles"] as? List<String> ?: emptyList()
            logger.info("Found realm_access.roles: $realmRoles")
            
            realmRoles.forEach { role ->
                // Add with ROLE_ prefix (Spring Security convention)
                roles.add(SimpleGrantedAuthority("ROLE_${role.uppercase()}"))
                roles.add(SimpleGrantedAuthority("ROLE_$role"))
                logger.info("  Added role: ROLE_${role.uppercase()}")
            }
        } else {
            logger.warn("realm_access NOT FOUND in OIDC claims!")
            logger.warn("Make sure Keycloak is configured to include realm_access in ID token.")
            logger.warn("Available claims: ${oidcUser.claims.keys}")
        }
        
        // Also check resource_access for client-specific roles
        @Suppress("UNCHECKED_CAST")
        val resourceAccess = oidcUser.claims["resource_access"] as? Map<String, Any>
        
        if (resourceAccess != null) {
            resourceAccess.forEach { (clientId, clientAccess) ->
                @Suppress("UNCHECKED_CAST")
                val clientRoles = (clientAccess as? Map<String, Any>)?.get("roles") as? List<String>
                clientRoles?.forEach { role ->
                    roles.add(SimpleGrantedAuthority("ROLE_${role.uppercase()}"))
                    roles.add(SimpleGrantedAuthority("ROLE_$role"))
                    logger.info("  Added client role ($clientId): ROLE_${role.uppercase()}")
                }
            }
        }
        
        return roles
    }
}
