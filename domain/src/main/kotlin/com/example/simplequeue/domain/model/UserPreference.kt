package com.example.simplequeue.domain.model

import java.time.Instant

/**
 * User preferences for settings like language.
 */
data class UserPreference(
    val userId: String,
    val preferredLanguage: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        val SUPPORTED_LANGUAGES = listOf("en", "no", "de", "es", "fr", "it", "ja", "pl", "pt")
        
        fun create(userId: String, language: String? = null): UserPreference {
            val now = Instant.now()
            return UserPreference(
                userId = userId,
                preferredLanguage = language?.takeIf { it in SUPPORTED_LANGUAGES },
                createdAt = now,
                updatedAt = now,
            )
        }
    }

    fun withLanguage(language: String?): UserPreference {
        val validLanguage = language?.takeIf { it in SUPPORTED_LANGUAGES }
        return copy(
            preferredLanguage = validLanguage,
            updatedAt = Instant.now(),
        )
    }
}
