package com.example.simplequeue.infrastructure

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

@TestConfiguration
@EnableWebSecurity
@EnableMethodSecurity
class TestSecurityConfig {

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
