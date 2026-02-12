package com.example.simplequeue.infrastructure.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute
import java.util.Base64

/**
 * Adds ntfy authentication token to all views so that SSE connections
 * can authenticate with the ntfy server using the ?auth= query parameter.
 */
@ControllerAdvice
class NtfyAuthModelAdvice(
    @Value("\${ntfy.username:}")
    private val ntfyUsername: String,
    @Value("\${ntfy.password:}")
    private val ntfyPassword: String,
    @Value("\${ntfy.server-url:\${ntfy.url:https://ntfy.knobo.no}}")
    private val ntfyServerUrl: String,
) {
    // ntfy ?auth= requires: base64("Basic " + base64("user:pass")) without padding
    // The trailing '=' padding must be stripped or ntfy returns 500
    private val ntfyAuth: String by lazy {
        if (ntfyUsername.isNotBlank() && ntfyPassword.isNotBlank()) {
            val basicCredentials = Base64.getEncoder().encodeToString("$ntfyUsername:$ntfyPassword".toByteArray())
            Base64.getEncoder().encodeToString("Basic $basicCredentials".toByteArray()).trimEnd('=')
        } else {
            ""
        }
    }

    @ModelAttribute("ntfyAuth")
    fun ntfyAuth(): String = ntfyAuth

    @ModelAttribute("ntfyServerUrl")
    fun ntfyServerUrl(): String = ntfyServerUrl
}
