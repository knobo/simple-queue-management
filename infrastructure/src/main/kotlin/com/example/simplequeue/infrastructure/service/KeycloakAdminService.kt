package com.example.simplequeue.infrastructure.service

import com.example.simplequeue.infrastructure.config.KeycloakAdminConfig
import jakarta.annotation.PostConstruct
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.keycloak.representations.idm.RoleRepresentation
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service

/**
 * Service for managing Keycloak users and roles via Admin API.
 * Used to assign SELLER role to users when they become sellers.
 * 
 * If Keycloak admin credentials are not configured, this service will
 * operate in a no-op mode and log warnings instead of performing operations.
 */
@Service
@EnableConfigurationProperties(KeycloakAdminConfig::class)
class KeycloakAdminService(
    private val config: KeycloakAdminConfig,
) {
    companion object {
        val logger: Logger = LoggerFactory.getLogger(KeycloakAdminService::class.java)
        const val SELLER_ROLE = "SELLER"
    }

    private var keycloak: Keycloak? = null
    private var enabled: Boolean = false

    @PostConstruct
    fun init() {
        if (!config.isConfigured()) {
            logger.warn("Keycloak Admin Client not configured - role management will be disabled")
            logger.warn("To enable, set keycloak.admin.server-url, realm, client-id, and client-secret")
            return
        }

        logger.info("Initializing Keycloak Admin Client for server: ${config.serverUrl}, realm: ${config.realm}")
        try {
            keycloak = KeycloakBuilder.builder()
                .serverUrl(config.serverUrl)
                .realm(config.realm)
                .clientId(config.clientId)
                .clientSecret(config.clientSecret)
                .grantType("client_credentials")
                .build()
            enabled = true

            // Ensure SELLER role exists on startup
            createRoleIfNotExists(SELLER_ROLE)
        } catch (e: Exception) {
            logger.error("Failed to initialize Keycloak Admin Client: ${e.message}", e)
            enabled = false
        }
    }

    /**
     * Returns true if the service is enabled and configured.
     */
    fun isEnabled(): Boolean = enabled

    /**
     * Assign a role to a user in Keycloak.
     *
     * @param userId The Keycloak user ID (subject from JWT/OIDC)
     * @param roleName The role name to assign
     * @return true if successful, false otherwise
     */
    fun assignRoleToUser(userId: String, roleName: String): Boolean {
        if (!enabled) {
            logger.warn("Keycloak Admin not enabled - cannot assign role '$roleName' to user '$userId'")
            return false
        }

        return try {
            val kc = keycloak ?: return false
            val realmResource = kc.realm(config.realm)
            val usersResource = realmResource.users()
            val userResource = usersResource.get(userId)

            // Get the role representation
            val role = realmResource.roles().get(roleName).toRepresentation()

            // Assign the role to the user
            userResource.roles().realmLevel().add(listOf(role))

            logger.info("Successfully assigned role '$roleName' to user '$userId'")
            true
        } catch (e: Exception) {
            logger.error("Failed to assign role '$roleName' to user '$userId': ${e.message}", e)
            false
        }
    }

    /**
     * Remove a role from a user in Keycloak.
     *
     * @param userId The Keycloak user ID (subject from JWT/OIDC)
     * @param roleName The role name to remove
     * @return true if successful, false otherwise
     */
    fun removeRoleFromUser(userId: String, roleName: String): Boolean {
        if (!enabled) {
            logger.warn("Keycloak Admin not enabled - cannot remove role '$roleName' from user '$userId'")
            return false
        }

        return try {
            val kc = keycloak ?: return false
            val realmResource = kc.realm(config.realm)
            val usersResource = realmResource.users()
            val userResource = usersResource.get(userId)

            // Get the role representation
            val role = realmResource.roles().get(roleName).toRepresentation()

            // Remove the role from the user
            userResource.roles().realmLevel().remove(listOf(role))

            logger.info("Successfully removed role '$roleName' from user '$userId'")
            true
        } catch (e: Exception) {
            logger.error("Failed to remove role '$roleName' from user '$userId': ${e.message}", e)
            false
        }
    }

    /**
     * Create a role in Keycloak if it doesn't exist.
     *
     * @param roleName The role name to create
     * @return true if role exists or was created, false on error
     */
    fun createRoleIfNotExists(roleName: String): Boolean {
        if (!enabled) {
            logger.warn("Keycloak Admin not enabled - cannot create role '$roleName'")
            return false
        }

        return try {
            val kc = keycloak ?: return false
            val realmResource = kc.realm(config.realm)
            val rolesResource = realmResource.roles()

            // Check if role exists
            val existingRoles = rolesResource.list().map { it.name }
            if (roleName in existingRoles) {
                logger.info("Role '$roleName' already exists in realm '${config.realm}'")
                return true
            }

            // Create the role
            val role = RoleRepresentation().apply {
                name = roleName
                description = "Role for $roleName users"
            }
            rolesResource.create(role)

            logger.info("Successfully created role '$roleName' in realm '${config.realm}'")
            true
        } catch (e: Exception) {
            logger.error("Failed to create role '$roleName': ${e.message}", e)
            false
        }
    }

    /**
     * Check if a user has a specific role.
     *
     * @param userId The Keycloak user ID
     * @param roleName The role name to check
     * @return true if user has the role, false otherwise
     */
    fun userHasRole(userId: String, roleName: String): Boolean {
        if (!enabled) {
            logger.warn("Keycloak Admin not enabled - cannot check role '$roleName' for user '$userId'")
            return false
        }

        return try {
            val kc = keycloak ?: return false
            val realmResource = kc.realm(config.realm)
            val userResource = realmResource.users().get(userId)
            val userRoles = userResource.roles().realmLevel().listEffective()

            userRoles.any { it.name == roleName }
        } catch (e: Exception) {
            logger.error("Failed to check if user '$userId' has role '$roleName': ${e.message}", e)
            false
        }
    }

    /**
     * Search for users in Keycloak by email or username.
     *
     * @param query The search query (email or username)
     * @param maxResults Maximum number of results to return
     * @return List of matching users
     */
    fun searchUsers(query: String, maxResults: Int = 20): List<KeycloakUser> {
        if (!enabled) {
            logger.warn("Keycloak Admin not enabled - cannot search users")
            return emptyList()
        }

        return try {
            val kc = keycloak ?: return emptyList()
            val realmResource = kc.realm(config.realm)
            val usersResource = realmResource.users()

            // Search by email or username
            val users = usersResource.search(query, 0, maxResults)

            users.map { user ->
                KeycloakUser(
                    id = user.id,
                    username = user.username,
                    email = user.email,
                    firstName = user.firstName,
                    lastName = user.lastName,
                    enabled = user.isEnabled,
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to search users with query '$query': ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get user details by ID.
     *
     * @param userId The Keycloak user ID
     * @return User details or null if not found
     */
    fun getUserById(userId: String): KeycloakUser? {
        if (!enabled) {
            logger.warn("Keycloak Admin not enabled - cannot get user '$userId'")
            return null
        }

        return try {
            val kc = keycloak ?: return null
            val realmResource = kc.realm(config.realm)
            val user = realmResource.users().get(userId).toRepresentation()

            KeycloakUser(
                id = user.id,
                username = user.username,
                email = user.email,
                firstName = user.firstName,
                lastName = user.lastName,
                enabled = user.isEnabled,
            )
        } catch (e: Exception) {
            logger.error("Failed to get user '$userId': ${e.message}", e)
            null
        }
    }

    /**
     * Represents a Keycloak user.
     */
    data class KeycloakUser(
        val id: String,
        val username: String?,
        val email: String?,
        val firstName: String?,
        val lastName: String?,
        val enabled: Boolean,
    ) {
        fun displayName(): String =
            when {
                !firstName.isNullOrBlank() && !lastName.isNullOrBlank() -> "$firstName $lastName"
                !firstName.isNullOrBlank() -> firstName
                !username.isNullOrBlank() -> username
                !email.isNullOrBlank() -> email
                else -> id
            }
    }
}
