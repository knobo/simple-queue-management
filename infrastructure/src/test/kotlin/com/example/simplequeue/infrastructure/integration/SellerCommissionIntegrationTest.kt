package com.example.simplequeue.infrastructure.integration

import com.example.simplequeue.application.service.CommissionService
import com.example.simplequeue.application.service.ReferralService
import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.domain.model.CommissionEntry
import com.example.simplequeue.domain.model.Seller
import com.example.simplequeue.domain.model.Subscription
import com.example.simplequeue.domain.model.SubscriptionTier
import com.example.simplequeue.domain.port.CommissionEntryRepository
import com.example.simplequeue.domain.port.PaymentGateway
import com.example.simplequeue.domain.port.SellerReferralRepository
import com.example.simplequeue.domain.port.SellerRepository
import com.example.simplequeue.domain.port.SubscriptionRepository
import com.example.simplequeue.domain.port.WebhookResult
import com.example.simplequeue.infrastructure.TestEmailConfig
import com.example.simplequeue.infrastructure.TestJacksonConfig
import com.example.simplequeue.infrastructure.TestSecurityConfig
import com.example.simplequeue.infrastructure.filter.ReferralCookieFilter
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Integration tests for the complete seller → referral → commission flow.
 *
 * These tests verify:
 * 1. Referral cookie is set when visiting with ?ref=CODE
 * 2. SellerReferral is created when user with cookie logs in
 * 3. Subscription is linked to referral when created
 * 4. Commission is calculated when invoice is paid
 * 5. End-to-end flow from referral click to commission
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig::class, TestEmailConfig::class, TestJacksonConfig::class)
class SellerCommissionIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var sellerRepository: SellerRepository

    @Autowired
    private lateinit var sellerReferralRepository: SellerReferralRepository

    @Autowired
    private lateinit var subscriptionRepository: SubscriptionRepository

    @Autowired
    private lateinit var commissionEntryRepository: CommissionEntryRepository

    @Autowired
    private lateinit var referralService: ReferralService

    @Autowired
    private lateinit var subscriptionService: SubscriptionService

    @Autowired
    private lateinit var commissionService: CommissionService

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    @BeforeEach
    fun setUp() {
        // Clean up test data in correct order (respecting foreign keys)
        jdbcClient.sql("DELETE FROM payout_entries").update()
        jdbcClient.sql("DELETE FROM commission_entries").update()
        jdbcClient.sql("DELETE FROM seller_payouts").update()
        jdbcClient.sql("DELETE FROM seller_activity_log").update()
        jdbcClient.sql("DELETE FROM seller_referrals").update()
        jdbcClient.sql("UPDATE queues SET organization_id = NULL").update()
        jdbcClient.sql("DELETE FROM organizations").update()
        jdbcClient.sql("DELETE FROM sellers").update()
        jdbcClient.sql("DELETE FROM subscriptions").update()
    }

    /**
     * Creates a test seller with default commission settings.
     */
    private fun createTestSeller(
        userId: String = "seller-user-${UUID.randomUUID()}",
        referralCode: String = "TEST${System.currentTimeMillis()}",
        commissionPercent: BigDecimal = BigDecimal("20.00"),
        commissionCapPerCustomer: BigDecimal? = null,
        commissionPeriodMonths: Int = 12,
    ): Seller {
        val seller = Seller.create(
            userId = userId,
            name = "Test Seller",
            email = "seller@test.com",
            phone = null,
            createdBy = "admin",
            commissionPercent = commissionPercent,
            commissionCapPerCustomer = commissionCapPerCustomer,
            commissionPeriodMonths = commissionPeriodMonths,
        ).copy(referralCode = referralCode)
        sellerRepository.save(seller)
        return seller
    }

    /**
     * Creates a test subscription for a user.
     */
    private fun createTestSubscription(
        userId: String,
        tier: SubscriptionTier = SubscriptionTier.PRO,
        stripeCustomerId: String = "cus_${UUID.randomUUID().toString().take(14)}",
    ): Subscription {
        val now = Instant.now()
        val subscription = Subscription(
            id = UUID.randomUUID(),
            userId = userId,
            tier = tier,
            status = Subscription.SubscriptionStatus.ACTIVE,
            stripeCustomerId = stripeCustomerId,
            stripeSubscriptionId = "sub_${UUID.randomUUID().toString().take(14)}",
            currentPeriodStart = now,
            currentPeriodEnd = now.plusSeconds(30L * 24 * 60 * 60),
            cancelAtPeriodEnd = false,
            createdAt = now,
            updatedAt = now,
        )
        subscriptionRepository.save(subscription)
        return subscription
    }

    // =========================================================================
    // 1. Referral Cookie Flow Tests
    // =========================================================================

    @Nested
    @DisplayName("1. Referral Cookie Flow")
    inner class ReferralCookieFlowTests {

        @Test
        @DisplayName("Should set referral cookie when visiting page with ?ref=CODE")
        fun shouldSetReferralCookieWhenVisitingWithRefParam() {
            val seller = createTestSeller(referralCode = "SELLER123")

            mockMvc.perform(get("/").param("ref", "SELLER123"))
                .andExpect(status().isOk)
                .andExpect(cookie().exists(ReferralCookieFilter.REFERRAL_COOKIE_NAME))
                .andExpect(cookie().value(ReferralCookieFilter.REFERRAL_COOKIE_NAME, "SELLER123"))
                .andExpect(cookie().maxAge(ReferralCookieFilter.REFERRAL_COOKIE_NAME, 30 * 24 * 60 * 60))
                .andExpect(cookie().path(ReferralCookieFilter.REFERRAL_COOKIE_NAME, "/"))
                .andExpect(cookie().httpOnly(ReferralCookieFilter.REFERRAL_COOKIE_NAME, true))
        }

        @Test
        @DisplayName("Should not set cookie when no ref parameter")
        fun shouldNotSetCookieWhenNoRefParam() {
            mockMvc.perform(get("/"))
                .andExpect(status().isOk)
                .andExpect(cookie().doesNotExist(ReferralCookieFilter.REFERRAL_COOKIE_NAME))
        }

        @Test
        @DisplayName("Should not set cookie when ref parameter is blank")
        fun shouldNotSetCookieWhenRefParamIsBlank() {
            mockMvc.perform(get("/").param("ref", ""))
                .andExpect(status().isOk)
                .andExpect(cookie().doesNotExist(ReferralCookieFilter.REFERRAL_COOKIE_NAME))

            mockMvc.perform(get("/").param("ref", "   "))
                .andExpect(status().isOk)
                .andExpect(cookie().doesNotExist(ReferralCookieFilter.REFERRAL_COOKIE_NAME))
        }

        @Test
        @DisplayName("Should set cookie on any public page with ref parameter")
        fun shouldSetCookieOnAnyPublicPageWithRefParam() {
            val seller = createTestSeller(referralCode = "ANYPAGE")

            // Test on landing page
            mockMvc.perform(get("/").param("ref", "ANYPAGE"))
                .andExpect(cookie().exists(ReferralCookieFilter.REFERRAL_COOKIE_NAME))

            // Test on signup page
            mockMvc.perform(get("/signup").param("ref", "ANYPAGE"))
                .andExpect(cookie().exists(ReferralCookieFilter.REFERRAL_COOKIE_NAME))
        }
    }

    // =========================================================================
    // 2. Referral Creation on Login Tests
    // =========================================================================

    @Nested
    @DisplayName("2. Referral Creation on Login")
    inner class ReferralCreationOnLoginTests {

        @Test
        @DisplayName("Should create SellerReferral when user with valid referral code logs in")
        fun shouldCreateReferralWhenUserWithValidCodeLogsIn() {
            val seller = createTestSeller(referralCode = "VALID123")
            val userId = "new-user-${UUID.randomUUID()}"

            // Simulate ReferralService.processReferralForUser (called by LoginSuccessHandler)
            val success = referralService.processReferralForUser(userId, "VALID123")

            assertThat(success).isTrue()

            // Verify referral was created
            val referral = sellerReferralRepository.findByCustomerUserId(userId)
            assertThat(referral).isNotNull
            assertThat(referral!!.sellerId).isEqualTo(seller.id)
            assertThat(referral.customerUserId).isEqualTo(userId)
            assertThat(referral.subscriptionId).isNull() // Not linked yet
            assertThat(referral.totalCommissionEarned).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(referral.isCommissionActive()).isTrue()
        }

        @Test
        @DisplayName("Should not create referral when referral code is invalid")
        fun shouldNotCreateReferralWhenCodeIsInvalid() {
            val userId = "new-user-${UUID.randomUUID()}"

            val success = referralService.processReferralForUser(userId, "INVALID999")

            assertThat(success).isFalse()
            assertThat(sellerReferralRepository.findByCustomerUserId(userId)).isNull()
        }

        @Test
        @DisplayName("Should not create referral when referral code is null or blank")
        fun shouldNotCreateReferralWhenCodeIsNullOrBlank() {
            val userId = "new-user-${UUID.randomUUID()}"

            assertThat(referralService.processReferralForUser(userId, null)).isFalse()
            assertThat(referralService.processReferralForUser(userId, "")).isFalse()
            assertThat(referralService.processReferralForUser(userId, "   ")).isFalse()
        }

        @Test
        @DisplayName("Should not create duplicate referral for same user")
        fun shouldNotCreateDuplicateReferralForSameUser() {
            val seller = createTestSeller(referralCode = "UNIQUE123")
            val userId = "existing-user-${UUID.randomUUID()}"

            // First referral succeeds
            val firstAttempt = referralService.processReferralForUser(userId, "UNIQUE123")
            assertThat(firstAttempt).isTrue()

            // Second attempt fails (user already has referral)
            val secondAttempt = referralService.processReferralForUser(userId, "UNIQUE123")
            assertThat(secondAttempt).isFalse()

            // Only one referral exists
            assertThat(sellerReferralRepository.findByCustomerUserId(userId)).isNotNull
        }

        @Test
        @DisplayName("Should not allow self-referral")
        fun shouldNotAllowSelfReferral() {
            val sellerId = "seller-${UUID.randomUUID()}"
            val seller = createTestSeller(userId = sellerId, referralCode = "SELFSELL")

            val success = referralService.processReferralForUser(sellerId, "SELFSELL")

            assertThat(success).isFalse()
            assertThat(sellerReferralRepository.findByCustomerUserId(sellerId)).isNull()
        }

        @Test
        @DisplayName("Should not create referral when seller is inactive")
        fun shouldNotCreateReferralWhenSellerIsInactive() {
            val seller = createTestSeller(referralCode = "INACTIVE1")
                .copy(status = Seller.SellerStatus.INACTIVE)
            sellerRepository.save(seller)

            val userId = "new-user-${UUID.randomUUID()}"
            val success = referralService.processReferralForUser(userId, "INACTIVE1")

            assertThat(success).isFalse()
            assertThat(sellerReferralRepository.findByCustomerUserId(userId)).isNull()
        }
    }

    // =========================================================================
    // 3. Subscription Links to Referral Tests
    // =========================================================================

    @Nested
    @DisplayName("3. Subscription Links to Referral")
    inner class SubscriptionLinksToReferralTests {

        @Test
        @DisplayName("Should link subscription to existing referral when subscription is created")
        fun shouldLinkSubscriptionToExistingReferral() {
            val seller = createTestSeller(referralCode = "LINK123")
            val userId = "subscriber-${UUID.randomUUID()}"

            // Step 1: User was referred
            referralService.processReferralForUser(userId, "LINK123")
            val referralBefore = sellerReferralRepository.findByCustomerUserId(userId)
            assertThat(referralBefore).isNotNull
            assertThat(referralBefore!!.subscriptionId).isNull()

            // Step 2: User creates subscription (via Stripe webhook)
            val now = Instant.now()
            val webhookResult = WebhookResult.SubscriptionCreated(
                stripeCustomerId = "cus_test123",
                stripeSubscriptionId = "sub_test456",
                userId = userId,
                tier = SubscriptionTier.PRO,
                currentPeriodStart = now,
                currentPeriodEnd = now.plusSeconds(30L * 24 * 60 * 60),
            )
            subscriptionService.handleSubscriptionCreated(webhookResult)

            // Verify subscription exists
            val subscription = subscriptionRepository.findByUserId(userId)
            assertThat(subscription).isNotNull
            assertThat(subscription!!.tier).isEqualTo(SubscriptionTier.PRO)

            // Verify referral is now linked to subscription
            val referralAfter = sellerReferralRepository.findByCustomerUserId(userId)
            assertThat(referralAfter).isNotNull
            assertThat(referralAfter!!.subscriptionId).isEqualTo(subscription.id)
        }

        @Test
        @DisplayName("Should not overwrite existing subscription link on referral")
        fun shouldNotOverwriteExistingSubscriptionLink() {
            val seller = createTestSeller(referralCode = "NOOVERWRITE")
            val userId = "subscriber-${UUID.randomUUID()}"

            // Create referral
            referralService.processReferralForUser(userId, "NOOVERWRITE")

            // First subscription
            val now = Instant.now()
            subscriptionService.handleSubscriptionCreated(
                WebhookResult.SubscriptionCreated(
                    stripeCustomerId = "cus_first",
                    stripeSubscriptionId = "sub_first",
                    userId = userId,
                    tier = SubscriptionTier.PRO,
                    currentPeriodStart = now,
                    currentPeriodEnd = now.plusSeconds(30L * 24 * 60 * 60),
                )
            )

            val firstSubscription = subscriptionRepository.findByUserId(userId)!!
            val referralAfterFirst = sellerReferralRepository.findByCustomerUserId(userId)!!
            assertThat(referralAfterFirst.subscriptionId).isEqualTo(firstSubscription.id)

            // Update subscription (simulating renewal/update)
            subscriptionService.handleSubscriptionUpdated(
                WebhookResult.SubscriptionUpdated(
                    stripeCustomerId = "cus_first",
                    stripeSubscriptionId = "sub_first",
                    tier = SubscriptionTier.ENTERPRISE,
                    status = Subscription.SubscriptionStatus.ACTIVE,
                    currentPeriodStart = now.plusSeconds(30L * 24 * 60 * 60),
                    currentPeriodEnd = now.plusSeconds(60L * 24 * 60 * 60),
                    cancelAtPeriodEnd = false,
                )
            )

            // Referral should still be linked to original subscription
            val referralAfterUpdate = sellerReferralRepository.findByCustomerUserId(userId)!!
            assertThat(referralAfterUpdate.subscriptionId).isEqualTo(firstSubscription.id)
        }
    }

    // =========================================================================
    // 4. Commission Calculation on Payment Tests
    // =========================================================================

    @Nested
    @DisplayName("4. Commission Calculation on Payment")
    inner class CommissionCalculationTests {

        @Test
        @DisplayName("Should create CommissionEntry when invoice.paid webhook is received")
        fun shouldCreateCommissionEntryWhenInvoicePaid() {
            // Setup: Seller -> Referral -> Subscription
            val seller = createTestSeller(
                referralCode = "COMMISSION1",
                commissionPercent = BigDecimal("20.00"),
            )
            val userId = "paying-user-${UUID.randomUUID()}"

            referralService.processReferralForUser(userId, "COMMISSION1")

            val now = Instant.now()
            subscriptionService.handleSubscriptionCreated(
                WebhookResult.SubscriptionCreated(
                    stripeCustomerId = "cus_paying",
                    stripeSubscriptionId = "sub_paying",
                    userId = userId,
                    tier = SubscriptionTier.PRO,
                    currentPeriodStart = now,
                    currentPeriodEnd = now.plusSeconds(30L * 24 * 60 * 60),
                )
            )

            val subscription = subscriptionRepository.findByUserId(userId)!!

            // Process payment
            val periodStart = LocalDate.now()
            val periodEnd = periodStart.plusMonths(1)
            val grossAmount = BigDecimal("99.00")

            val commissionEntry = commissionService.processPayment(
                subscriptionId = subscription.id,
                grossAmount = grossAmount,
                sourceType = CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT,
                sourceReference = "in_test_invoice_123",
                periodStart = periodStart,
                periodEnd = periodEnd,
            )

            // Verify commission entry
            assertThat(commissionEntry).isNotNull
            assertThat(commissionEntry!!.sellerId).isEqualTo(seller.id)
            assertThat(commissionEntry.grossAmount).isEqualByComparingTo(BigDecimal("99.00"))
            assertThat(commissionEntry.commissionPercent).isEqualByComparingTo(BigDecimal("20.00"))
            assertThat(commissionEntry.commissionAmount).isEqualByComparingTo(BigDecimal("19.80")) // 99 * 20%
            assertThat(commissionEntry.sourceType).isEqualTo(CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT)
            assertThat(commissionEntry.sourceReference).isEqualTo("in_test_invoice_123")

            // Verify commission entry is saved
            val savedEntry = commissionEntryRepository.findById(commissionEntry.id)
            assertThat(savedEntry).isNotNull
        }

        @Test
        @DisplayName("Should update SellerReferral.totalCommissionEarned when payment processed")
        fun shouldUpdateReferralTotalCommissionEarned() {
            val seller = createTestSeller(
                referralCode = "TOTAL123",
                commissionPercent = BigDecimal("25.00"),
            )
            val userId = "total-user-${UUID.randomUUID()}"

            referralService.processReferralForUser(userId, "TOTAL123")

            val now = Instant.now()
            subscriptionService.handleSubscriptionCreated(
                WebhookResult.SubscriptionCreated(
                    stripeCustomerId = "cus_total",
                    stripeSubscriptionId = "sub_total",
                    userId = userId,
                    tier = SubscriptionTier.PRO,
                    currentPeriodStart = now,
                    currentPeriodEnd = now.plusSeconds(30L * 24 * 60 * 60),
                )
            )

            val subscription = subscriptionRepository.findByUserId(userId)!!
            val periodStart = LocalDate.now()

            // First payment
            commissionService.processPayment(
                subscriptionId = subscription.id,
                grossAmount = BigDecimal("100.00"),
                sourceType = CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT,
                sourceReference = "in_first",
                periodStart = periodStart,
                periodEnd = periodStart.plusMonths(1),
            )

            var referral = sellerReferralRepository.findByCustomerUserId(userId)!!
            assertThat(referral.totalCommissionEarned).isEqualByComparingTo(BigDecimal("25.00")) // 100 * 25%

            // Second payment
            commissionService.processPayment(
                subscriptionId = subscription.id,
                grossAmount = BigDecimal("100.00"),
                sourceType = CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT,
                sourceReference = "in_second",
                periodStart = periodStart.plusMonths(1),
                periodEnd = periodStart.plusMonths(2),
            )

            referral = sellerReferralRepository.findByCustomerUserId(userId)!!
            assertThat(referral.totalCommissionEarned).isEqualByComparingTo(BigDecimal("50.00")) // 2 * 25
        }

        @Test
        @DisplayName("Should respect commission cap per customer")
        fun shouldRespectCommissionCapPerCustomer() {
            val seller = createTestSeller(
                referralCode = "CAPPED123",
                commissionPercent = BigDecimal("20.00"),
                commissionCapPerCustomer = BigDecimal("30.00"), // Cap at 30 kr
            )
            val userId = "capped-user-${UUID.randomUUID()}"

            referralService.processReferralForUser(userId, "CAPPED123")

            val now = Instant.now()
            subscriptionService.handleSubscriptionCreated(
                WebhookResult.SubscriptionCreated(
                    stripeCustomerId = "cus_capped",
                    stripeSubscriptionId = "sub_capped",
                    userId = userId,
                    tier = SubscriptionTier.PRO,
                    currentPeriodStart = now,
                    currentPeriodEnd = now.plusSeconds(30L * 24 * 60 * 60),
                )
            )

            val subscription = subscriptionRepository.findByUserId(userId)!!
            val periodStart = LocalDate.now()

            // First payment: 100 * 20% = 20, under cap
            val entry1 = commissionService.processPayment(
                subscriptionId = subscription.id,
                grossAmount = BigDecimal("100.00"),
                sourceType = CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT,
                sourceReference = "in_capped_1",
                periodStart = periodStart,
                periodEnd = periodStart.plusMonths(1),
            )
            assertThat(entry1!!.commissionAmount).isEqualByComparingTo(BigDecimal("20.00"))

            // Second payment: Would be 20, but cap is 30, remaining is 10
            val entry2 = commissionService.processPayment(
                subscriptionId = subscription.id,
                grossAmount = BigDecimal("100.00"),
                sourceType = CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT,
                sourceReference = "in_capped_2",
                periodStart = periodStart.plusMonths(1),
                periodEnd = periodStart.plusMonths(2),
            )
            assertThat(entry2!!.commissionAmount).isEqualByComparingTo(BigDecimal("10.00")) // Only 10 remaining

            // Third payment: Cap reached, no commission
            val entry3 = commissionService.processPayment(
                subscriptionId = subscription.id,
                grossAmount = BigDecimal("100.00"),
                sourceType = CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT,
                sourceReference = "in_capped_3",
                periodStart = periodStart.plusMonths(2),
                periodEnd = periodStart.plusMonths(3),
            )
            assertThat(entry3).isNull() // No commission, cap reached

            // Verify total commission equals cap
            val referral = sellerReferralRepository.findByCustomerUserId(userId)!!
            assertThat(referral.totalCommissionEarned).isEqualByComparingTo(BigDecimal("30.00"))
        }

        @Test
        @DisplayName("Should not create commission when referral is not linked to subscription")
        fun shouldNotCreateCommissionWhenNoReferral() {
            // Subscription without referral
            val userId = "no-referral-user-${UUID.randomUUID()}"
            val subscription = createTestSubscription(userId)

            val entry = commissionService.processPayment(
                subscriptionId = subscription.id,
                grossAmount = BigDecimal("99.00"),
                sourceType = CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT,
                sourceReference = "in_no_referral",
                periodStart = LocalDate.now(),
                periodEnd = LocalDate.now().plusMonths(1),
            )

            assertThat(entry).isNull()
        }

        @Test
        @DisplayName("Should not create commission when commission period has ended")
        fun shouldNotCreateCommissionWhenPeriodEnded() {
            val seller = createTestSeller(
                referralCode = "EXPIRED123",
                commissionPeriodMonths = 0, // Immediately expired for testing
            )
            val userId = "expired-user-${UUID.randomUUID()}"

            referralService.processReferralForUser(userId, "EXPIRED123")

            // Manually set commission end date to the past
            val referral = sellerReferralRepository.findByCustomerUserId(userId)!!
            val expiredReferral = referral.copy(
                commissionEndsAt = Instant.now().minusSeconds(1)
            )
            sellerReferralRepository.save(expiredReferral)

            val now = Instant.now()
            subscriptionService.handleSubscriptionCreated(
                WebhookResult.SubscriptionCreated(
                    stripeCustomerId = "cus_expired",
                    stripeSubscriptionId = "sub_expired",
                    userId = userId,
                    tier = SubscriptionTier.PRO,
                    currentPeriodStart = now,
                    currentPeriodEnd = now.plusSeconds(30L * 24 * 60 * 60),
                )
            )

            val subscription = subscriptionRepository.findByUserId(userId)!!

            val entry = commissionService.processPayment(
                subscriptionId = subscription.id,
                grossAmount = BigDecimal("99.00"),
                sourceType = CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT,
                sourceReference = "in_expired",
                periodStart = LocalDate.now(),
                periodEnd = LocalDate.now().plusMonths(1),
            )

            assertThat(entry).isNull() // No commission for expired referral
        }

        @Test
        @DisplayName("Should not create commission when seller is inactive")
        fun shouldNotCreateCommissionWhenSellerInactive() {
            val seller = createTestSeller(referralCode = "INACTIVESELLER")
            val userId = "inactive-seller-user-${UUID.randomUUID()}"

            referralService.processReferralForUser(userId, "INACTIVESELLER")

            val now = Instant.now()
            subscriptionService.handleSubscriptionCreated(
                WebhookResult.SubscriptionCreated(
                    stripeCustomerId = "cus_inactive_seller",
                    stripeSubscriptionId = "sub_inactive_seller",
                    userId = userId,
                    tier = SubscriptionTier.PRO,
                    currentPeriodStart = now,
                    currentPeriodEnd = now.plusSeconds(30L * 24 * 60 * 60),
                )
            )

            // Deactivate seller after referral was created
            sellerRepository.save(seller.copy(status = Seller.SellerStatus.INACTIVE, updatedAt = Instant.now()))

            val subscription = subscriptionRepository.findByUserId(userId)!!

            val entry = commissionService.processPayment(
                subscriptionId = subscription.id,
                grossAmount = BigDecimal("99.00"),
                sourceType = CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT,
                sourceReference = "in_inactive_seller",
                periodStart = LocalDate.now(),
                periodEnd = LocalDate.now().plusMonths(1),
            )

            assertThat(entry).isNull()
        }
    }

    // =========================================================================
    // 5. End-to-End Flow Tests
    // =========================================================================

    @Nested
    @DisplayName("5. End-to-End Flow")
    inner class EndToEndFlowTests {

        @Test
        @DisplayName("Complete flow: referral click → login → subscription → payment → commission")
        fun completeFlowFromReferralClickToCommission() {
            // =====================================================
            // Step 1: Seller is set up with referral code
            // =====================================================
            val seller = createTestSeller(
                referralCode = "E2ETEST",
                commissionPercent = BigDecimal("15.00"),
            )
            assertThat(sellerRepository.findByReferralCode("E2ETEST")).isNotNull

            // =====================================================
            // Step 2: User visits landing page with ?ref=E2ETEST
            // =====================================================
            mockMvc.perform(get("/").param("ref", "E2ETEST"))
                .andExpect(status().isOk)
                .andExpect(cookie().exists(ReferralCookieFilter.REFERRAL_COOKIE_NAME))
                .andExpect(cookie().value(ReferralCookieFilter.REFERRAL_COOKIE_NAME, "E2ETEST"))

            // =====================================================
            // Step 3: User logs in (cookie is processed by LoginSuccessHandler)
            // =====================================================
            val userId = "e2e-customer-${UUID.randomUUID()}"
            val referralProcessed = referralService.processReferralForUser(userId, "E2ETEST")
            assertThat(referralProcessed).isTrue()

            val referral = sellerReferralRepository.findByCustomerUserId(userId)
            assertThat(referral).isNotNull
            assertThat(referral!!.sellerId).isEqualTo(seller.id)
            assertThat(referral.subscriptionId).isNull() // Not yet subscribed

            // =====================================================
            // Step 4: User creates subscription (Stripe checkout completes)
            // =====================================================
            val now = Instant.now()
            subscriptionService.handleSubscriptionCreated(
                WebhookResult.SubscriptionCreated(
                    stripeCustomerId = "cus_e2e_customer",
                    stripeSubscriptionId = "sub_e2e_customer",
                    userId = userId,
                    tier = SubscriptionTier.PRO,
                    currentPeriodStart = now,
                    currentPeriodEnd = now.plusSeconds(30L * 24 * 60 * 60),
                )
            )

            val subscription = subscriptionRepository.findByUserId(userId)
            assertThat(subscription).isNotNull
            assertThat(subscription!!.tier).isEqualTo(SubscriptionTier.PRO)

            // Verify referral is now linked to subscription
            val linkedReferral = sellerReferralRepository.findByCustomerUserId(userId)
            assertThat(linkedReferral!!.subscriptionId).isEqualTo(subscription.id)

            // =====================================================
            // Step 5: invoice.paid webhook arrives (payment processed)
            // =====================================================
            val commissionEntry = commissionService.processPayment(
                subscriptionId = subscription.id,
                grossAmount = BigDecimal("199.00"), // PRO plan price
                sourceType = CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT,
                sourceReference = "in_e2e_first_payment",
                periodStart = LocalDate.now(),
                periodEnd = LocalDate.now().plusMonths(1),
            )

            // =====================================================
            // Verify: Commission calculated correctly
            // =====================================================
            assertThat(commissionEntry).isNotNull
            assertThat(commissionEntry!!.sellerId).isEqualTo(seller.id)
            assertThat(commissionEntry.grossAmount).isEqualByComparingTo(BigDecimal("199.00"))
            assertThat(commissionEntry.commissionPercent).isEqualByComparingTo(BigDecimal("15.00"))
            assertThat(commissionEntry.commissionAmount).isEqualByComparingTo(BigDecimal("29.85")) // 199 * 15%

            // Verify referral totals updated
            val finalReferral = sellerReferralRepository.findByCustomerUserId(userId)
            assertThat(finalReferral!!.totalCommissionEarned).isEqualByComparingTo(BigDecimal("29.85"))
            assertThat(finalReferral.totalCommissionPaid).isEqualByComparingTo(BigDecimal.ZERO)

            // Verify commission entry persisted
            val entries = commissionEntryRepository.findBySellerId(seller.id)
            assertThat(entries).hasSize(1)
            assertThat(entries[0].sourceReference).isEqualTo("in_e2e_first_payment")
        }

        @Test
        @DisplayName("Multiple payments accumulate commission over time")
        fun multiplePaymentsAccumulateCommission() {
            val seller = createTestSeller(
                referralCode = "MULTI123",
                commissionPercent = BigDecimal("20.00"),
            )
            val userId = "multi-payment-user-${UUID.randomUUID()}"

            // Setup referral and subscription
            referralService.processReferralForUser(userId, "MULTI123")

            val now = Instant.now()
            subscriptionService.handleSubscriptionCreated(
                WebhookResult.SubscriptionCreated(
                    stripeCustomerId = "cus_multi",
                    stripeSubscriptionId = "sub_multi",
                    userId = userId,
                    tier = SubscriptionTier.PRO,
                    currentPeriodStart = now,
                    currentPeriodEnd = now.plusSeconds(30L * 24 * 60 * 60),
                )
            )

            val subscription = subscriptionRepository.findByUserId(userId)!!
            val periodStart = LocalDate.now()

            // Simulate 3 monthly payments
            for (month in 0..2) {
                commissionService.processPayment(
                    subscriptionId = subscription.id,
                    grossAmount = BigDecimal("99.00"),
                    sourceType = CommissionEntry.SourceType.SUBSCRIPTION_PAYMENT,
                    sourceReference = "in_month_$month",
                    periodStart = periodStart.plusMonths(month.toLong()),
                    periodEnd = periodStart.plusMonths(month.toLong() + 1),
                )
            }

            // Verify all entries created
            val entries = commissionEntryRepository.findBySellerId(seller.id)
            assertThat(entries).hasSize(3)

            // Verify total commission: 3 * (99 * 20%) = 3 * 19.80 = 59.40
            val referral = sellerReferralRepository.findByCustomerUserId(userId)!!
            assertThat(referral.totalCommissionEarned).isEqualByComparingTo(BigDecimal("59.40"))
        }
    }
}
