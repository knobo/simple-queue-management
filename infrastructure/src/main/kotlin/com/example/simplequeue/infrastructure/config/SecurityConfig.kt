package com.example.simplequeue.infrastructure.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("!test")
open class SecurityConfig(
    private val clientRegistrationRepository: ClientRegistrationRepository,
    private val keycloakJwtAuthenticationConverter: KeycloakJwtAuthenticationConverter,
    private val signupAuthorizationRequestResolver: SignupAuthorizationRequestResolver,
    private val keycloakOidcUserService: KeycloakOidcUserService,
    private val loginSuccessHandler: LoginSuccessHandler
) {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(SecurityConfig::class.java)
    }

    @Bean
    open fun filterChain(http: HttpSecurity): SecurityFilterChain {
        logger.info("========== SECURITY CONFIG INITIALIZING ==========")
        
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/", "/signup", "/public/**", "/api/public/**", "/q/**", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error", "/actuator/**").permitAll()
                    // Allow viewing invite info and declining without auth
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/api/invites/{token}"
                    ).permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/api/invites/{token}/decline"
                    ).permitAll()
                    // Stripe webhook - must be accessible without auth (verified by signature)
                    .requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/api/stripe/webhook"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2Login { login ->
                login.loginPage("/oauth2/authorization/keycloak")
                login.authorizationEndpoint { endpoint ->
                    endpoint.authorizationRequestResolver(signupAuthorizationRequestResolver)
                }
                // Use custom OIDC user service to map Keycloak roles to Spring Security authorities
                login.userInfoEndpoint { userInfo ->
                    userInfo.oidcUserService(keycloakOidcUserService)
                }
                // Use custom success handler for role-based redirect
                login.successHandler(loginSuccessHandler)
            }
            .logout { logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutSuccessHandler(oidcLogoutSuccessHandler())
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID")
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)
                }
            }

        logger.info("========== SECURITY CONFIG INITIALIZED ==========")
        return http.build()
    }

    /**
     * OIDC RP-initiated logout handler.
     * Redirects to Keycloak's logout endpoint with:
     * - id_token_hint: identifies the session to logout
     * - post_logout_redirect_uri: where to redirect after logout
     */
    private fun oidcLogoutSuccessHandler(): LogoutSuccessHandler {
        val handler = OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository)
        handler.setPostLogoutRedirectUri("{baseUrl}/")
        return handler
    }
}
