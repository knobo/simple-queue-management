package com.example.simplequeue.infrastructure

import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.EmailPort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestEmailConfig {

    @Bean
    @Primary
    fun testEmailPort(): EmailPort = object : EmailPort {
        override fun sendTicketEmail(to: String, ticket: Ticket, queue: Queue) {
            // No-op for tests
            println("TEST: Would send email to $to for ticket ${ticket.code}")
        }

        override fun sendInviteEmail(
            to: String,
            inviteToken: String,
            queueName: String,
            inviterName: String,
            role: String,
        ) {
            // No-op for tests
            println("TEST: Would send invite email to $to for queue $queueName as $role")
        }
    }
}
