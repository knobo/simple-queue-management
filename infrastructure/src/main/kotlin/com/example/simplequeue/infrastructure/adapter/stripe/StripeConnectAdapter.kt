package com.example.simplequeue.infrastructure.adapter.stripe

import com.example.simplequeue.domain.port.ConnectWebhookResult
import com.example.simplequeue.domain.port.SellerPaymentGateway
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Account
import com.stripe.model.Event
import com.stripe.model.Transfer
import com.stripe.net.Webhook
import com.stripe.param.AccountCreateParams
import com.stripe.param.AccountLinkCreateParams
import com.stripe.param.LoginLinkCreateOnAccountParams
import com.stripe.param.TransferCreateParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class StripeConnectAdapter(
    private val connectConfig: StripeConnectProperties,
) : SellerPaymentGateway {

    private val logger = LoggerFactory.getLogger(StripeConnectAdapter::class.java)

    override fun createExpressAccount(email: String, country: String): String {
        val params = AccountCreateParams.builder()
            .setType(AccountCreateParams.Type.EXPRESS)
            .setCountry(country)
            .setEmail(email)
            .setCapabilities(
                AccountCreateParams.Capabilities.builder()
                    .setCardPayments(
                        AccountCreateParams.Capabilities.CardPayments.builder()
                            .setRequested(true)
                            .build()
                    )
                    .setTransfers(
                        AccountCreateParams.Capabilities.Transfers.builder()
                            .setRequested(true)
                            .build()
                    )
                    .build()
            )
            .build()

        val account = Account.create(params)
        logger.info("Created Stripe Express account {} for email {}", account.id, email)
        return account.id
    }

    override fun createAccountLink(
        stripeAccountId: String,
        refreshUrl: String,
        returnUrl: String,
    ): String {
        val params = AccountLinkCreateParams.builder()
            .setAccount(stripeAccountId)
            .setRefreshUrl(refreshUrl)
            .setReturnUrl(returnUrl)
            .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
            .build()

        val accountLink = com.stripe.model.AccountLink.create(params)
        logger.info("Created account link for Stripe account {}", stripeAccountId)
        return accountLink.url
    }

    override fun createLoginLink(stripeAccountId: String): String {
        val params = LoginLinkCreateOnAccountParams.builder().build()
        val loginLink = com.stripe.model.LoginLink.createOnAccount(stripeAccountId, params)
        logger.info("Created login link for Stripe account {}", stripeAccountId)
        return loginLink.url
    }

    override fun transferToSeller(
        stripeAccountId: String,
        amount: Long,
        currency: String,
        description: String,
    ): String {
        val params = TransferCreateParams.builder()
            .setAmount(amount)
            .setCurrency(currency.lowercase())
            .setDestination(stripeAccountId)
            .setDescription(description)
            .build()

        val transfer = Transfer.create(params)
        logger.info(
            "Created transfer {} of {} {} to account {}",
            transfer.id, amount, currency, stripeAccountId
        )
        return transfer.id
    }

    override fun getAccountStatus(stripeAccountId: String): SellerPaymentGateway.AccountStatus {
        val account = Account.retrieve(stripeAccountId)
        return SellerPaymentGateway.AccountStatus(
            chargesEnabled = account.chargesEnabled ?: false,
            payoutsEnabled = account.payoutsEnabled ?: false,
            detailsSubmitted = account.detailsSubmitted ?: false,
        )
    }

    override fun handleConnectWebhook(
        payload: String,
        signature: String,
    ): ConnectWebhookResult {
        val event: Event = try {
            Webhook.constructEvent(payload, signature, connectConfig.webhookSecret)
        } catch (e: SignatureVerificationException) {
            logger.error("Invalid Connect webhook signature", e)
            throw IllegalArgumentException("Invalid webhook signature", e)
        }

        logger.info("Processing Connect webhook event: {} ({})", event.type, event.id)

        return when (event.type) {
            "account.updated" -> handleAccountUpdated(event)
            "transfer.created" -> handleTransferCreated(event)
            else -> {
                logger.debug("Ignoring Connect event type: {}", event.type)
                ConnectWebhookResult.Ignored
            }
        }
    }

    private fun handleAccountUpdated(event: Event): ConnectWebhookResult {
        val account = event.dataObjectDeserializer.`object`.orElse(null) as? Account
            ?: throw IllegalStateException("Could not deserialize account")

        logger.info(
            "Account updated: id={}, chargesEnabled={}, payoutsEnabled={}, detailsSubmitted={}",
            account.id, account.chargesEnabled, account.payoutsEnabled, account.detailsSubmitted
        )

        return ConnectWebhookResult.AccountUpdated(
            stripeAccountId = account.id,
            chargesEnabled = account.chargesEnabled ?: false,
            payoutsEnabled = account.payoutsEnabled ?: false,
            detailsSubmitted = account.detailsSubmitted ?: false,
        )
    }

    private fun handleTransferCreated(event: Event): ConnectWebhookResult {
        val transfer = event.dataObjectDeserializer.`object`.orElse(null) as? Transfer
            ?: throw IllegalStateException("Could not deserialize transfer")

        logger.info(
            "Transfer created: id={}, destination={}, amount={} {}",
            transfer.id, transfer.destination, transfer.amount, transfer.currency
        )

        return ConnectWebhookResult.TransferCreated(
            transferId = transfer.id,
            stripeAccountId = transfer.destination,
            amount = transfer.amount,
            currency = transfer.currency,
        )
    }
}
