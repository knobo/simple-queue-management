package com.example.simplequeue.infrastructure

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import java.time.Instant

object TestJwtHelper {
    fun createToken(
        userId: String,
        roles: List<String> = emptyList(),
        claims: Map<String, Any> = emptyMap()
    ): Jwt {
        val now = Instant.now()
        val allClaims = mutableMapOf<String, Any>(
            "preferred_username" to userId,
            "realm_access" to mapOf("roles" to roles)
        )
        allClaims.putAll(claims)

        return Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .subject(userId)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .claims { it.putAll(allClaims) }
            .build()
    }

    /**
     * Create a JWT post-processor with authorities properly set.
     * Use this instead of just jwt().jwt(token) to ensure authorities are populated.
     */
    fun jwtWithRoles(userId: String, roles: List<String>): SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor {
        val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }
        return SecurityMockMvcRequestPostProcessors.jwt()
            .jwt(createToken(userId, roles))
            .authorities(authorities)
    }

    fun superadminJwt() = jwtWithRoles("superadmin", listOf("SUPERADMIN"))
    fun sellerJwt() = jwtWithRoles("seller", listOf("SELLER"))
    fun ownerJwt(id: String = "owner1") = jwtWithRoles(id, listOf("QUEUE_OWNER"))
    fun userJwt(id: String = "user1") = jwtWithRoles(id, emptyList())

    // Keep old methods for backward compatibility
    fun superadminToken() = createToken("superadmin", listOf("SUPERADMIN"))
    fun sellerToken() = createToken("seller", listOf("SELLER"))
    fun ownerToken(id: String = "owner1") = createToken(id, listOf("QUEUE_OWNER"))
    fun userToken(id: String = "user1") = createToken(id)
}
