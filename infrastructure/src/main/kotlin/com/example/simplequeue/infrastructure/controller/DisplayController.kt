package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.domain.model.AccessTokenMode
import com.example.simplequeue.domain.model.Counter
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.CounterRepository
import com.example.simplequeue.domain.port.TicketRepository
import com.example.simplequeue.infrastructure.adapter.persistence.JdbcQueueRepository
import com.example.simplequeue.infrastructure.service.AccessTokenService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class DisplayController(
    private val queueRepository: JdbcQueueRepository,
    private val accessTokenService: AccessTokenService,
    private val ticketRepository: TicketRepository,
    private val counterRepository: CounterRepository,
) {
    @GetMapping("/display/{displayToken}")
    fun displayMode(
        @PathVariable displayToken: String,
        model: Model,
    ): String {
        val queue = queueRepository.findByDisplayToken(displayToken)
            ?: return "error/404"

        // Get token info for countdown display
        val tokenInfo = accessTokenService.getTokenInfo(queue.id)

        // Generate join URLs based on token mode
        val (joinUrl, kioskJoinUrl) = if (queue.accessTokenMode == AccessTokenMode.STATIC) {
            // Legacy static mode - now with kiosk support
            val baseJoinUrl = "/public/q/${queue.id}/join?secret=${queue.qrCodeSecret}"
            baseJoinUrl to "$baseJoinUrl&kiosk=true"
        } else {
            // Dynamic token mode - use /q/{token} endpoint
            val token = accessTokenService.getCurrentToken(queue.id)
                ?: accessTokenService.generateNewToken(queue.id)
            val baseJoinUrl = "/q/${token.token}"
            baseJoinUrl to "$baseJoinUrl?kiosk=true"
        }

        model.addAttribute("queue", queue)
        model.addAttribute("joinUrl", joinUrl)
        model.addAttribute("kioskJoinUrl", kioskJoinUrl)
        model.addAttribute("isStaticMode", queue.accessTokenMode == AccessTokenMode.STATIC)
        model.addAttribute("tokenExpiresIn", tokenInfo?.secondsUntilExpiry)
        model.addAttribute("tokenRotatesIn", tokenInfo?.secondsUntilRotation)
        
        return "display"
    }

    /**
     * Kiosk mode display with auto-refreshing QR code.
     * Uses HTMX polling to refresh the QR code every 30 seconds.
     * 
     * This is the recommended display mode for public kiosks as it:
     * - Automatically refreshes the QR code for rotating tokens
     * - Shows a clear "Get Ticket Here" button for customers without QR scanners
     * - Provides real-time status updates via SSE
     */
    @GetMapping("/kiosk/{displayToken}")
    fun kioskMode(
        @PathVariable displayToken: String,
        model: Model,
        request: HttpServletRequest,
    ): String {
        val queue = queueRepository.findByDisplayToken(displayToken)
            ?: return "error/404"

        // Build the full base URL for QR code generation
        val scheme = request.scheme
        val serverName = request.serverName
        val serverPort = request.serverPort
        val baseUrl = when (serverPort) {
            80, 443 -> "$scheme://$serverName"
            else -> "$scheme://$serverName:$serverPort"
        }

        // Generate join URLs based on token mode
        val (joinUrl, kioskJoinUrl) = if (queue.accessTokenMode == AccessTokenMode.STATIC) {
            // Legacy static mode
            val baseJoinUrl = "$baseUrl/public/q/${queue.id}/join?secret=${queue.qrCodeSecret}"
            baseJoinUrl to "$baseUrl/public/q/${queue.id}/join?secret=${queue.qrCodeSecret}&kiosk=true"
        } else {
            // Dynamic token mode - use /q/{token} endpoint
            val token = accessTokenService.getCurrentToken(queue.id)
                ?: accessTokenService.generateNewToken(queue.id)
            val baseJoinUrl = "$baseUrl/q/${token.token}"
            baseJoinUrl to "$baseUrl/q/${token.token}?kiosk=true"
        }

        model.addAttribute("queue", queue)
        model.addAttribute("joinUrl", joinUrl)
        model.addAttribute("kioskJoinUrl", kioskJoinUrl)
        model.addAttribute("baseUrl", baseUrl)
        
        return "kiosk"
    }

    /**
     * Public endpoint to get token status for display pages.
     * Used by JavaScript for countdown timer and QR refresh.
     */
    @GetMapping("/display/{displayToken}/token/status")
    @ResponseBody
    fun getTokenStatus(
        @PathVariable displayToken: String,
    ): ResponseEntity<TokenStatusDTO> {
        val queue = queueRepository.findByDisplayToken(displayToken)
            ?: return ResponseEntity.notFound().build()

        if (queue.accessTokenMode == AccessTokenMode.STATIC) {
            return ResponseEntity.ok(TokenStatusDTO(
                isStatic = true,
                token = queue.qrCodeSecret,
                joinUrl = "/public/q/${queue.id}/join?secret=${queue.qrCodeSecret}",
                kioskJoinUrl = "/public/q/${queue.id}/join?secret=${queue.qrCodeSecret}&kiosk=true",
                secondsUntilExpiry = null,
                secondsUntilRotation = null,
            ))
        }

        val tokenInfo = accessTokenService.getTokenInfo(queue.id)
            ?: return ResponseEntity.notFound().build()

        val joinUrl = "/q/${tokenInfo.token}"
        
        return ResponseEntity.ok(TokenStatusDTO(
            isStatic = false,
            token = tokenInfo.token,
            joinUrl = joinUrl,
            kioskJoinUrl = "$joinUrl?kiosk=true",
            secondsUntilExpiry = tokenInfo.secondsUntilExpiry,
            secondsUntilRotation = tokenInfo.secondsUntilRotation,
        ))
    }

    data class TokenStatusDTO(
        val isStatic: Boolean,
        val token: String,
        val joinUrl: String,
        val kioskJoinUrl: String,
        val secondsUntilExpiry: Long?,
        val secondsUntilRotation: Long?,
    )

    // ==================== Counter Display (Now Serving) ====================

    /**
     * Counter display for service desks.
     * Shows which ticket is currently being served at a specific counter.
     * 
     * Use cases:
     * - TV/monitor at the service desk showing "Now Serving: A-042"
     * - Multiple counters can each have their own display
     * - Auto-updates via SSE when tickets are called
     * 
     * @param displayToken The queue's display token for authentication
     * @param counter Optional counter number to show specific counter (shows all if omitted)
     */
    @GetMapping("/counter/{displayToken}")
    fun counterDisplay(
        @PathVariable displayToken: String,
        @RequestParam(required = false) counter: Int?,
        model: Model,
    ): String {
        val queue = queueRepository.findByDisplayToken(displayToken)
            ?: return "error/404"

        // Get all counters for this queue
        val counters = counterRepository.findByQueueId(queue.id)
        
        // Get next waiting tickets for preview
        val waitingTickets = ticketRepository.findByQueueIdAndStatus(queue.id, Ticket.TicketStatus.WAITING)
        val nextTickets = waitingTickets.take(5)

        // Build counter status list with their current tickets
        val counterStatuses = counters.map { c ->
            val ticket = c.currentTicketId?.let { ticketRepository.findById(it) }
            CounterWithTicket(
                counter = c,
                currentTicket = ticket,
            )
        }

        // If specific counter requested, filter to just that one
        val displayCounters = if (counter != null) {
            counterStatuses.filter { it.counter.number == counter }
        } else {
            counterStatuses
        }

        model.addAttribute("queue", queue)
        model.addAttribute("counters", displayCounters)
        model.addAttribute("singleCounter", counter != null)
        model.addAttribute("counterNumber", counter)
        model.addAttribute("nextTickets", nextTickets)
        model.addAttribute("waitingCount", waitingTickets.size)

        return "counter-display"
    }

    /**
     * API endpoint for counter display status.
     * Used by JavaScript for polling/SSE updates.
     */
    @GetMapping("/counter/{displayToken}/status")
    @ResponseBody
    fun getCounterStatus(
        @PathVariable displayToken: String,
        @RequestParam(required = false) counter: Int?,
    ): ResponseEntity<CounterStatusDTO> {
        val queue = queueRepository.findByDisplayToken(displayToken)
            ?: return ResponseEntity.notFound().build()

        val counters = counterRepository.findByQueueId(queue.id)
        val waitingTickets = ticketRepository.findByQueueIdAndStatus(queue.id, Ticket.TicketStatus.WAITING)
        val nextTickets = waitingTickets.take(5)

        // Build counter statuses
        val counterStatuses = counters.map { c ->
            val ticket = c.currentTicketId?.let { ticketRepository.findById(it) }
            CounterTicketDTO(
                counterNumber = c.number,
                counterName = c.displayName,
                currentTicketCode = ticket?.code,
                currentTicketNumber = ticket?.number,
                operatorId = c.currentOperatorId,
            )
        }

        // Filter if specific counter requested
        val filteredCounters = if (counter != null) {
            counterStatuses.filter { it.counterNumber == counter }
        } else {
            counterStatuses
        }

        return ResponseEntity.ok(CounterStatusDTO(
            queueName = queue.name,
            queueOpen = queue.open,
            counters = filteredCounters,
            nextTicketCodes = nextTickets.map { it.code },
            waitingCount = waitingTickets.size,
        ))
    }

    data class CounterWithTicket(
        val counter: Counter,
        val currentTicket: Ticket?,
    )

    data class CounterTicketDTO(
        val counterNumber: Int,
        val counterName: String,
        val currentTicketCode: String?,
        val currentTicketNumber: Int?,
        val operatorId: String?,
    )

    data class CounterStatusDTO(
        val queueName: String,
        val queueOpen: Boolean,
        val counters: List<CounterTicketDTO>,
        val nextTicketCodes: List<String>,
        val waitingCount: Int,
    )
}
