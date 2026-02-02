package com.example.simplequeue.infrastructure.adapter.notification

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange
interface NtfyClient {
    @PostExchange("/{topic}")
    fun sendNotification(
        @PathVariable topic: String,
        @RequestBody message: String,
    )

    @PostExchange(contentType = MediaType.APPLICATION_JSON_VALUE)
    fun sendRichNotification(
        @RequestBody message: NtfyMessage,
    )
}
