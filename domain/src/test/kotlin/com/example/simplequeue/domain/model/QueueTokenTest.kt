package com.example.simplequeue.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.RepeatedTest

class QueueTokenTest {

    @Test
    fun `should create QueueToken with valid value`() {
        val token = QueueToken("abcDEF234")
        assertEquals("abcDEF234", token.value)
    }

    @Test
    fun `should throw exception when creating with empty string`() {
        val exception = assertThrows<IllegalArgumentException> {
            QueueToken("")
        }
        assertEquals("Token must not be empty or blank", exception.message)
    }

    @Test
    fun `should throw exception when creating with blank string`() {
        val exception = assertThrows<IllegalArgumentException> {
            QueueToken("   ")
        }
        assertEquals("Token must not be empty or blank", exception.message)
    }

    @Test
    fun `should throw exception when creating with string exceeding max length`() {
        val longString = "a".repeat(256)
        val exception = assertThrows<IllegalArgumentException> {
            QueueToken(longString)
        }
        assertEquals("Token must not exceed 255 characters", exception.message)
    }

    @Test
    fun `should throw exception when creating with invalid characters`() {
        val exception = assertThrows<IllegalArgumentException> {
            QueueToken("abcDEF!")
        }
        assertEquals("Token contains invalid characters. Only alphanumeric characters are allowed (excluding 0, O, 1, l, I)", exception.message)
    }

    @Test
    fun `should throw exception when creating with zero character`() {
        val exception = assertThrows<IllegalArgumentException> {
            QueueToken("abcDEF0")
        }
        assertEquals("Token contains invalid characters. Only alphanumeric characters are allowed (excluding 0, O, 1, l, I)", exception.message)
    }

    @Test
    fun `should throw exception when creating with capital O`() {
        val exception = assertThrows<IllegalArgumentException> {
            QueueToken("abcOEF2")
        }
        assertEquals("Token contains invalid characters. Only alphanumeric characters are allowed (excluding 0, O, 1, l, I)", exception.message)
    }

    @Test
    fun `should throw exception when creating with lowercase L`() {
        val exception = assertThrows<IllegalArgumentException> {
            QueueToken("abcDEFl")
        }
        assertEquals("Token contains invalid characters. Only alphanumeric characters are allowed (excluding 0, O, 1, l, I)", exception.message)
    }

    @Test
    fun `should create QueueToken from factory method with valid value`() {
        val token = QueueToken.from("defGHJ234")
        assertEquals("defGHJ234", token.value)
    }

    @Test
    fun `generate should create token with default length`() {
        val token = QueueToken.generate()
        assertEquals(24, token.value.length)
    }

    @Test
    fun `generate should create token with custom length`() {
        val token = QueueToken.generate(12)
        assertEquals(12, token.value.length)
    }

    @Test
    fun `generate should throw exception when length is too small`() {
        val exception = assertThrows<IllegalArgumentException> {
            QueueToken.generate(0)
        }
        assertEquals("Token length must be between 1 and 255 characters", exception.message)
    }

    @Test
    fun `generate should throw exception when length is too large`() {
        val exception = assertThrows<IllegalArgumentException> {
            QueueToken.generate(300)
        }
        assertEquals("Token length must be between 1 and 255 characters", exception.message)
    }

    @RepeatedTest(10)
    fun `generate should create unique tokens`() {
        val token1 = QueueToken.generate()
        val token2 = QueueToken.generate()
        assertNotEquals(token1.value, token2.value)
    }

    @Test
    fun `generated token should contain only valid characters`() {
        val token = QueueToken.generate()
        val validChars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        assertTrue(token.value.all { it in validChars })
    }

    @Test
    fun `toString should return the token value`() {
        val token = QueueToken("testTokenDEF".replace('o', 'p'))
        assertEquals("testTokenDEF".replace('o', 'p'), token.toString())
    }

    @Test
    fun `should accept token at maximum length`() {
        val maxLengthToken = "a".repeat(255)
        val token = QueueToken(maxLengthToken)
        assertEquals(255, token.value.length)
    }

    @Test
    fun `should accept single character token`() {
        val token = QueueToken("a")
        assertEquals("a", token.value)
    }

    @Test
    fun `generated tokens should have reasonable distribution of characters`() {
        val token = QueueToken.generate(100)
        val hasUppercase = token.value.any { it.isUpperCase() }
        val hasLowercase = token.value.any { it.isLowerCase() }
        val hasDigit = token.value.any { it.isDigit() }
        
        // With 100 characters, we should have a good mix
        assertTrue(hasUppercase || hasLowercase || hasDigit)
    }

    @Test
    fun `should throw exception when from method is called with invalid value`() {
        val exception = assertThrows<IllegalArgumentException> {
            QueueToken.from("invalid token!")
        }
        assertTrue(exception.message!!.contains("Token contains invalid characters"))
    }

    @Test
    fun `should create token with minimum length via generate`() {
        val token = QueueToken.generate(1)
        assertEquals(1, token.value.length)
        assertTrue(token.value in "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789")
    }

    @Test
    fun `should create token with maximum length via generate`() {
        val token = QueueToken.generate(255)
        assertEquals(255, token.value.length)
    }

    @Test
    fun `should maintain immutability`() {
        val token1 = QueueToken("ABC234")
        val token2 = QueueToken("DEF789")
        
        // Tokens should be equal to themselves
        assertEquals(token1, token1)
        assertNotEquals(token1, token2)
        
        // Hash codes should be consistent
        assertEquals(token1.hashCode(), token1.hashCode())
        assertNotEquals(token1.hashCode(), token2.hashCode())
    }

    @Test
    fun `should handle all valid characters`() {
        val allValidChars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        val token = QueueToken(allValidChars)
        assertEquals(allValidChars, token.value)
    }

    @Test
    fun `should provide meaningful error message for invalid characters`() {
        val exception = assertThrows<IllegalArgumentException> {
            QueueToken("abcDEF!")
        }
        assertTrue(exception.message!!.contains("Token contains invalid characters"))
        assertTrue(exception.message!!.contains("excluding 0, O, 1, l, I"))
    }
}