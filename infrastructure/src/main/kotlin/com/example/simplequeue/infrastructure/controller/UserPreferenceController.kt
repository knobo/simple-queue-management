package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.domain.model.UserPreference
import com.example.simplequeue.domain.port.UserPreferenceRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.*

/**
 * REST controller for managing user preferences like language settings.
 */
@RestController
@RequestMapping("/api/preferences")
class UserPreferenceController(
    private val userPreferenceRepository: UserPreferenceRepository,
) {
    private val logger = LoggerFactory.getLogger(UserPreferenceController::class.java)

    /**
     * Get current user's preferences.
     */
    @GetMapping
    fun getPreferences(
        @AuthenticationPrincipal user: OAuth2User?,
    ): ResponseEntity<UserPreferenceResponse> {
        if (user == null) {
            return ResponseEntity.status(401).build()
        }

        val userId = user.name
        val pref = userPreferenceRepository.findByUserId(userId)
        
        return ResponseEntity.ok(UserPreferenceResponse(
            preferredLanguage = pref?.preferredLanguage,
        ))
    }

    /**
     * Update user's language preference.
     * Called via AJAX when user clicks a language flag.
     */
    @PostMapping("/language")
    fun setLanguage(
        @AuthenticationPrincipal user: OAuth2User?,
        @RequestBody request: SetLanguageRequest,
    ): ResponseEntity<Map<String, Any>> {
        if (user == null) {
            return ResponseEntity.status(401).body(mapOf("error" to "Not authenticated"))
        }

        val userId = user.name
        val language = request.language

        // Validate language code
        if (language != null && language !in UserPreference.SUPPORTED_LANGUAGES) {
            return ResponseEntity.badRequest().body(mapOf(
                "error" to "Unsupported language",
                "supported" to UserPreference.SUPPORTED_LANGUAGES,
            ))
        }

        // Get or create preference
        val existingPref = userPreferenceRepository.findByUserId(userId)
        val updatedPref = if (existingPref != null) {
            existingPref.withLanguage(language)
        } else {
            UserPreference.create(userId, language)
        }

        userPreferenceRepository.save(updatedPref)
        logger.info("Updated language preference for user $userId to: $language")

        return ResponseEntity.ok(mapOf(
            "success" to true,
            "language" to (language ?: "default"),
        ))
    }

    data class UserPreferenceResponse(
        val preferredLanguage: String?,
    )

    data class SetLanguageRequest(
        val language: String?,
    )
}
