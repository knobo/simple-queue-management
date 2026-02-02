package com.example.simplequeue.domain.port

import com.example.simplequeue.domain.model.Category
import com.example.simplequeue.domain.model.Feedback
import com.example.simplequeue.domain.model.FeedbackCategory
import com.example.simplequeue.domain.model.FeedbackNote
import com.example.simplequeue.domain.model.FeedbackStatus
import com.example.simplequeue.domain.model.FeedbackType
import com.example.simplequeue.domain.model.CommissionEntry
import com.example.simplequeue.domain.model.Invite
import com.example.simplequeue.domain.model.Notification
import com.example.simplequeue.domain.model.OpeningHours
import com.example.simplequeue.domain.model.Organization
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.QueueClosedDate
import com.example.simplequeue.domain.model.QueueMember
import com.example.simplequeue.domain.model.QueueOpeningHours
import com.example.simplequeue.domain.model.QueueState
import com.example.simplequeue.domain.model.Seller
import com.example.simplequeue.domain.model.SellerActivityLog
import com.example.simplequeue.domain.model.SellerPayout
import com.example.simplequeue.domain.model.SellerReferral
import com.example.simplequeue.domain.model.Subscription
import com.example.simplequeue.domain.model.UserPreference
import com.example.simplequeue.domain.model.SubscriptionTier
import com.example.simplequeue.domain.model.Ticket
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

interface QueueRepository {
    fun save(queue: Queue)

    fun findById(id: UUID): Queue?

    fun findByName(name: String): Queue?

    fun findByOwnerId(ownerId: String): List<Queue>

    fun delete(id: UUID)

    /**
     * Find queues that need token rotation.
     * Returns queues where accessTokenMode is ROTATING and enough time has passed.
     */
    fun findQueuesNeedingTokenRotation(): List<Queue>
}

interface QueueAccessTokenRepository {
    fun save(token: com.example.simplequeue.domain.model.QueueAccessToken)

    fun findById(id: UUID): com.example.simplequeue.domain.model.QueueAccessToken?

    fun findByToken(token: String): com.example.simplequeue.domain.model.QueueAccessToken?

    fun findByQueueId(queueId: UUID): List<com.example.simplequeue.domain.model.QueueAccessToken>

    fun findActiveByQueueId(queueId: UUID): List<com.example.simplequeue.domain.model.QueueAccessToken>

    fun findCurrentToken(queueId: UUID): com.example.simplequeue.domain.model.QueueAccessToken?

    fun incrementUseCount(tokenId: UUID)

    fun deactivateOldTokens(queueId: UUID)

    fun deactivate(tokenId: UUID)

    fun delete(id: UUID)
}

interface QueueStateRepository {
    fun save(state: QueueState)

    fun findByQueueId(queueId: UUID): List<QueueState>

    fun deleteByQueueId(queueId: UUID)

    fun findByQueueIdAndStatus(
        queueId: UUID,
        status: Ticket.TicketStatus,
    ): List<QueueState>

    fun delete(id: UUID)
}

interface TicketRepository {
    fun save(ticket: Ticket)

    fun findById(id: UUID): Ticket?

    fun countByQueueId(queueId: UUID): Int

    fun findByQueueIdAndStatus(
        queueId: UUID,
        status: Ticket.TicketStatus,
    ): List<Ticket>

    fun getNextNumber(queueId: UUID): Int

    fun getLastCalledNumber(queueId: UUID): Int

    fun getAverageProcessingTimeSeconds(queueId: UUID): Double

    // Customer Portal methods
    /**
     * Find active ticket (WAITING or CALLED) for a user.
     * Returns the most recent active ticket if multiple exist.
     */
    fun findActiveByUserId(userId: String): Ticket?

    /**
     * Find ticket history (COMPLETED or CANCELLED) for a user.
     * Returns tickets ordered by completedAt/createdAt descending.
     */
    fun findHistoryByUserId(userId: String, limit: Int, offset: Int): List<Ticket>

    /**
     * Count total history tickets for a user.
     */
    fun countHistoryByUserId(userId: String): Int

    /**
     * Count position in queue for a ticket (how many WAITING tickets are ahead).
     */
    fun countPositionInQueue(queueId: UUID, ticketNumber: Int): Int
}

interface NotificationPort {
    fun notify(
        topic: String,
        message: String,
    )

