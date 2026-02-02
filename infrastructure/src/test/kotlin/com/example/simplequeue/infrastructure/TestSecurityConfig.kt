package com.example.simplequeue.infrastructure

import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.QueueMember
import com.example.simplequeue.domain.model.UserPreference
import com.example.simplequeue.domain.port.QueueMemberRepository
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.UserPreferenceRepository
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.util.UUID

@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
class TestSecurityConfig {

    /**
     * Stub implementation of QueueRepository for tests.
     * Queue functionality was moved to a separate service, but SubscriptionService
     * still has a dependency on this for backward compatibility.
     */
    @Bean
    @Primary
    fun testQueueRepository(): QueueRepository = object : QueueRepository {
        override fun save(queue: Queue) {}
        override fun findById(id: UUID): Queue? = null
        override fun findByName(name: String): Queue? = null
        override fun findByOwnerId(ownerId: String): List<Queue> = emptyList()
        override fun delete(id: UUID) {}
        override fun findQueuesNeedingTokenRotation(): List<Queue> = emptyList()
    }

    /**
     * Stub implementation of QueueMemberRepository for tests.
     * Queue functionality was moved to a separate service, but SubscriptionService
     * still has a dependency on this for backward compatibility.
     */
    @Bean
    @Primary
    fun testQueueMemberRepository(): QueueMemberRepository = object : QueueMemberRepository {
        override fun save(member: QueueMember) {}
        override fun findById(id: UUID): QueueMember? = null
        override fun findByQueueId(queueId: UUID): List<QueueMember> = emptyList()
        override fun findByUserId(userId: String): List<QueueMember> = emptyList()
        override fun findByQueueIdAndUserId(queueId: UUID, userId: String): QueueMember? = null
        override fun delete(id: UUID) {}
        override fun countByQueueId(queueId: UUID): Int = 0
    }

    @Bean
    @Primary
    fun testFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/", "/signup", "/public/**", "/api/public/**", "/q/**", "/css/**", "/js/**", "/images/**").permitAll()
                auth.anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter { jwtObj ->
                        val realmAccess = jwtObj.claims["realm_access"] as? Map<String, Any>
                        val roles = realmAccess?.get("roles") as? List<String> ?: emptyList()
                        val authorities = roles.map { role ->
                            SimpleGrantedAuthority("ROLE_${role.uppercase()}")
                        }
                        JwtAuthenticationToken(jwtObj, authorities)
                    }
                }
            }
        return http.build()
    }

    @Bean
    @Primary
    fun mockJwtDecoder(): JwtDecoder {
        return JwtDecoder { token ->
            // This is a dummy decoder that returns a token if needed, 
            // but mostly we use .with(jwt()) in tests which bypasses this.
            // However, it prevents app startup failure.
            Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("mock-user")
                .build()
        }
    }
}
