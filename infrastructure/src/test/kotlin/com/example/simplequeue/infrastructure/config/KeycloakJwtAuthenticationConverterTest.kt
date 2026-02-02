package com.example.simplequeue.infrastructure.config

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant

class KeycloakJwtAuthenticationConverterTest {

    private val converter = KeycloakJwtAuthenticationConverter()

    @Test
    fun `should extract superadmin role from realm_access`() {
        // Given: A JWT with superadmin role in realm_access
        val jwt = createJwtWithRealmRoles(listOf("superadmin", "default-roles-simplequeue"))

        // When: Converting the JWT
        val authentication = converter.convert(jwt)

        // Then: Should have ROLE_SUPERADMIN authority
        val authorities = authentication.authorities
        assertTrue(
            authorities.contains(SimpleGrantedAuthority("ROLE_SUPERADMIN")),
            "Should contain ROLE_SUPERADMIN. Actual authorities: $authorities"
        )
        assertTrue(
            authorities.contains(SimpleGrantedAuthority("ROLE_superadmin")),
            "Should contain ROLE_superadmin. Actual authorities: $authorities"
        )
    }

    @Test
    fun `should not have superadmin role when not in token`() {
        // Given: A JWT without superadmin role
        val jwt = createJwtWithRealmRoles(listOf("user", "default-roles-simplequeue"))

        // When: Converting the JWT
        val authentication = converter.convert(jwt)

        // Then: Should NOT have ROLE_SUPERADMIN authority
        val authorities = authentication.authorities
        assertFalse(
            authorities.contains(SimpleGrantedAuthority("ROLE_SUPERADMIN")),
            "Should NOT contain ROLE_SUPERADMIN. Actual authorities: $authorities"
        )
        assertFalse(
            authorities.contains(SimpleGrantedAuthority("ROLE_superadmin")),
            "Should NOT contain ROLE_superadmin. Actual authorities: $authorities"
        )
    }

    @Test
    fun `should handle missing realm_access gracefully`() {
        // Given: A JWT without realm_access claim
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject("user-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        // When: Converting the JWT
        val authentication = converter.convert(jwt)

        // Then: Should return empty authorities without error
        assertTrue(authentication.authorities.isEmpty())
    }

    @Test
    fun `should extract roles from resource_access`() {
        // Given: A JWT with roles in resource_access (client roles)
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject("user-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claim("resource_access", mapOf(
                "simple-queue" to mapOf(
                    "roles" to listOf("admin", "editor")
                )
            ))
            .build()

        // When: Converting the JWT
        val authentication = converter.convert(jwt)

        // Then: Should have client roles
        val authorities = authentication.authorities
        assertTrue(authorities.contains(SimpleGrantedAuthority("ROLE_ADMIN")))
        assertTrue(authorities.contains(SimpleGrantedAuthority("ROLE_admin")))
        assertTrue(authorities.contains(SimpleGrantedAuthority("ROLE_EDITOR")))
    }

    private fun createJwtWithRealmRoles(roles: List<String>): Jwt {
        return Jwt.withTokenValue("test-token")
            .header("alg", "RS256")
            .subject("user-123")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claim("realm_access", mapOf("roles" to roles))
            .claim("preferred_username", "testuser")
            .claim("email", "test@example.com")
            .build()
    }
}
