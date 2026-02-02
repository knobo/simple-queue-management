package com.example.simplequeue.application.usecase

/**
 * Exception thrown when a feature is not allowed due to subscription tier limitations.
 */
class FeatureNotAllowedException(message: String) : RuntimeException(message)
