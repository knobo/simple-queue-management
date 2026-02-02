package com.example.simplequeue.infrastructure.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Component

/**
 * Custom authorization request resolver that adds support for Keycloak's prompt=create parameter.
 * 
 * When a user visits /signup, they are redirected to /oauth2/authorization/keycloak?prompt=create.
 * This resolver intercepts that request and forwards the prompt parameter to Keycloak,
 * which triggers the registration form instead of the login form.
 * 
 * See: https://www.keycloak.org/docs/latest/securing_apps/#_params_forwarding
 */
@Component
@Profile("!test")
class SignupAuthorizationRequestResolver(
    clientRegistrationRepository: ClientRegistrationRepository
) : OAuth2AuthorizationRequestResolver {

    private val defaultResolver = DefaultOAuth2AuthorizationRequestResolver(
        clientRegistrationRepository,
        "/oauth2/authorization"
    ).apply {
        setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce())
    }

    override fun resolve(request: HttpServletRequest): OAuth2AuthorizationRequest? {
        val authorizationRequest = defaultResolver.resolve(request) ?: return null
        return customizeAuthorizationRequest(request, authorizationRequest)
    }

    override fun resolve(request: HttpServletRequest, clientRegistrationId: String): OAuth2AuthorizationRequest? {
        val authorizationRequest = defaultResolver.resolve(request, clientRegistrationId) ?: return null
        return customizeAuthorizationRequest(request, authorizationRequest)
    }

    private fun customizeAuthorizationRequest(
        request: HttpServletRequest,
        authorizationRequest: OAuth2AuthorizationRequest
    ): OAuth2AuthorizationRequest {
        val prompt = request.getParameter("prompt")
        
        // If prompt parameter is present (e.g., prompt=create), add it to the authorization request
        if (prompt != null) {
            val additionalParameters = authorizationRequest.additionalParameters.toMutableMap()
            additionalParameters["prompt"] = prompt
            
            return OAuth2AuthorizationRequest.from(authorizationRequest)
                .additionalParameters(additionalParameters)
                .build()
        }
        
        return authorizationRequest
    }
}
