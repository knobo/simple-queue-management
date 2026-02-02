package com.example.simplequeue.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Keycloak Admin Client.
 * Used for programmatic user/role management.
 * 
 * All fields are nullable to allow the service to be disabled when
 * Keycloak admin credentials are not configured (e.g., in tests).
 */
@ConfigurationProperties(prefix = "keycloak.admin")
data class KeycloakAdminConfig(
    val serverUrl: String? = null,
    val realm: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
) {
    /**
     * Returns true if all required fields are configured.
     */
    fun isConfigured(): Boolean =
        !serverUrl.isNullOrBlank() &&
            !realm.isNullOrBlank() &&
            !clientId.isNullOrBlank() &&
            !clientSecret.isNullOrBlank()
}
