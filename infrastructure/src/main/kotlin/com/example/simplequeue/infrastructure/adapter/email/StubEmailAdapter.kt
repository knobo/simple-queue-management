package com.example.simplequeue.infrastructure.adapter.email

import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.EmailPort
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component

/**
 * Stub implementation of EmailPort that just logs.
 * Used when no email provider is configured.
 */
@Component
@ConditionalOnMissingBean(SmtpEmailAdapter::class)
class StubEmailAdapter : EmailPort {

    private val logger = LoggerFactory.getLogger(StubEmailAdapter::class.java)

    override fun sendTicketEmail(to: String, ticket: Ticket, queue: Queue) {
        logger.info(
            "STUB: Would send ticket email to {} for ticket {} in queue {}",
            to,
            ticket.code,
            queue.name
        )
    }

    override fun sendInviteEmail(
        to: String,
        inviteToken: String,
        queueName: String,
        inviterName: String,
        role: String,
    ) {
        logger.info(
            "STUB: Would send invite email to {} for queue '{}' from {} as {}. Token: {}",
            to,
            queueName,
            inviterName,
            role,
            inviteToken
        )
    }
}
