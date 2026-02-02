package com.example.simplequeue.infrastructure

import com.example.simplequeue.application.usecase.CreateQueueUseCase
import com.example.simplequeue.application.usecase.FeatureNotAllowedException
import com.example.simplequeue.application.usecase.SendInviteUseCase
import com.example.simplequeue.domain.model.MemberRole
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.Subscription
import com.example.simplequeue.domain.model.SubscriptionTier
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.SubscriptionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * Integration tests for tier limits enforcement at the use case level.
 * Verifies that use cases throw appropriate exceptions when limits are exceeded.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(TestSecurityConfig::class, TestEmailConfig::class, TestJacksonConfig::class)
class TierLimitsEnforcementIntegrationTest {

    @Autowired
    private lateinit var createQueueUseCase: CreateQueueUseCase

    @Autowired
    private lateinit var sendInviteUseCase: SendInviteUseCase

    @Autowired
    private lateinit var queueRepository: QueueRepository

    @Autowired
    private lateinit var subscriptionRepository: SubscriptionRepository

    @Autowired
    private lateinit var jdbcClient: JdbcClient

    private val testUserId = "test-user-${UUID.randomUUID()}"

    @BeforeEach
    fun setUp() {
        // Clean up test data
        jdbcClient.sql("DELETE FROM invites WHERE queue_id IN (SELECT id FROM queues WHERE owner_id LIKE 'test-user-%')")
            .update()
        jdbcClient.sql("DELETE FROM queue_members WHERE queue_id IN (SELECT id FROM queues WHERE owner_id LIKE 'test-user-%')")
            .update()
        jdbcClient.sql("DELETE FROM tickets WHERE queue_id IN (SELECT id FROM queues WHERE owner_id LIKE 'test-user-%')")
            .update()
        jdbcClient.sql("DELETE FROM queue_states WHERE queue_id IN (SELECT id FROM queues WHERE owner_id LIKE 'test-user-%')")
            .update()
        jdbcClient.sql("DELETE FROM queues WHERE owner_id LIKE 'test-user-%'").update()
        jdbcClient.sql("DELETE FROM subscriptions WHERE user_id LIKE 'test-user-%'").update()
    }

    @Nested
    inner class CreateQueueLimits {

        @Test
        fun `should throw FeatureNotAllowedException when FREE user tries to create second queue`() {
            // Given: FREE user already has 1 queue (the limit)
            createSubscription(testUserId, SubscriptionTier.FREE)
            createQueueUseCase.execute("First Queue", testUserId)

            // When/Then: Try to create another queue
            assertThatThrownBy { createQueueUseCase.execute("Second Queue", testUserId) }
                .isInstanceOf(FeatureNotAllowedException::class.java)
                .hasMessageContaining("maximum number of queues")
                .hasMessageContaining("Upgrade")
        }

        @Test
        fun `should allow FREE user to create first queue`() {
            // Given: FREE user with no queues
            createSubscription(testUserId, SubscriptionTier.FREE)

            // When: Create first queue
            val queue = createQueueUseCase.execute("First Queue", testUserId)

            // Then: Should succeed
            assertThat(queue).isNotNull
            assertThat(queue.name).isEqualTo("First Queue")
            assertThat(queue.ownerId).isEqualTo(testUserId)
        }

        @Test
        fun `should throw FeatureNotAllowedException when STARTER user exceeds 3 queue limit`() {
            // Given: STARTER user already has 3 queues (the limit)
            createSubscription(testUserId, SubscriptionTier.STARTER)
            createQueueUseCase.execute("Queue 1", testUserId)
            createQueueUseCase.execute("Queue 2", testUserId)
            createQueueUseCase.execute("Queue 3", testUserId)

            // When/Then: Try to create a 4th queue
            assertThatThrownBy { createQueueUseCase.execute("Queue 4", testUserId) }
                .isInstanceOf(FeatureNotAllowedException::class.java)
                .hasMessageContaining("3")
                .hasMessageContaining("STARTER")
        }

        @Test
        fun `should allow PRO user to create up to 10 queues`() {
            // Given: PRO user with 9 queues
            createSubscription(testUserId, SubscriptionTier.PRO)
            (1..9).forEach { createQueueUseCase.execute("Queue $it", testUserId) }

            // When: Create 10th queue
            val queue = createQueueUseCase.execute("Queue 10", testUserId)

            // Then: Should succeed
            assertThat(queue).isNotNull
            assertThat(queue.name).isEqualTo("Queue 10")
        }

        @Test
        fun `should throw FeatureNotAllowedException when PRO user exceeds 10 queue limit`() {
            // Given: PRO user already has 10 queues (the limit)
            createSubscription(testUserId, SubscriptionTier.PRO)
            (1..10).forEach { createQueueUseCase.execute("Queue $it", testUserId) }

            // When/Then: Try to create an 11th queue
            assertThatThrownBy { createQueueUseCase.execute("Queue 11", testUserId) }
                .isInstanceOf(FeatureNotAllowedException::class.java)
                .hasMessageContaining("10")
                .hasMessageContaining("PRO")
        }

        @Test
        fun `ENTERPRISE user should have unlimited queues`() {
            // Given: ENTERPRISE user
            createSubscription(testUserId, SubscriptionTier.ENTERPRISE)
            
            // When: Create many queues (more than PRO limit)
            (1..15).forEach { createQueueUseCase.execute("Queue $it", testUserId) }

            // Then: All should succeed
            val queues = queueRepository.findByOwnerId(testUserId)
            assertThat(queues).hasSize(15)
        }
    }

