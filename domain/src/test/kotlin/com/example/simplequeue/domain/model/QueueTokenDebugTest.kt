package com.example.simplequeue.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*

class QueueTokenDebugTest {

    @Test
    fun `debug valid characters`() {
        val validChars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
        println("Valid chars: $validChars")
        println("Length: ${validChars.length}")
        
        // Test each character in "defGHI234"
        val testString = "defGHI234"
        testString.forEach { char ->
            println("Char '$char' in validChars: ${char in validChars}")
        }
    }

    @Test
    fun `test simple valid token`() {
        val token = QueueToken("abc")
        assertEquals("abc", token.value)
    }

    @Test
    fun `test another valid token`() {
        val token = QueueToken("DEF")
        assertEquals("DEF", token.value)
    }

    @Test
    fun `test numbers`() {
        val token = QueueToken("234")
        assertEquals("234", token.value)
    }
}