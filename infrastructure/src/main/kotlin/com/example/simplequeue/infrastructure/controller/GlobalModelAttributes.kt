package com.example.simplequeue.infrastructure.controller

import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/**
 * Global controller advice that adds common model attributes for all templates.
 * This ensures the header fragment always has access to user information.
 */
@ControllerAdvice
class GlobalModelAttributes {

    private val logger = LoggerFactory.getLogger(GlobalModelAttributes::class.java)

    /**
     * Adds the current authenticated user to the model for all templates.
     * This allows the header fragment to check authentication status and display
     * user information without each controller having to add it manually.
     */
    @ModelAttribute("user")
    fun addUser(@AuthenticationPrincipal user: OAuth2User?): OAuth2User? {
        return user
    }

    /**
     * Adds a boolean flag indicating if the user is authenticated.
     * Convenient for templates that need a simple boolean check.
     */
    @ModelAttribute("isAuthenticated")
    fun addIsAuthenticated(@AuthenticationPrincipal user: OAuth2User?): Boolean {
        return user != null
    }

    /**
     * Adds application name to the model.
     */
    @ModelAttribute("appName")
    fun addAppName(): String {
        return "Simple Queue"
    }
}
