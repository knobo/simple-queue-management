package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.domain.model.AccessTokenMode
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.infrastructure.service.AccessTokenService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Public API controller for kiosk/QR code display.
 * Provides endpoints that don't require authentication for displaying
 * current QR codes on public screens.
 */
@RestController
@RequestMapping("/api/public/queues/{queueId}")
class PublicQrCodeController(
    private val queueRepository: QueueRepository,
    private val accessTokenService: AccessTokenService,
) {
    private val logger = LoggerFactory.getLogger(PublicQrCodeController::class.java)

    /**
     * Get current QR code data for a queue.
     * This endpoint is public and used by kiosk displays to show
     * the current valid QR code. The display token in the URL
     * provides a layer of security (obscurity) since these URLs
     * are meant to be displayed on screens.
     */
    @GetMapping("/qr-code")
    fun getCurrentQrCode(
        @PathVariable queueId: UUID,
    ): ResponseEntity<QrCodeResponse> {
        val queue = queueRepository.findById(queueId)
            ?: return ResponseEntity.notFound().build()

        // Only return QR code if queue is open
        if (!queue.open) {
            return ResponseEntity.ok(QrCodeResponse(
                queueId = queueId,
                queueName = queue.name,
                isOpen = false,
                token = null,
                joinUrl = null,
                mode = queue.accessTokenMode.name.lowercase(),
                secondsUntilExpiry = null,
            ))
        }

        val tokenInfo = accessTokenService.getTokenInfo(queueId)
            ?: return ResponseEntity.notFound().build()

        // Build join URL based on token mode
        val joinUrl = if (tokenInfo.isLegacy) {
            "/public/q/$queueId/join?secret=${tokenInfo.token}"
        } else {
            "/q/${tokenInfo.token}"
        }

        return ResponseEntity.ok(QrCodeResponse(
            queueId = queueId,
            queueName = queue.name,
            isOpen = true,
            token = tokenInfo.token,
            joinUrl = joinUrl,
            mode = tokenInfo.mode,
            secondsUntilExpiry = tokenInfo.secondsUntilExpiry,
            secondsUntilRotation = tokenInfo.secondsUntilRotation,
        ))
    }

    /**
     * Get QR code HTML fragment for HTMX polling.
     * Returns just the QR code image and related info for easy polling updates.
     */
    @GetMapping("/qr-code-fragment")
    fun getQrCodeFragment(
        @PathVariable queueId: UUID,
    ): ResponseEntity<String> {
        val queue = queueRepository.findById(queueId)
            ?: return ResponseEntity.notFound().build()

        if (!queue.open) {
            return ResponseEntity.ok("""
                <div class="qr-closed">
                    <h2>Queue is Closed</h2>
                    <p>Please wait for the queue to open.</p>
                </div>
            """.trimIndent())
        }

        val tokenInfo = accessTokenService.getTokenInfo(queueId)
            ?: return ResponseEntity.notFound().build()

        // Build the full URL for the QR code
        val baseUrl = "http://localhost:8080" // This will be replaced by the frontend
        val joinPath = if (tokenInfo.isLegacy) {
            "/public/q/$queueId/join?secret=${tokenInfo.token}"
        } else {
            "/q/${tokenInfo.token}"
        }

        // Generate QR code using external service
        val qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=$baseUrl$joinPath"

        val expiryInfo = if (tokenInfo.secondsUntilExpiry != null && tokenInfo.secondsUntilExpiry > 0) {
            val minutes = tokenInfo.secondsUntilExpiry / 60
            val seconds = tokenInfo.secondsUntilExpiry % 60
            "Expires in: ${minutes}m ${seconds}s"
        } else {
            ""
        }

        val html = """
            <div class="qr-code-container" id="qr-code-content">
                <img src="$qrCodeUrl" alt="Join Queue QR Code" class="qr-code-image"/>
                <p class="qr-instructions">Scan to join the queue</p>
                ${if (expiryInfo.isNotEmpty()) "<p class='qr-expiry'>$expiryInfo</p>" else ""}
            </div>
        """.trimIndent()

        return ResponseEntity.ok(html)
    }

    data class QrCodeResponse(
        val queueId: UUID,
        val queueName: String,
        val isOpen: Boolean,
        val token: String?,
        val joinUrl: String?,
        val mode: String,
        val secondsUntilExpiry: Long?,
        val secondsUntilRotation: Long? = null,
    )
}
