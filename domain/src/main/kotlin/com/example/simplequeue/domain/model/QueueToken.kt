package com.example.simplequeue.domain.model

import java.security.SecureRandom

/**
 * Value object representing a queue token.
 * Wraps a String value with validation logic.
 */
data class QueueToken(
    val value: String
) {
    init {
        validateToken(value)
    }

    companion object {
        private const val MIN_LENGTH = 1
        private const val MAX_LENGTH = 255
        private const val TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        private val secureRandom = SecureRandom()

        /**
         * Generates a secure random token of the specified length.
         * Default length is 24 characters.
         * Uses a character set that avoids ambiguous characters (0, O, 1, l, I).
         */
        fun generate(length: Int = 24): QueueToken {
            require(length in MIN_LENGTH..MAX_LENGTH) {
                "Token length must be between $MIN_LENGTH and $MAX_LENGTH characters"
            }
            
            val tokenValue = (1..length)
                .map { TOKEN_CHARS[secureRandom.nextInt(TOKEN_CHARS.length)] }
                .joinToString("")
            
            return QueueToken(tokenValue)
        }

        /**
         * Creates a QueueToken from a string value with validation.
         * This is useful when you already have a token value that needs to be wrapped.
         */
        fun from(value: String): QueueToken {
            return QueueToken(value)
        }

        private fun validateToken(value: String) {
            require(value.isNotBlank()) {
                "Token must not be empty or blank"
            }
            
            require(value.length <= MAX_LENGTH) {
                "Token must not exceed $MAX_LENGTH characters"
            }
            
            require(value.all { it in TOKEN_CHARS }) {
                "Token contains invalid characters. Only alphanumeric characters are allowed (excluding 0, O, 1, l, I)"
            }
        }
    }

    override fun toString(): String = value
}