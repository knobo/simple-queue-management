package com.example.simplequeue.application.usecase

import com.example.simplequeue.application.service.QueueNotificationService
import com.example.simplequeue.application.service.QueueOpeningHoursService
import com.example.simplequeue.domain.model.AccessTokenMode
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.QueueAccessTokenRepository
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.QueueStateRepository
import com.example.simplequeue.domain.port.TicketRepository
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToLong

class IssueTicketUseCase(
    private val queueRepository: QueueRepository,
    private val ticketRepository: TicketRepository,
    private val queueStateRepository: QueueStateRepository,
    private val queueNotificationService: QueueNotificationService,
    private val accessTokenRepository: QueueAccessTokenRepository? = null,
    private val queueOpeningHoursService: QueueOpeningHoursService? = null,
) {
    /**
     * Issue a ticket using legacy qr_code_secret validation.
     * For backwards compatibility with existing QR codes.
     */
    fun execute(
        queueId: UUID,
        qrSecret: String,
        name: String? = null,
        email: String? = null,
    ): Ticket {
        val queue =
            queueRepository.findById(queueId)
                ?: throw IllegalArgumentException("Queue not found")

        if (!queue.open) {
            throw IllegalStateException("Queue is closed")
        }

        // For STATIC mode, validate qr_code_secret directly
        if (queue.accessTokenMode == AccessTokenMode.STATIC) {
            if (queue.qrCodeSecret != qrSecret) {
                throw IllegalArgumentException("Invalid or expired QR code")
            }

            if (queue.qrCodeType == Queue.QrCodeType.SINGLE_USE) {
                queue.rotateQrCode()
                queueRepository.save(queue)
            }
        } else {
            // For dynamic modes, the secret should be the access token
            // Validate via token repository
            throw IllegalArgumentException("This queue uses dynamic tokens. Use /q/{token} endpoint.")
        }

        return issueTicketInternal(queue, name, email)
    }

    /**
     * Issue a ticket using dynamic access token validation.
     * Used by /q/{token} endpoint.
     */
    fun executeWithToken(
        queue: Queue,
        name: String? = null,
        email: String? = null,
    ): Ticket {
        if (!queue.open) {
            throw IllegalStateException("Queue is closed")
        }

        // Token validation is done by AccessTokenService before calling this
        return issueTicketInternal(queue, name, email)
    }

    private fun issueTicketInternal(
        queue: Queue,
        name: String?,
        email: String?,
    ): Ticket {
        val queueId = queue.id
        
        // Check opening hours if service is available
        queueOpeningHoursService?.let { service ->
            if (!service.isQueueOpenNow(queueId)) {
                val nextOpening = service.getNextOpeningTime(queueId)
                val message = if (nextOpening != null) {
                    "Queue is currently closed. Opens at $nextOpening"
                } else {
                    "Queue is currently closed"
                }
                throw IllegalStateException(message)
            }
        }
        
        val number = ticketRepository.getNextNumber(queueId)
        val ticket = Ticket.issue(queueId, number, name, email)

        // Assign default state
        val waitingStates = queueStateRepository.findByQueueIdAndStatus(queueId, Ticket.TicketStatus.WAITING)
        if (waitingStates.isNotEmpty()) {
            ticket.stateId = waitingStates.first().id
        }

        ticketRepository.save(ticket)

        // Calculate position and estimated wait time
        val waitingTickets = ticketRepository.findByQueueIdAndStatus(queueId, Ticket.TicketStatus.WAITING)
        val position = waitingTickets.indexOfFirst { it.id == ticket.id } + 1
        val avgSeconds = ticketRepository.getAverageProcessingTimeSeconds(queueId)
        val estimatedWaitMinutes = ((position) * avgSeconds / 60.0).roundToLong().coerceAtLeast(1)

        // Send rich notification to the new ticket holder
        queueNotificationService.notifyTicketIssued(ticket, position, estimatedWaitMinutes, queue)

        // Notify queue topic for dashboard
        queueNotificationService.notifyQueueTopic(queueId, "New ticket: ${ticket.number}")
        
        // Notify display screens
        queueNotificationService.notifyTicketCreatedForDisplay(queueId, ticket.code)

        return ticket
    }
}
