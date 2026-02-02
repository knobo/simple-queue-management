package com.example.simplequeue.infrastructure.controller

import com.example.simplequeue.application.usecase.FeatureNotAllowedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@ControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    /**
     * Handle FeatureNotAllowedException - returns 403 Forbidden for subscription limit violations.
     */
    @ExceptionHandler(FeatureNotAllowedException::class)
    fun handleFeatureNotAllowed(exception: FeatureNotAllowedException): ResponseEntity<ErrorResponse> {
        logger.info("Feature not allowed: ${exception.message}")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(
                error = "feature_not_allowed",
                message = exception.message ?: "This feature is not available with your current subscription.",
                status = 403
            ))
    }

    /**
     * Handle Spring Security authorization exceptions - returns 403 Forbidden.
     * This catches @PreAuthorize failures and other access denied scenarios.
     */
    @ExceptionHandler(AuthorizationDeniedException::class, AccessDeniedException::class)
    fun handleAccessDenied(exception: Exception): ResponseEntity<ErrorResponse> {
        logger.warn("Access denied: ${exception.message}")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(
                error = "access_denied",
                message = "You don't have permission to access this resource.",
                status = 403
            ))
    }

    /**
     * Handle QueueClosedException - returns a friendly error page when queue is closed.
     */
    @ExceptionHandler(QueueClosedException::class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    fun handleQueueClosed(
        exception: QueueClosedException,
        model: Model,
    ): String {
        logger.info("Queue closed: ${exception.message}")
        model.addAttribute("message", exception.message)
        return "queue-closed"
    }

    @ExceptionHandler(Throwable::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleException(
        exception: Throwable,
        model: Model,
    ): String {
        logger.error("An unexpected error occurred", exception)
        val errorMessage =
            if (exception is IllegalArgumentException || exception is IllegalStateException) {
                exception.message
            } else {
                "An unexpected error occurred."
            }
        model.addAttribute("message", errorMessage)
        model.addAttribute("status", 500)
        return "error"
    }

    data class ErrorResponse(
        val error: String,
        val message: String,
        val status: Int,
    )
}