    fun notify(notification: Notification)
}

interface EmailPort {
    fun sendTicketEmail(to: String, ticket: Ticket, queue: Queue)

    /**
     * Send an invite email to a user.
     * @param to The email address of the invitee
     * @param inviteToken The unique token for the invite
     * @param queueName The name of the queue
     * @param inviterName The name/identifier of the person who sent the invite
     * @param role The role being offered
     */
    fun sendInviteEmail(
        to: String,
        inviteToken: String,
        queueName: String,
        inviterName: String,
        role: String,
    )
}

interface SubscriptionRepository {
    fun save(subscription: Subscription)

    fun findById(id: UUID): Subscription?

    fun findByUserId(userId: String): Subscription?

    fun findByStripeCustomerId(customerId: String): Subscription?
}

interface QueueMemberRepository {
    fun save(member: QueueMember)

    fun findById(id: UUID): QueueMember?

    fun findByQueueId(queueId: UUID): List<QueueMember>

    fun findByUserId(userId: String): List<QueueMember>

    fun findByQueueIdAndUserId(queueId: UUID, userId: String): QueueMember?

    fun delete(id: UUID)

    fun countByQueueId(queueId: UUID): Int
}

interface InviteRepository {
    fun save(invite: Invite)

    fun findById(id: UUID): Invite?

    fun findByToken(token: String): Invite?

    fun findByQueueId(queueId: UUID): List<Invite>

    fun findPendingByQueueId(queueId: UUID): List<Invite>

    fun countPendingByQueueId(queueId: UUID): Int

    fun delete(id: UUID)
}

// =============================================================================
// Sales System Repositories
// =============================================================================

interface SellerRepository {
    fun save(seller: Seller)

    fun findById(id: UUID): Seller?

    fun findByUserId(userId: String): Seller?

    fun findByReferralCode(referralCode: String): Seller?

    fun findByStripeAccountId(stripeAccountId: String): Seller?

    fun findAll(): List<Seller>

    fun findByStatus(status: Seller.SellerStatus): List<Seller>

    fun countByStatusSince(status: Seller.SellerStatus, since: Instant): Int

    fun delete(id: UUID)
}

interface OrganizationRepository {
    fun save(organization: Organization)

    fun findById(id: UUID): Organization?

    fun findByAdminUserId(userId: String): List<Organization>

    fun findByAdminEmail(email: String): Organization?

    fun findBySellerId(sellerId: UUID): List<Organization>

    fun findAll(): List<Organization>

    fun findPublicListed(): List<Organization>

    fun findByCity(city: String): List<Organization>

    fun findNearby(latitude: Double, longitude: Double, radiusKm: Double): List<Organization>

    fun delete(id: UUID)
}

// =============================================================================
// Opening Hours & Categories
// =============================================================================

interface OpeningHoursRepository {
    fun save(hours: OpeningHours)

    fun saveAll(hours: List<OpeningHours>)

    fun findByOrganizationId(organizationId: UUID): List<OpeningHours>

    fun findByOrganizationIdAndDay(organizationId: UUID, dayOfWeek: DayOfWeek): OpeningHours?

    fun deleteByOrganizationId(organizationId: UUID)

    fun delete(id: UUID)
}

interface QueueOpeningHoursRepository {
    fun save(hours: QueueOpeningHours)

    fun saveAll(hours: List<QueueOpeningHours>)

    fun findByQueueId(queueId: UUID): List<QueueOpeningHours>

    fun findByQueueIdAndDay(queueId: UUID, dayOfWeek: DayOfWeek): QueueOpeningHours?

    fun deleteByQueueId(queueId: UUID)

    fun delete(id: UUID)
}

interface QueueClosedDateRepository {
    fun save(closedDate: QueueClosedDate)

    fun saveAll(closedDates: List<QueueClosedDate>)

    fun findByQueueId(queueId: UUID): List<QueueClosedDate>

    fun findByQueueIdAndDate(queueId: UUID, date: LocalDate): QueueClosedDate?

    fun deleteByQueueId(queueId: UUID)

    fun delete(id: UUID)
}

interface CategoryRepository {
    fun save(category: Category)

    fun findById(id: UUID): Category?

    fun findBySlug(slug: String): Category?

    fun findAll(): List<Category>

    fun findTopLevel(): List<Category>

