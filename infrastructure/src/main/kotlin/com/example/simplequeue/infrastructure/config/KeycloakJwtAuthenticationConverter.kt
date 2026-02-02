package com.example.simplequeue.infrastructure.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Converts Keycloak JWT tokens to Spring Security authentication tokens,
 * extracting roles from realm_access.roles and resource_access claims.
 * 
 * NOTE: This converter is used for API calls with Bearer JWT tokens.
 * For web sessions (OAuth2 login), the OAuth2User attributes are used instead.
 */
@Component
class KeycloakJwtAuthenticationConverter : Converter<Jwt, AbstractAuthenticationToken> {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(KeycloakJwtAuthenticationConverter::class.java)
    }

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        logger.info("========== JWT CONVERTER DEBUG START ==========")
        logger.info("Converting JWT for subject: ${jwt.subject}")
        
        // Log ALL JWT claims
        logger.info("--- ALL JWT CLAIMS ---")
        jwt.claims.forEach { (key, value) ->
            logger.info("  JWT_CLAIM [$key] = $value (type: ${value?.javaClass?.name})")
        }
        
        val authorities = extractAuthorities(jwt)
        
        logger.info("--- FINAL AUTHORITIES ---")
        authorities.forEach { auth ->
            logger.info("  AUTHORITY: ${auth.authority}")
        }
        
        logger.info("========== JWT CONVERTER DEBUG END ==========")
        return JwtAuthenticationToken(jwt, authorities, jwt.subject)
    }

    private fun extractAuthorities(jwt: Jwt): Collection<GrantedAuthority> {
        val authorities = mutableSetOf<GrantedAuthority>()
        
        logger.info("--- EXTRACTING AUTHORITIES FROM JWT ---")

        // Extract realm roles from realm_access.roles
        logger.info("Checking realm_access claim...")
        @Suppress("UNCHECKED_CAST")
        val realmAccess = jwt.getClaim<Map<String, Any>>("realm_access")
        logger.info("  realm_access: $realmAccess")
        
        if (realmAccess != null) {
            @Suppress("UNCHECKED_CAST")
            val realmRoles = realmAccess["roles"] as? List<String> ?: emptyList()
            logger.info("  realm roles: $realmRoles")
            
            realmRoles.forEach { role ->
                // Add both with and without ROLE_ prefix for flexibility
                authorities.add(SimpleGrantedAuthority("ROLE_${role.uppercase()}"))
                authorities.add(SimpleGrantedAuthority("ROLE_$role"))
                logger.info("    Added: ROLE_${role.uppercase()}, ROLE_$role")
            }
        } else {
            logger.warn("  realm_access is NULL in JWT!")
        }

        // Also extract resource/client roles if needed
        logger.info("Checking resource_access claim...")
        @Suppress("UNCHECKED_CAST")
        val resourceAccess = jwt.getClaim<Map<String, Any>>("resource_access")
        logger.info("  resource_access: $resourceAccess")
        
        if (resourceAccess != null) {
            resourceAccess.forEach { (clientId, clientAccess) ->
                logger.info("  Processing client: $clientId")
                @Suppress("UNCHECKED_CAST")
                val clientRoles = (clientAccess as? Map<String, Any>)?.get("roles") as? List<String>
                logger.info("    client roles: $clientRoles")
                clientRoles?.forEach { role ->
                    authorities.add(SimpleGrantedAuthority("ROLE_${role.uppercase()}"))
                    authorities.add(SimpleGrantedAuthority("ROLE_$role"))
                    logger.info("      Added: ROLE_${role.uppercase()}, ROLE_$role")
                }
            }
        }
        
        logger.info("Total authorities extracted: ${authorities.size}")

        return authorities
    }
}
