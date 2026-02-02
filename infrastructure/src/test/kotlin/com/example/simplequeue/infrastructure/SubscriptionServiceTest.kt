package com.example.simplequeue.infrastructure

import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.QueueMember
import com.example.simplequeue.domain.model.Subscription
import com.example.simplequeue.domain.model.SubscriptionTier
import com.example.simplequeue.domain.model.TierLimit
import com.example.simplequeue.domain.port.QueueMemberRepository
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.SubscriptionRepository
import com.example.simplequeue.domain.port.TierLimitRepository
import com.example.simplequeue.domain.port.WebhookResult
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SubscriptionServiceTest {

    private lateinit var subscriptionRepository: InMemorySubscriptionRepository
    private lateinit var queueRepository: ConfigurableQueueRepository
    private lateinit var queueMemberRepository: ConfigurableQueueMemberRepository
    private lateinit var tierLimitRepository: InMemoryTierLimitRepository
    private lateinit var service: SubscriptionService

    @BeforeEach
    fun setUp() {
        subscriptionRepository = InMemorySubscriptionRepository()
        queueRepository = ConfigurableQueueRepository()
        queueMemberRepository = ConfigurableQueueMemberRepository()
        tierLimitRepository = InMemoryTierLimitRepository()
        service = SubscriptionService(
            subscriptionRepository,
            queueRepository,
            queueMemberRepository,
            tierLimitRepository
        )
    }

    // ====================================================================================
    // TIER LIMITS ENFORCEMENT TESTS
    // ====================================================================================

    @Nested
    inner class CanCreateQueue {

        @Test
        fun `should return true when user has no queues and FREE tier allows 1`() {
            // Given: FREE user with 0 queues (FREE allows 1 queue)
            val userId = "user-free"
            subscriptionRepository.save(Subscription.createFree(userId))
            queueRepository.setQueuesForOwner(userId, emptyList())

            // When
            val result = service.canCreateQueue(userId)

            // Then
            assertThat(result).isTrue()
        }

        @Test
        fun `should return false when FREE user reaches queue limit`() {
            // Given: FREE user already has 1 queue (FREE limit is 1)
            val userId = "user-free"
            subscriptionRepository.save(Subscription.createFree(userId))
            queueRepository.setQueuesForOwner(userId, listOf(Queue.create("Queue 1", userId)))

            // When
            val result = service.canCreateQueue(userId)

            // Then
            assertThat(result).isFalse()
        }

        @Test
        fun `should return true when STARTER user has fewer than 3 queues`() {
            // Given: STARTER user with 2 queues (STARTER allows 3)
            val userId = "user-starter"
            val subscription = Subscription.createFree(userId).apply {
                tier = SubscriptionTier.STARTER
                status = Subscription.SubscriptionStatus.ACTIVE
            }
            subscriptionRepository.save(subscription)
            queueRepository.setQueuesForOwner(userId, listOf(
                Queue.create("Queue 1", userId),
                Queue.create("Queue 2", userId)
            ))

            // When
            val result = service.canCreateQueue(userId)

            // Then
            assertThat(result).isTrue()
        }

        @Test
        fun `should return false when STARTER user reaches queue limit`() {
            // Given: STARTER user already has 3 queues (STARTER limit is 3)
            val userId = "user-starter"
            val subscription = Subscription.createFree(userId).apply {
                tier = SubscriptionTier.STARTER
                status = Subscription.SubscriptionStatus.ACTIVE
            }
            subscriptionRepository.save(subscription)
            queueRepository.setQueuesForOwner(userId, listOf(
                Queue.create("Queue 1", userId),
                Queue.create("Queue 2", userId),
                Queue.create("Queue 3", userId)
            ))

            // When
            val result = service.canCreateQueue(userId)

            // Then
            assertThat(result).isFalse()
        }

        @Test
        fun `should return true for ENTERPRISE user with unlimited queues`() {
            // Given: ENTERPRISE user with many queues (ENTERPRISE has unlimited)
            val userId = "user-enterprise"
            val subscription = Subscription.createFree(userId).apply {
                tier = SubscriptionTier.ENTERPRISE
                status = Subscription.SubscriptionStatus.ACTIVE
            }
            subscriptionRepository.save(subscription)
            // Create 100 queues
            val queues = (1..100).map { Queue.create("Queue $it", userId) }
            queueRepository.setQueuesForOwner(userId, queues)

            // When
            val result = service.canCreateQueue(userId)

            // Then
            assertThat(result).isTrue()
        }
    }

    @Nested
    inner class CanInviteOperator {

        @Test
        fun `should return false for FREE user - operators not allowed`() {
            // Given: FREE user (FREE allows 0 operators per queue)
            val userId = "user-free"
            val queueId = UUID.randomUUID()
            subscriptionRepository.save(Subscription.createFree(userId))
            queueMemberRepository.setMemberCount(queueId, 0)

            // When
            val result = service.canInviteOperator(userId, queueId)

            // Then
            assertThat(result).isFalse()
        }

        @Test
        fun `should return true for STARTER user with fewer than 2 operators`() {
            // Given: STARTER user with 1 operator (STARTER allows 2)
            val userId = "user-starter"
            val queueId = UUID.randomUUID()
            val subscription = Subscription.createFree(userId).apply {
                tier = SubscriptionTier.STARTER
                status = Subscription.SubscriptionStatus.ACTIVE
            }
            subscriptionRepository.save(subscription)
            queueMemberRepository.setMemberCount(queueId, 1)

            // When
            val result = service.canInviteOperator(userId, queueId)

            // Then
            assertThat(result).isTrue()
        }

        @Test
        fun `should return false for STARTER user at operator limit`() {
            // Given: STARTER user with 2 operators (STARTER limit is 2)
            val userId = "user-starter"
            val queueId = UUID.randomUUID()
            val subscription = Subscription.createFree(userId).apply {
                tier = SubscriptionTier.STARTER
                status = Subscription.SubscriptionStatus.ACTIVE
            }
            subscriptionRepository.save(subscription)
            queueMemberRepository.setMemberCount(queueId, 2)

            // When
            val result = service.canInviteOperator(userId, queueId)

            // Then
            assertThat(result).isFalse()
        }

        @Test
        fun `should return true for PRO user with many operators`() {
            // Given: PRO user with 9 operators (PRO allows 10)
            val userId = "user-pro"
            val queueId = UUID.randomUUID()
            val subscription = Subscription.createFree(userId).apply {
                tier = SubscriptionTier.PRO
                status = Subscription.SubscriptionStatus.ACTIVE
            }
            subscriptionRepository.save(subscription)
            queueMemberRepository.setMemberCount(queueId, 9)

            // When
            val result = service.canInviteOperator(userId, queueId)

            // Then
            assertThat(result).isTrue()
        }

        @Test
        fun `should return true for ENTERPRISE user - unlimited operators`() {
            // Given: ENTERPRISE user with many operators (unlimited)
            val userId = "user-enterprise"
            val queueId = UUID.randomUUID()
            val subscription = Subscription.createFree(userId).apply {
                tier = SubscriptionTier.ENTERPRISE
                status = Subscription.SubscriptionStatus.ACTIVE
            }
            subscriptionRepository.save(subscription)
            queueMemberRepository.setMemberCount(queueId, 100)

            // When
            val result = service.canInviteOperator(userId, queueId)

            // Then
            assertThat(result).isTrue()
        }
    }

    @Nested
    inner class TierLimitEnforcementOnUpgrade {

        @Test
        fun `limits should increase when upgrading from FREE to STARTER`() {
            // Given: FREE user
            val userId = "user-upgrade"
            subscriptionRepository.save(Subscription.createFree(userId))

            // Get FREE limits
            val freeLimits = service.getTierLimitForUser(userId)
            assertThat(freeLimits.maxQueues).isEqualTo(1)
            assertThat(freeLimits.maxOperatorsPerQueue).isEqualTo(0)

            // When: Upgrade to STARTER
            val subscription = subscriptionRepository.findByUserId(userId)!!
            subscription.tier = SubscriptionTier.STARTER
            subscription.status = Subscription.SubscriptionStatus.ACTIVE
            subscriptionRepository.save(subscription)

            // Then: Limits should be higher
            val starterLimits = service.getTierLimitForUser(userId)
            assertThat(starterLimits.maxQueues).isEqualTo(3)
            assertThat(starterLimits.maxOperatorsPerQueue).isEqualTo(2)
        }

        @Test
        fun `limits should increase when upgrading from STARTER to PRO`() {
            // Given: STARTER user
            val userId = "user-upgrade-pro"
            val subscription = Subscription.createFree(userId).apply {
                tier = SubscriptionTier.STARTER
                status = Subscription.SubscriptionStatus.ACTIVE
            }
            subscriptionRepository.save(subscription)

            val starterLimits = service.getTierLimitForUser(userId)
            assertThat(starterLimits.maxQueues).isEqualTo(3)

            // When: Upgrade to PRO
            subscription.tier = SubscriptionTier.PRO
            subscriptionRepository.save(subscription)

            // Then: Limits should be higher
            val proLimits = service.getTierLimitForUser(userId)
            assertThat(proLimits.maxQueues).isEqualTo(10)
            assertThat(proLimits.canUseCustomBranding).isTrue()
            assertThat(proLimits.canUseAnalytics).isTrue()
        }

        @Test
        fun `can create queue after upgrading from FREE to STARTER`() {
            // Given: FREE user at limit (1 queue)
            val userId = "user-at-limit"
            subscriptionRepository.save(Subscription.createFree(userId))
            queueRepository.setQueuesForOwner(userId, listOf(Queue.create("Queue 1", userId)))

            // Verify cannot create more
            assertThat(service.canCreateQueue(userId)).isFalse()

            // When: Upgrade to STARTER
            val subscription = subscriptionRepository.findByUserId(userId)!!
            subscription.tier = SubscriptionTier.STARTER
            subscription.status = Subscription.SubscriptionStatus.ACTIVE
            subscriptionRepository.save(subscription)

            // Then: Can now create more queues
            assertThat(service.canCreateQueue(userId)).isTrue()
        }
    }

    @Nested
    inner class CustomTierLimitsFromRepository {

        @Test
        fun `should use custom limits from repository when available`() {
            // Given: Custom FREE limits in repository (more generous than defaults)
            val customFreeLimit = TierLimit(
                tier = SubscriptionTier.FREE,
                maxQueues = 5,  // Default is 1
                maxOperatorsPerQueue = 2,  // Default is 0
                maxTicketsPerDay = 100,  // Default is 50
                maxActiveTickets = 200,
                maxInvitesPerMonth = 10,
                maxCountersPerQueue = 3,  // Default is 1
                canUseEmailNotifications = true,  // Default is false
                canUseCustomBranding = false,
                canUseAnalytics = false,
                canUseApiAccess = false,
                updatedAt = Instant.now(),
                updatedBy = "admin"
            )
            tierLimitRepository.save(customFreeLimit)

            val userId = "user-custom"
            subscriptionRepository.save(Subscription.createFree(userId))

            // When
            val limits = service.getTierLimitForUser(userId)

            // Then: Should use custom limits
            assertThat(limits.maxQueues).isEqualTo(5)
            assertThat(limits.maxOperatorsPerQueue).isEqualTo(2)
            assertThat(limits.canUseEmailNotifications).isTrue()
        }
    }

    @Nested
    inner class HandleSubscriptionCreated {

        @Test
        fun `should create new subscription when user has no existing subscription`() {
            val result = WebhookResult.SubscriptionCreated(
                stripeCustomerId = "cus_123",
                stripeSubscriptionId = "sub_456",
                userId = "user-1",
                tier = SubscriptionTier.PRO,
                currentPeriodStart = Instant.now(),
                currentPeriodEnd = Instant.now().plusSeconds(30 * 24 * 60 * 60),
            )

            service.handleSubscriptionCreated(result)

            val subscription = subscriptionRepository.findByUserId("user-1")
            assertThat(subscription).isNotNull
            assertThat(subscription!!.tier).isEqualTo(SubscriptionTier.PRO)
            assertThat(subscription.stripeCustomerId).isEqualTo("cus_123")
            assertThat(subscription.stripeSubscriptionId).isEqualTo("sub_456")
            assertThat(subscription.status).isEqualTo(Subscription.SubscriptionStatus.ACTIVE)
        }

        @Test
        fun `should upgrade existing FREE subscription`() {
            // Create existing FREE subscription
            val existingSubscription = Subscription.createFree("user-1")
            subscriptionRepository.save(existingSubscription)

            val result = WebhookResult.SubscriptionCreated(
                stripeCustomerId = "cus_123",
                stripeSubscriptionId = "sub_456",
                userId = "user-1",
                tier = SubscriptionTier.STARTER,
                currentPeriodStart = Instant.now(),
                currentPeriodEnd = Instant.now().plusSeconds(30 * 24 * 60 * 60),
            )

            service.handleSubscriptionCreated(result)

            val subscription = subscriptionRepository.findByUserId("user-1")
            assertThat(subscription).isNotNull
            assertThat(subscription!!.tier).isEqualTo(SubscriptionTier.STARTER)
            assertThat(subscription.stripeCustomerId).isEqualTo("cus_123")
        }

        @Test
        fun `should throw when userId is missing`() {
            val result = WebhookResult.SubscriptionCreated(
                stripeCustomerId = "cus_123",
                stripeSubscriptionId = "sub_456",
                userId = null,
                tier = SubscriptionTier.PRO,
                currentPeriodStart = Instant.now(),
                currentPeriodEnd = Instant.now().plusSeconds(30 * 24 * 60 * 60),
            )

            assertThatThrownBy { service.handleSubscriptionCreated(result) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("No user_id in subscription metadata")
        }
    }

    @Nested
    inner class HandleSubscriptionUpdated {

        @Test
        fun `should update existing subscription tier and status`() {
            // Create existing subscription
            val existingSubscription = Subscription.createFree("user-1").apply {
                tier = SubscriptionTier.STARTER
                stripeCustomerId = "cus_123"
                stripeSubscriptionId = "sub_456"
            }
            subscriptionRepository.save(existingSubscription)

            val result = WebhookResult.SubscriptionUpdated(
                stripeCustomerId = "cus_123",
                stripeSubscriptionId = "sub_456",
                tier = SubscriptionTier.PRO,
                status = Subscription.SubscriptionStatus.ACTIVE,
                currentPeriodStart = Instant.now(),
                currentPeriodEnd = Instant.now().plusSeconds(30 * 24 * 60 * 60),
                cancelAtPeriodEnd = false,
            )

            service.handleSubscriptionUpdated(result)

            val subscription = subscriptionRepository.findByStripeCustomerId("cus_123")
            assertThat(subscription).isNotNull
            assertThat(subscription!!.tier).isEqualTo(SubscriptionTier.PRO)
        }

        @Test
        fun `should set cancelAtPeriodEnd when subscription is scheduled to cancel`() {
            val existingSubscription = Subscription.createFree("user-1").apply {
                tier = SubscriptionTier.PRO
                stripeCustomerId = "cus_123"
                stripeSubscriptionId = "sub_456"
            }
            subscriptionRepository.save(existingSubscription)

            val result = WebhookResult.SubscriptionUpdated(
                stripeCustomerId = "cus_123",
                stripeSubscriptionId = "sub_456",
                tier = SubscriptionTier.PRO,
                status = Subscription.SubscriptionStatus.ACTIVE,
                currentPeriodStart = Instant.now(),
                currentPeriodEnd = Instant.now().plusSeconds(30 * 24 * 60 * 60),
                cancelAtPeriodEnd = true,
            )

            service.handleSubscriptionUpdated(result)

            val subscription = subscriptionRepository.findByStripeCustomerId("cus_123")
            assertThat(subscription!!.cancelAtPeriodEnd).isTrue()
        }

        @Test
        fun `should throw when subscription not found`() {
            val result = WebhookResult.SubscriptionUpdated(
                stripeCustomerId = "cus_unknown",
                stripeSubscriptionId = "sub_456",
                tier = SubscriptionTier.PRO,
                status = Subscription.SubscriptionStatus.ACTIVE,
                currentPeriodStart = Instant.now(),
                currentPeriodEnd = Instant.now().plusSeconds(30 * 24 * 60 * 60),
                cancelAtPeriodEnd = false,
            )

            assertThatThrownBy { service.handleSubscriptionUpdated(result) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("No subscription found for Stripe customer")
        }
    }

    @Nested
    inner class HandleSubscriptionDeleted {

        @Test
        fun `should downgrade to FREE when subscription is deleted`() {
            val existingSubscription = Subscription.createFree("user-1").apply {
                tier = SubscriptionTier.PRO
                status = Subscription.SubscriptionStatus.ACTIVE
                stripeCustomerId = "cus_123"
                stripeSubscriptionId = "sub_456"
            }
            subscriptionRepository.save(existingSubscription)

            val result = WebhookResult.SubscriptionDeleted(
                stripeCustomerId = "cus_123",
                stripeSubscriptionId = "sub_456",
            )

            service.handleSubscriptionDeleted(result)

            val subscription = subscriptionRepository.findByStripeCustomerId("cus_123")
            assertThat(subscription).isNotNull
            assertThat(subscription!!.tier).isEqualTo(SubscriptionTier.FREE)
            assertThat(subscription.status).isEqualTo(Subscription.SubscriptionStatus.CANCELLED)
            assertThat(subscription.stripeSubscriptionId).isNull()
        }

        @Test
        fun `should do nothing when subscription not found`() {
            val result = WebhookResult.SubscriptionDeleted(
                stripeCustomerId = "cus_unknown",
                stripeSubscriptionId = "sub_456",
            )

            // Should not throw
            service.handleSubscriptionDeleted(result)
        }
    }

    @Nested
    inner class HandlePaymentFailed {

        @Test
        fun `should set status to PAST_DUE when payment fails`() {
            val existingSubscription = Subscription.createFree("user-1").apply {
                tier = SubscriptionTier.PRO
                status = Subscription.SubscriptionStatus.ACTIVE
                stripeCustomerId = "cus_123"
                stripeSubscriptionId = "sub_456"
            }
            subscriptionRepository.save(existingSubscription)

            val result = WebhookResult.PaymentFailed(
                stripeCustomerId = "cus_123",
                stripeSubscriptionId = "sub_456",
            )

            service.handlePaymentFailed(result)

            val subscription = subscriptionRepository.findByStripeCustomerId("cus_123")
            assertThat(subscription!!.status).isEqualTo(Subscription.SubscriptionStatus.PAST_DUE)
        }

        @Test
        fun `should do nothing when subscription not found`() {
            val result = WebhookResult.PaymentFailed(
                stripeCustomerId = "cus_unknown",
                stripeSubscriptionId = null,
            )

            // Should not throw
            service.handlePaymentFailed(result)
        }
    }

    // ====================================================================================
    // TEST DOUBLES
    // ====================================================================================

    private class InMemorySubscriptionRepository : SubscriptionRepository {
        private val subscriptions = mutableMapOf<UUID, Subscription>()

        override fun save(subscription: Subscription) {
            subscriptions[subscription.id] = subscription
        }

        override fun findById(id: UUID): Subscription? = subscriptions[id]

        override fun findByUserId(userId: String): Subscription? =
            subscriptions.values.find { it.userId == userId }

        override fun findByStripeCustomerId(customerId: String): Subscription? =
            subscriptions.values.find { it.stripeCustomerId == customerId }
    }

    private class ConfigurableQueueRepository : QueueRepository {
        private val queuesByOwner = mutableMapOf<String, List<Queue>>()

        fun setQueuesForOwner(ownerId: String, queues: List<Queue>) {
            queuesByOwner[ownerId] = queues
        }

        override fun save(queue: Queue) {}
        override fun findById(id: UUID) = null
        override fun findByName(name: String) = null
        override fun findByOwnerId(ownerId: String): List<Queue> =
            queuesByOwner[ownerId] ?: emptyList()
        override fun delete(id: UUID) {}
        override fun findQueuesNeedingTokenRotation(): List<Queue> = emptyList()
    }

    private class ConfigurableQueueMemberRepository : QueueMemberRepository {
        private val memberCounts = mutableMapOf<UUID, Int>()

        fun setMemberCount(queueId: UUID, count: Int) {
            memberCounts[queueId] = count
        }

        override fun save(member: QueueMember) {}
        override fun findById(id: UUID) = null
        override fun findByQueueId(queueId: UUID) = emptyList<QueueMember>()
        override fun findByUserId(userId: String) = emptyList<QueueMember>()
        override fun findByQueueIdAndUserId(queueId: UUID, userId: String) = null
        override fun delete(id: UUID) {}
        override fun countByQueueId(queueId: UUID) = memberCounts[queueId] ?: 0
    }

    private class InMemoryTierLimitRepository : TierLimitRepository {
        private val limits = mutableMapOf<SubscriptionTier, TierLimit>()

        override fun findByTier(tier: SubscriptionTier): TierLimit? = limits[tier]

        override fun findAll(): List<TierLimit> = limits.values.toList()

        override fun save(limit: TierLimit) {
            limits[limit.tier] = limit
        }
    }
}