    fun findByParentId(parentId: UUID): List<Category>

    fun delete(id: UUID)
}

interface OrganizationCategoryRepository {
    fun link(organizationId: UUID, categoryId: UUID)

    fun unlink(organizationId: UUID, categoryId: UUID)

    fun findCategoriesByOrganizationId(organizationId: UUID): List<Category>

    fun findOrganizationsByCategoryId(categoryId: UUID): List<Organization>

    fun unlinkAll(organizationId: UUID)
}

interface SellerReferralRepository {
    fun save(referral: SellerReferral)

    fun findById(id: UUID): SellerReferral?

    fun findBySellerId(sellerId: UUID): List<SellerReferral>

    fun findByCustomerUserId(userId: String): SellerReferral?

    fun findByOrganizationId(orgId: UUID): SellerReferral?

    fun findBySubscriptionId(subscriptionId: UUID): SellerReferral?

    fun findActiveBySellerIdSince(sellerId: UUID, since: Instant): List<SellerReferral>

    fun countBySellerId(sellerId: UUID): Int

    fun countBySellerIdSince(sellerId: UUID, since: Instant): Int
}

interface CommissionEntryRepository {
    fun save(entry: CommissionEntry)

    fun findById(id: UUID): CommissionEntry?

    fun findBySellerId(sellerId: UUID): List<CommissionEntry>

    fun findByReferralId(referralId: UUID): List<CommissionEntry>

    fun findUnpaidBySellerId(sellerId: UUID): List<CommissionEntry>

    fun findBySellerIdAndPeriod(sellerId: UUID, from: LocalDate, to: LocalDate): List<CommissionEntry>

    fun sumUnpaidBySellerId(sellerId: UUID): java.math.BigDecimal
}

interface SellerPayoutRepository {
    fun save(payout: SellerPayout)

    fun findById(id: UUID): SellerPayout?

    fun findBySellerId(sellerId: UUID): List<SellerPayout>

    fun findByStatus(status: SellerPayout.PayoutStatus): List<SellerPayout>

    fun findPendingBySellerId(sellerId: UUID): List<SellerPayout>
}

interface PayoutEntryRepository {
    fun link(payoutId: UUID, entryId: UUID)

    fun findEntriesByPayoutId(payoutId: UUID): List<UUID>

    fun findPayoutByEntryId(entryId: UUID): UUID?
}

interface SellerActivityLogRepository {
    fun save(log: SellerActivityLog)

    fun findBySellerId(sellerId: UUID): List<SellerActivityLog>

    fun findBySellerIdAndType(sellerId: UUID, type: SellerActivityLog.ActivityType): List<SellerActivityLog>

    fun countSalesBySellerIdSince(sellerId: UUID, since: Instant): Int
}

// =============================================================================
// Feedback System Repositories
// =============================================================================

interface FeedbackRepository {
    fun save(feedback: Feedback): Feedback

    fun findById(id: UUID): Feedback?

    fun findByUserId(userId: String): List<Feedback>

    fun findAll(): List<Feedback>

    fun findFiltered(
        type: FeedbackType? = null,
        status: FeedbackStatus? = null,
        category: FeedbackCategory? = null,
        search: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): List<Feedback>

    fun countByType(): Map<FeedbackType, Int>

    fun countByStatus(): Map<FeedbackStatus, Int>

    fun delete(id: UUID)
}

interface FeedbackNoteRepository {
    fun save(note: FeedbackNote): FeedbackNote

    fun findById(id: UUID): FeedbackNote?

    fun findByFeedbackId(feedbackId: UUID): List<FeedbackNote>

    fun delete(id: UUID)
}

// =============================================================================
// Tier Limits Repository
// =============================================================================

interface TierLimitRepository {
    fun findByTier(tier: SubscriptionTier): com.example.simplequeue.domain.model.TierLimit?

    fun findAll(): List<com.example.simplequeue.domain.model.TierLimit>

    fun save(limit: com.example.simplequeue.domain.model.TierLimit)
}

// =============================================================================
// Payment Gateway
// =============================================================================

/**
 * Result of processing a Stripe webhook event.
 */
sealed class WebhookResult {
    data class SubscriptionCreated(
        val stripeCustomerId: String,
        val stripeSubscriptionId: String,
        val userId: String?,
        val tier: SubscriptionTier,
        val currentPeriodStart: Instant,
        val currentPeriodEnd: Instant,
    ) : WebhookResult()

