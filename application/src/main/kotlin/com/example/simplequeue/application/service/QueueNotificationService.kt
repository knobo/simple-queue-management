package com.example.simplequeue.application.service

import com.example.simplequeue.domain.model.Notification
import com.example.simplequeue.domain.model.Notification.Priority
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.NotificationPort
import com.example.simplequeue.domain.port.TicketRepository
import java.util.UUID
import kotlin.math.roundToLong

class QueueNotificationService(
    private val notificationPort: NotificationPort,
    private val ticketRepository: TicketRepository
) {
    companion object {
        const val MAX_POSITION_NOTIFICATIONS = 5
    }

    fun notifyTicketIssued(ticket: Ticket, position: Int, estimatedWaitMinutes: Long, queue: Queue) {
        val notification = Notification(
            topic = ticket.ntfyTopic,
            title = "Ticket Received - ${ticket.code}",
            message = "You are #$position in line at ${queue.name}. Estimated wait: ~$estimatedWaitMinutes minutes.",
            priority = Priority.DEFAULT,
            tags = listOf("ticket", "white_check_mark")
        )
        notificationPort.notify(notification)
    }

    fun notifyTicketCalled(ticket: Ticket, queue: Queue) {
        val notification = Notification(
            topic = ticket.ntfyTopic,
            title = "It's Your Turn!",
            message = "Ticket ${ticket.code} - Please proceed to the counter now.",
            priority = Priority.URGENT,
            tags = listOf("bell", "rotating_light")
        )
        notificationPort.notify(notification)
    }

    fun notifyPositionUpdate(ticket: Ticket, position: Int, estimatedWaitMinutes: Long, queue: Queue) {
        val (priority, tags) = when {
            position == 1 -> Priority.HIGH to listOf("one", "hourglass")
            position <= 3 -> Priority.DEFAULT to listOf("three", "hourglass_flowing_sand")
            else -> Priority.LOW to listOf("hourglass_flowing_sand")
        }

        val message = buildPositionMessage(position, estimatedWaitMinutes)

        val notification = Notification(
            topic = ticket.ntfyTopic,
            title = "Queue Update - Position #$position",
            message = message,
            priority = priority,
            tags = tags
        )
        notificationPort.notify(notification)
    }

    fun notifyAllWaitingTickets(queueId: UUID, queue: Queue) {
        val waitingTickets = ticketRepository.findByQueueIdAndStatus(queueId, Ticket.TicketStatus.WAITING)
        val avgProcessingSeconds = ticketRepository.getAverageProcessingTimeSeconds(queueId)

        waitingTickets.forEachIndexed { index, ticket ->
            val position = index + 1
            val estimatedWaitMinutes = ((position) * avgProcessingSeconds / 60.0).roundToLong().coerceAtLeast(1)

            if (position <= MAX_POSITION_NOTIFICATIONS) {
                notifyPositionUpdate(ticket, position, estimatedWaitMinutes, queue)
            }
        }
    }

    fun notifyTicketCompleted(ticket: Ticket, queue: Queue) {
        val notification = Notification(
            topic = ticket.ntfyTopic,
            title = "Service Complete",
            message = "Thank you for visiting ${queue.name}!",
            priority = Priority.LOW,
            tags = listOf("white_check_mark", "wave")
        )
        notificationPort.notify(notification)
    }

    fun notifyTicketCancelled(ticket: Ticket, queue: Queue) {
        val notification = Notification(
            topic = ticket.ntfyTopic,
            title = "Ticket Cancelled",
            message = "Your ticket ${ticket.code} at ${queue.name} has been cancelled.",
            priority = Priority.DEFAULT,
            tags = listOf("x")
        )
        notificationPort.notify(notification)
    }

    fun notifyQueueTopic(queueId: UUID, message: String) {
        notificationPort.notify("simple-queue-$queueId", message)
    }

    /**
     * Notify display screens that the QR code token has been rotated
     */
    fun notifyTokenRotated(queueId: UUID) {
        val notification = Notification(
            topic = "queue-$queueId",
            title = "Token Rotated",
            message = """{"event":"token_rotated"}""",
            priority = Priority.LOW,
            tags = listOf("arrows_counterclockwise")
        )
        notificationPort.notify(notification)
    }

    /**
     * Notify display screens that the queue status has changed
     */
    fun notifyQueueStatusChanged(queueId: UUID, isOpen: Boolean) {
        val notification = Notification(
            topic = "queue-$queueId",
            title = if (isOpen) "Queue Opened" else "Queue Closed",
            message = """{"event":"queue_status_changed","open":$isOpen}""",
            priority = Priority.DEFAULT,
            tags = if (isOpen) listOf("unlock") else listOf("lock")
        )
        notificationPort.notify(notification)
    }

    /**
     * Notify display screens that a new ticket has been created
     */
    fun notifyTicketCreatedForDisplay(queueId: UUID, ticketCode: String) {
        val notification = Notification(
            topic = "queue-$queueId",
            title = "New Ticket",
            message = """{"event":"ticket_created","code":"$ticketCode"}""",
            priority = Priority.LOW,
            tags = listOf("ticket")
        )
        notificationPort.notify(notification)
    }

    private fun buildPositionMessage(position: Int, waitMinutes: Long): String {
        return when (position) {
            1 -> "You're next! Estimated wait: ~$waitMinutes minute${if (waitMinutes != 1L) "s" else ""}."
            2 -> "Almost there! Just 1 person ahead. Estimated wait: ~$waitMinutes minutes."
            else -> "You are #$position in line. Estimated wait: ~$waitMinutes minutes."
        }
    }
}
