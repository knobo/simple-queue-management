package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.usecase.GetQueueStatusUseCase
import com.example.simplequeue.application.usecase.IssueTicketUseCase
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.TicketRepository
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/public")
class PublicQueueController(
    private val issueTicketUseCase: IssueTicketUseCase,
    private val getQueueStatusUseCase: GetQueueStatusUseCase,
    private val queueRepository: QueueRepository,
    private val ticketRepository: TicketRepository,
) {
    private val emitters = ConcurrentHashMap<UUID, SseEmitter>()
    private val executor = Executors.newSingleThreadScheduledExecutor()

    companion object {
        val logger = org.slf4j.LoggerFactory.getLogger(PublicQueueController::class.java)
    }

    init {
        // Simple polling for SSE updates in this MVP
        executor.scheduleAtFixedRate(this::broadcastUpdates, 5, 5, TimeUnit.SECONDS)
    }

    @GetMapping("/queues/{queueId}")
    fun getQueue(
        @PathVariable queueId: UUID,
    ): Queue =
        queueRepository.findById(queueId)
            ?: throw IllegalArgumentException("Queue not found")

    /**
     * Get ticket status by ID.
     * Used by landing page to validate stored tickets.
     */
    @GetMapping("/tickets/{ticketId}")
    fun getTicketStatus(
        @PathVariable ticketId: UUID,
    ): ResponseEntity<TicketStatusResponse> {
        val ticket = ticketRepository.findById(ticketId)
            ?: return ResponseEntity.notFound().build()

        val queue = queueRepository.findById(ticket.queueId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            TicketStatusResponse(
                ticketId = ticket.id,
                queueId = ticket.queueId,
                queueName = queue.name,
                code = ticket.code,
                number = ticket.number,
                status = ticket.status.name,
                isActive = ticket.status == Ticket.TicketStatus.WAITING || ticket.status == Ticket.TicketStatus.CALLED,
            ),
        )
    }

    data class TicketStatusResponse(
        val ticketId: UUID,
        val queueId: UUID,
        val queueName: String,
        val code: String,
        val number: Int,
        val status: String,
        val isActive: Boolean,
    )

    @PostMapping("/queues/{queueId}/tickets")
    fun issueTicket(
        @PathVariable queueId: UUID,
        @RequestParam secret: String,
    ): Ticket = issueTicketUseCase.execute(queueId, secret)

    @GetMapping(value = ["/tickets/{queueId}/{number}/events"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribeToTicket(
        @PathVariable queueId: UUID,
        @PathVariable number: Int,
    ): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        val emitterId = UUID.randomUUID()
        emitters[emitterId] = emitter

        emitter.onCompletion { emitters.remove(emitterId) }
        emitter.onTimeout { emitters.remove(emitterId) }

        try {
            emitter.send(getQueueStatusUseCase.execute(queueId, number))
        } catch (e: Exception) {
            logger.error("Error sending initial event", e)
            emitters.remove(emitterId)
        }

        return emitter
    }

    private fun broadcastUpdates() {
        emitters.forEach { (id, emitter) ->
            try {
                emitter.send(SseEmitter.event().name("ping").data("alive"))
            } catch (e: Exception) {
                emitters.remove(id)
            }
        }
    }
}