    data class SubscriptionUpdated(
        val stripeCustomerId: String,
        val stripeSubscriptionId: String,
        val tier: SubscriptionTier,
        val status: Subscription.SubscriptionStatus,
        val currentPeriodStart: Instant,
        val currentPeriodEnd: Instant,
        val cancelAtPeriodEnd: Boolean,
    ) : WebhookResult()

    data class SubscriptionDeleted(
        val stripeCustomerId: String,
        val stripeSubscriptionId: String,
    ) : WebhookResult()

    data class PaymentFailed(
        val stripeCustomerId: String,
        val stripeSubscriptionId: String?,
    ) : WebhookResult()

    data class InvoicePaid(
        val invoiceId: String,
        val stripeSubscriptionId: String,
        val stripeCustomerId: String,
        val amount: java.math.BigDecimal,
        val periodStart: Instant,
        val periodEnd: Instant,
    ) : WebhookResult()

    data object Ignored : WebhookResult()
}

// =============================================================================
// Stripe Connect Gateway (Seller Payouts)
// =============================================================================

/**
 * Result of processing a Stripe Connect webhook event.
 */
sealed class ConnectWebhookResult {
    data class AccountUpdated(
        val stripeAccountId: String,
        val chargesEnabled: Boolean,
        val payoutsEnabled: Boolean,
        val detailsSubmitted: Boolean,
    ) : ConnectWebhookResult()

    data class TransferCreated(
        val transferId: String,
        val stripeAccountId: String,
        val amount: Long,
        val currency: String,
    ) : ConnectWebhookResult()

    data object Ignored : ConnectWebhookResult()
}

/**
 * Port for Stripe Connect operations (seller payouts).
 */
interface SellerPaymentGateway {
    /**
     * Create a Stripe Express account for a seller.
     * @return The Stripe account ID.
     */
    fun createExpressAccount(
        email: String,
        country: String = "NO",
    ): String

    /**
     * Create an account link for onboarding a seller to Stripe Connect.
     * @return The onboarding URL to redirect the seller to.
     */
    fun createAccountLink(
        stripeAccountId: String,
        refreshUrl: String,
        returnUrl: String,
    ): String

    /**
     * Create a login link for the seller to access their Stripe Express Dashboard.
     * @return The dashboard URL.
     */
    fun createLoginLink(stripeAccountId: String): String

    /**
     * Transfer funds to a seller's Stripe Connect account.
     * @return The Stripe transfer ID.
     */
    fun transferToSeller(
        stripeAccountId: String,
        amount: Long,
        currency: String,
        description: String,
    ): String

    /**
     * Retrieve account status.
     */
    fun getAccountStatus(stripeAccountId: String): AccountStatus

    /**
     * Handle a Stripe Connect webhook event.
     */
    fun handleConnectWebhook(
        payload: String,
        signature: String,
    ): ConnectWebhookResult

    data class AccountStatus(
        val chargesEnabled: Boolean,
        val payoutsEnabled: Boolean,
        val detailsSubmitted: Boolean,
    )
}

/**
 * Port for payment gateway operations (Stripe).
 */
interface PaymentGateway {
    /**
     * Create a Stripe Checkout session for a user to subscribe to a tier.
     * @return The checkout session URL to redirect the user to.
     */
    fun createCheckoutSession(
        userId: String,
        tier: SubscriptionTier,
        successUrl: String,
        cancelUrl: String,
        customerEmail: String? = null,
    ): String

    /**
     * Create a Stripe Customer Portal session for managing subscription.
     * @return The portal session URL to redirect the user to.
     */
    fun createCustomerPortalSession(
        stripeCustomerId: String,
        returnUrl: String,
    ): String

    /**
     * Handle a Stripe webhook event.
     * @param payload The raw request body.
     * @param signature The Stripe-Signature header value.
     * @return The result of processing the event.
     */
    fun handleWebhookEvent(
        payload: String,
        signature: String,
    ): WebhookResult
}

// =============================================================================
// User Preferences Repository
// =============================================================================

interface UserPreferenceRepository {
    fun save(preference: UserPreference)

    fun findByUserId(userId: String): UserPreference?

    fun delete(userId: String)
}
