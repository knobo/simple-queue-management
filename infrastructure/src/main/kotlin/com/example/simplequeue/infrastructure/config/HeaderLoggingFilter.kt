package com.example.simplequeue.infrastructure.config

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class HeaderLoggingFilter : Filter {
    private val logger = LoggerFactory.getLogger(HeaderLoggingFilter::class.java)

    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse,
        chain: FilterChain,
    ) {
        if (request is HttpServletRequest) {
            val uri = request.requestURI
            val method = request.method
            logger.info(">>> Incoming Request: $method $uri")

            request.headerNames.asSequence().forEach { headerName ->
                val headerValue = request.getHeader(headerName)
                logger.info("    Header: $headerName = $headerValue")
            }
        }
        chain.doFilter(request, response)
    }
}
