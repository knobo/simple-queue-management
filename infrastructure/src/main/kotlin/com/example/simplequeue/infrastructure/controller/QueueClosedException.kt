package com.example.simplequeue.infrastructure.controller

/**
 * Exception thrown when attempting to access a closed queue.
 */
class QueueClosedException(message: String) : RuntimeException(message)
