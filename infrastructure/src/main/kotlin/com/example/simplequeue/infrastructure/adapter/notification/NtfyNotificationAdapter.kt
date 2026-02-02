package com.example.simplequeue.infrastructure.adapter.notification

import com.example.simplequeue.domain.model.Notification
import com.example.simplequeue.domain.port.NotificationPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NtfyNotificationAdapter(private val ntfyClient: NtfyClient) : NotificationPort {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun notify(topic: String, message: String) {
        try {
            ntfyClient.sendNotification(topic, message)
        } catch (e: Exception) {
            logger.error("Failed to send ntfy notification to topic $topic: ${e.message}")
        }
    }

    override fun notify(notification: Notification) {
        try {
            val ntfyMessage = NtfyMessage(
                topic = notification.topic,
                message = notification.message,
                title = notification.title,
                priority = notification.priority.value.takeIf { it != Notification.Priority.DEFAULT.value },
                tags = notification.tags.takeIf { it.isNotEmpty() },
                click = notification.click
            )
            ntfyClient.sendRichNotification(ntfyMessage)
        } catch (e: Exception) {
            logger.error("Failed to send rich ntfy notification to topic ${notification.topic}: ${e.message}")
        }
    }
}