    @Nested
    inner class InviteOperatorLimits {

        @Test
        fun `should throw FeatureNotAllowedException when FREE user tries to invite operator`() {
            // Given: FREE user with a queue (FREE tier doesn't allow operators)
            createSubscription(testUserId, SubscriptionTier.FREE)
            val queue = createQueueUseCase.execute("Test Queue", testUserId)

            // When/Then: Try to invite an operator
            assertThatThrownBy {
                sendInviteUseCase.execute(
                    queueId = queue.id,
                    email = "operator@example.com",
                    role = MemberRole.OPERATOR,
                    inviterId = testUserId
                )
            }
                .isInstanceOf(FeatureNotAllowedException::class.java)
                .hasMessageContaining("operators")
                .hasMessageContaining("Upgrade")
        }

        @Test
        fun `should allow STARTER user to invite first operator`() {
            // Given: STARTER user with a queue (STARTER allows 2 operators)
            createSubscription(testUserId, SubscriptionTier.STARTER)
            val queue = createQueueUseCase.execute("Test Queue", testUserId)

            // When: Invite first operator
            val invite = sendInviteUseCase.execute(
                queueId = queue.id,
                email = "operator1@example.com",
                role = MemberRole.OPERATOR,
                inviterId = testUserId
            )

            // Then: Should succeed
            assertThat(invite).isNotNull
            assertThat(invite.email).isEqualTo("operator1@example.com")
        }

        @Test
        fun `should throw FeatureNotAllowedException when STARTER user exceeds 2 operator limit`() {
            // Given: STARTER user with a queue and 2 existing members
            createSubscription(testUserId, SubscriptionTier.STARTER)
            val queue = createQueueUseCase.execute("Test Queue", testUserId)
            
            // Add 2 existing queue members
            addQueueMember(queue.id, "member-1", testUserId)
            addQueueMember(queue.id, "member-2", testUserId)

            // When/Then: Try to invite a 3rd operator
            assertThatThrownBy {
                sendInviteUseCase.execute(
                    queueId = queue.id,
                    email = "operator3@example.com",
                    role = MemberRole.OPERATOR,
                    inviterId = testUserId
                )
            }
                .isInstanceOf(FeatureNotAllowedException::class.java)
        }

        @Test
        fun `should allow PRO user to invite up to 10 operators`() {
            // Given: PRO user with a queue and 9 existing members
            createSubscription(testUserId, SubscriptionTier.PRO)
            val queue = createQueueUseCase.execute("Test Queue", testUserId)
            
            // Add 9 existing queue members
            (1..9).forEach { addQueueMember(queue.id, "member-$it", testUserId) }

            // When: Invite 10th operator
            val invite = sendInviteUseCase.execute(
                queueId = queue.id,
                email = "operator10@example.com",
                role = MemberRole.OPERATOR,
                inviterId = testUserId
            )

            // Then: Should succeed
            assertThat(invite).isNotNull
        }
    }

