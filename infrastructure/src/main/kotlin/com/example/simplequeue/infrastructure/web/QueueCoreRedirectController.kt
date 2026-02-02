package com.example.simplequeue.infrastructure.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

/**
 * Redirects queue-related requests to the Queue Core service.
 * These endpoints belong to Queue Core, not Management.
 */
@Controller
class QueueCoreRedirectController(
    @Value("\${queue-core.base-url:https://queue.knobo.no}")
    private val queueCoreBaseUrl: String
) {
    @GetMapping("/dashboard")
    fun redirectDashboard(): String {
        return "redirect:$queueCoreBaseUrl/dashboard"
    }
    
    @GetMapping("/queue/{queueId}/**")
    fun redirectQueuePages(@PathVariable queueId: String): String {
        return "redirect:$queueCoreBaseUrl/queue/$queueId"
    }
    
    @GetMapping("/q/**")
    fun redirectPublicQueue(): String {
        return "redirect:$queueCoreBaseUrl/q"
    }
}
