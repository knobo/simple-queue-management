package com.example.simplequeue.infrastructure.adapter.email

import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.EmailPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

@Component
@ConditionalOnProperty(name = ["spring.mail.host"], matchIfMissing = false)
class SmtpEmailAdapter(
    private val mailSender: JavaMailSender,
    private val templateEngine: TemplateEngine,
    @Value("\${app.base-url:http://localhost:8080}") private val baseUrl: String,
    @Value("\${MAIL_FROM:noreply@example.com}") private val fromAddress: String,
) : EmailPort {

    private val logger = LoggerFactory.getLogger(SmtpEmailAdapter::class.java)

    override fun sendTicketEmail(to: String, ticket: Ticket, queue: Queue) {
        logger.info("Sending ticket email to {} for ticket {}", to, ticket.code)

        val ticketUrl = "$baseUrl/public/tickets/${ticket.id}"

        val context = Context().apply {
            setVariable("ticket", ticket)
            setVariable("queue", queue)
            setVariable("ticketUrl", ticketUrl)
        }

        val htmlContent = templateEngine.process("ticket-email", context)

        val mimeMessage = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(mimeMessage, true, "UTF-8")

        helper.setFrom(fromAddress)
        helper.setTo(to)
        helper.setSubject("Din billett ${ticket.code} - ${queue.name}")
        helper.setText(htmlContent, true)

        mailSender.send(mimeMessage)
        logger.info("Ticket email sent successfully to {}", to)
    }

    override fun sendInviteEmail(
        to: String,
        inviteToken: String,
        queueName: String,
        inviterName: String,
        role: String,
    ) {
        logger.info("Sending invite email to {} for queue {}", to, queueName)

        val inviteUrl = "$baseUrl/invite/$inviteToken"

        val context = Context().apply {
            setVariable("inviteUrl", inviteUrl)
            setVariable("queueName", queueName)
            setVariable("inviterName", inviterName)
            setVariable("role", role)
        }

        val htmlContent = templateEngine.process("invite-email", context)

        val mimeMessage = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(mimeMessage, true, "UTF-8")

        helper.setFrom(fromAddress)
        helper.setTo(to)
        helper.setSubject("You've been invited to join $queueName")
        helper.setText(htmlContent, true)

        mailSender.send(mimeMessage)
        logger.info("Invite email sent successfully to {}", to)
    }
}