    @Nested
    inner class ErrorMessageQuality {

        @Test
        fun `error message should mention current tier`() {
            // Given: FREE user at limit
            createSubscription(testUserId, SubscriptionTier.FREE)
            createQueueUseCase.execute("Queue 1", testUserId)

            // When/Then: Try to exceed limit
            assertThatThrownBy { createQueueUseCase.execute("Queue 2", testUserId) }
                .isInstanceOf(FeatureNotAllowedException::class.java)
                .hasMessageContaining("FREE")
        }

        @Test
        fun `error message should mention the limit number`() {
            // Given: STARTER user at limit
            createSubscription(testUserId, SubscriptionTier.STARTER)
            createQueueUseCase.execute("Queue 1", testUserId)
            createQueueUseCase.execute("Queue 2", testUserId)
            createQueueUseCase.execute("Queue 3", testUserId)

            // When/Then: Try to exceed limit
            assertThatThrownBy { createQueueUseCase.execute("Queue 4", testUserId) }
                .isInstanceOf(FeatureNotAllowedException::class.java)
                .hasMessageContaining("3")
        }

        @Test
        fun `error message should suggest upgrade path`() {
            // Given: FREE user at limit
            createSubscription(testUserId, SubscriptionTier.FREE)
            createQueueUseCase.execute("Queue 1", testUserId)

            // When/Then: Should suggest upgrading
            assertThatThrownBy { createQueueUseCase.execute("Queue 2", testUserId) }
                .isInstanceOf(FeatureNotAllowedException::class.java)
                .hasMessageMatching(".*[Uu]pgrade.*")
        }
    }

    @Nested
    inner class UpgradeBehavior {

        @Test
        fun `user can create more queues after upgrading tier`() {
            // Given: FREE user at limit (1 queue)
            createSubscription(testUserId, SubscriptionTier.FREE)
            createQueueUseCase.execute("Queue 1", testUserId)
            
            // Verify cannot create more
            assertThatThrownBy { createQueueUseCase.execute("Queue 2", testUserId) }
                .isInstanceOf(FeatureNotAllowedException::class.java)

            // When: Upgrade to STARTER
            val subscription = subscriptionRepository.findByUserId(testUserId)!!
            subscription.tier = SubscriptionTier.STARTER
            subscription.status = Subscription.SubscriptionStatus.ACTIVE
            subscriptionRepository.save(subscription)

            // Then: Can now create more queues
            val queue2 = createQueueUseCase.execute("Queue 2", testUserId)
            assertThat(queue2).isNotNull
            
            val queue3 = createQueueUseCase.execute("Queue 3", testUserId)
            assertThat(queue3).isNotNull
        }

        @Test
        fun `user can invite operators after upgrading from FREE to STARTER`() {
            // Given: FREE user with a queue (cannot invite operators)
            createSubscription(testUserId, SubscriptionTier.FREE)
            val queue = createQueueUseCase.execute("Test Queue", testUserId)
            
            // Verify cannot invite
            assertThatThrownBy {
                sendInviteUseCase.execute(queue.id, "op@test.com", MemberRole.OPERATOR, testUserId)
            }.isInstanceOf(FeatureNotAllowedException::class.java)

            // When: Upgrade to STARTER
            val subscription = subscriptionRepository.findByUserId(testUserId)!!
            subscription.tier = SubscriptionTier.STARTER
            subscriptionRepository.save(subscription)

            // Then: Can now invite operators
            val invite = sendInviteUseCase.execute(
                queueId = queue.id,
                email = "operator@test.com",
                role = MemberRole.OPERATOR,
                inviterId = testUserId
            )
            assertThat(invite).isNotNull
        }
    }

    // ====================================================================================
    // HELPER METHODS
    // ====================================================================================

    private fun createSubscription(userId: String, tier: SubscriptionTier) {
        val subscription = Subscription.createFree(userId).apply {
            this.tier = tier
            this.status = Subscription.SubscriptionStatus.ACTIVE
        }
        subscriptionRepository.save(subscription)
    }

    private fun addQueueMember(queueId: UUID, userId: String, invitedBy: String) {
        jdbcClient.sql("""
            INSERT INTO queue_members (id, queue_id, user_id, role, joined_at, invited_by)
            VALUES (:id, :queueId, :userId, 'OPERATOR', :joinedAt, :invitedBy)
        """)
            .param("id", UUID.randomUUID())
            .param("queueId", queueId)
            .param("userId", userId)
            .param("joinedAt", java.sql.Timestamp.from(Instant.now()))
            .param("invitedBy", invitedBy)
            .update()
    }

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
