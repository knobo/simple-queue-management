package com.example.simplequeue.infrastructure.config

import com.example.simplequeue.application.service.CommissionService
import com.example.simplequeue.application.service.QueueNotificationService
import com.example.simplequeue.application.service.QueueOpeningHoursService
import com.example.simplequeue.application.service.ReferralService
import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.application.usecase.AcceptInviteUseCase
import com.example.simplequeue.application.usecase.CompleteTicketUseCase
import com.example.simplequeue.application.usecase.CreateCounterUseCase
import com.example.simplequeue.application.usecase.CreateOrganizationUseCase
import com.example.simplequeue.application.usecase.CreateQueueUseCase
import com.example.simplequeue.application.usecase.CreateSellerUseCase
import com.example.simplequeue.application.usecase.DeclineInviteUseCase
import com.example.simplequeue.application.usecase.DeleteCounterUseCase
import com.example.simplequeue.application.usecase.EndCounterSessionUseCase
import com.example.simplequeue.application.usecase.GetOperatorSessionUseCase
import com.example.simplequeue.application.usecase.ListCountersUseCase
import com.example.simplequeue.application.usecase.DeleteQueueUseCase
import com.example.simplequeue.application.usecase.GetAdminSalesDashboardUseCase
import com.example.simplequeue.application.usecase.GetCustomerPortalUseCase
import com.example.simplequeue.application.usecase.GetMyQueuesUseCase
import com.example.simplequeue.application.usecase.GetMyTicketHistoryUseCase
import com.example.simplequeue.application.usecase.GetQueueStatusUseCase
import com.example.simplequeue.application.usecase.GetSellerDashboardUseCase
import com.example.simplequeue.application.usecase.IssueTicketUseCase
import com.example.simplequeue.application.usecase.ManageQueueStateUseCase
import com.example.simplequeue.application.usecase.RemoveMemberUseCase
import com.example.simplequeue.application.usecase.RevokeInviteUseCase
import com.example.simplequeue.application.usecase.RevokeTicketUseCase
import com.example.simplequeue.application.usecase.SendInviteUseCase
import com.example.simplequeue.application.usecase.SendTicketEmailUseCase
import com.example.simplequeue.application.usecase.ServeTicketUseCase
import com.example.simplequeue.application.usecase.StartCounterSessionUseCase
import com.example.simplequeue.application.usecase.UpdateCounterUseCase
import com.example.simplequeue.application.usecase.UpdateMemberRoleUseCase
import com.example.simplequeue.domain.port.CommissionEntryRepository
import com.example.simplequeue.domain.port.CounterRepository
import com.example.simplequeue.domain.port.CounterSessionRepository
import com.example.simplequeue.domain.port.EmailPort
import com.example.simplequeue.domain.port.InviteRepository
import com.example.simplequeue.domain.port.NotificationPort
import com.example.simplequeue.domain.port.OrganizationRepository
import com.example.simplequeue.domain.port.QueueClosedDateRepository
import com.example.simplequeue.domain.port.QueueMemberRepository
import com.example.simplequeue.domain.port.QueueOpeningHoursRepository
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.QueueStateRepository
import com.example.simplequeue.domain.port.SellerActivityLogRepository
import com.example.simplequeue.domain.port.SellerPayoutRepository
import com.example.simplequeue.domain.port.SellerReferralRepository
import com.example.simplequeue.domain.port.SellerRepository
import com.example.simplequeue.domain.port.SubscriptionRepository
import com.example.simplequeue.domain.port.QueueAccessTokenRepository
import com.example.simplequeue.domain.port.TicketRepository
import com.example.simplequeue.domain.port.TierLimitRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class UseCaseConfig {
    @Bean
    open fun queueNotificationService(
        notificationPort: NotificationPort,
        ticketRepository: TicketRepository,
    ): QueueNotificationService = QueueNotificationService(notificationPort, ticketRepository)

    @Bean
    open fun issueTicketUseCase(
        queueRepository: QueueRepository,
        ticketRepository: TicketRepository,
        queueStateRepository: QueueStateRepository,
        queueNotificationService: QueueNotificationService,
        accessTokenRepository: QueueAccessTokenRepository,
        queueOpeningHoursService: QueueOpeningHoursService,
    ): IssueTicketUseCase = IssueTicketUseCase(
        queueRepository,
        ticketRepository,
        queueStateRepository,
        queueNotificationService,
        accessTokenRepository,
        queueOpeningHoursService,
    )

    @Bean
    open fun getQueueStatusUseCase(ticketRepository: TicketRepository): GetQueueStatusUseCase = GetQueueStatusUseCase(ticketRepository)

    @Bean
    open fun serveTicketUseCase(
        ticketRepository: TicketRepository,
        queueStateRepository: QueueStateRepository,
        queueRepository: QueueRepository,
        queueNotificationService: QueueNotificationService,
        counterRepository: CounterRepository,
        counterSessionRepository: CounterSessionRepository,
    ): ServeTicketUseCase = ServeTicketUseCase(
        ticketRepository,
        queueStateRepository,
        queueRepository,
        queueNotificationService,
        counterRepository,
        counterSessionRepository,
    )

    @Bean
    open fun completeTicketUseCase(
        ticketRepository: TicketRepository,
        queueStateRepository: QueueStateRepository,
        queueRepository: QueueRepository,
        queueNotificationService: QueueNotificationService,
        counterRepository: CounterRepository,
    ): CompleteTicketUseCase = CompleteTicketUseCase(
        ticketRepository,
        queueStateRepository,
        queueRepository,
        queueNotificationService,
        counterRepository,
    )

    @Bean
    open fun revokeTicketUseCase(
        ticketRepository: TicketRepository,
        queueRepository: QueueRepository,
        queueNotificationService: QueueNotificationService,
    ): RevokeTicketUseCase = RevokeTicketUseCase(ticketRepository, queueRepository, queueNotificationService)

    @Bean
    open fun deleteQueueUseCase(
        queueRepository: QueueRepository,
        ticketRepository: TicketRepository,
    ): DeleteQueueUseCase = DeleteQueueUseCase(queueRepository, ticketRepository)

    @Bean
    open fun subscriptionService(
        subscriptionRepository: SubscriptionRepository,
        queueRepository: QueueRepository,
        queueMemberRepository: QueueMemberRepository,
        tierLimitRepository: TierLimitRepository,
        sellerReferralRepository: SellerReferralRepository,
    ): SubscriptionService = SubscriptionService(subscriptionRepository, queueRepository, queueMemberRepository, tierLimitRepository, sellerReferralRepository)

    @Bean
    open fun createQueueUseCase(
        queueRepository: QueueRepository,
        queueStateRepository: QueueStateRepository,
        subscriptionService: SubscriptionService,
    ): CreateQueueUseCase = CreateQueueUseCase(queueRepository, queueStateRepository, subscriptionService)

    @Bean
    open fun manageQueueStateUseCase(queueStateRepository: QueueStateRepository): ManageQueueStateUseCase =
        ManageQueueStateUseCase(queueStateRepository)

    @Bean
    open fun sendTicketEmailUseCase(
        ticketRepository: TicketRepository,
        queueRepository: QueueRepository,
        emailPort: EmailPort,
    ): SendTicketEmailUseCase = SendTicketEmailUseCase(ticketRepository, queueRepository, emailPort)

    @Bean
    open fun sendInviteUseCase(
        inviteRepository: InviteRepository,
        queueRepository: QueueRepository,
        subscriptionService: SubscriptionService,
    ): SendInviteUseCase = SendInviteUseCase(inviteRepository, queueRepository, subscriptionService)

    @Bean
    open fun acceptInviteUseCase(
        inviteRepository: InviteRepository,
        queueMemberRepository: QueueMemberRepository,
        queueRepository: QueueRepository,
    ): AcceptInviteUseCase = AcceptInviteUseCase(inviteRepository, queueMemberRepository, queueRepository)

    @Bean
    open fun declineInviteUseCase(
        inviteRepository: InviteRepository,
    ): DeclineInviteUseCase = DeclineInviteUseCase(inviteRepository)

    @Bean
    open fun revokeInviteUseCase(
        inviteRepository: InviteRepository,
        queueRepository: QueueRepository,
    ): RevokeInviteUseCase = RevokeInviteUseCase(inviteRepository, queueRepository)

    @Bean
    open fun removeMemberUseCase(
        queueMemberRepository: QueueMemberRepository,
        queueRepository: QueueRepository,
    ): RemoveMemberUseCase = RemoveMemberUseCase(queueMemberRepository, queueRepository)

    @Bean
    open fun updateMemberRoleUseCase(
        queueMemberRepository: QueueMemberRepository,
        queueRepository: QueueRepository,
    ): UpdateMemberRoleUseCase = UpdateMemberRoleUseCase(queueMemberRepository, queueRepository)

    @Bean
    open fun getMyQueuesUseCase(
        queueRepository: QueueRepository,
        queueMemberRepository: QueueMemberRepository,
    ): GetMyQueuesUseCase = GetMyQueuesUseCase(queueRepository, queueMemberRepository)

    // =============================================================================
    // Counter Use Cases
    // =============================================================================

    @Bean
    open fun createCounterUseCase(
        counterRepository: CounterRepository,
        queueRepository: QueueRepository,
        subscriptionService: SubscriptionService,
    ): CreateCounterUseCase = CreateCounterUseCase(counterRepository, queueRepository, subscriptionService)

    @Bean
    open fun deleteCounterUseCase(
        counterRepository: CounterRepository,
    ): DeleteCounterUseCase = DeleteCounterUseCase(counterRepository)

    @Bean
    open fun listCountersUseCase(
        counterRepository: CounterRepository,
    ): ListCountersUseCase = ListCountersUseCase(counterRepository)

    @Bean
    open fun updateCounterUseCase(
        counterRepository: CounterRepository,
        queueRepository: QueueRepository,
    ): UpdateCounterUseCase = UpdateCounterUseCase(counterRepository, queueRepository)

    @Bean
    open fun startCounterSessionUseCase(
        counterRepository: CounterRepository,
        counterSessionRepository: CounterSessionRepository,
    ): StartCounterSessionUseCase = StartCounterSessionUseCase(counterRepository, counterSessionRepository)

    @Bean
    open fun endCounterSessionUseCase(
        counterRepository: CounterRepository,
        counterSessionRepository: CounterSessionRepository,
    ): EndCounterSessionUseCase = EndCounterSessionUseCase(counterRepository, counterSessionRepository)

    @Bean
    open fun getOperatorSessionUseCase(
        counterRepository: CounterRepository,
        counterSessionRepository: CounterSessionRepository,
    ): GetOperatorSessionUseCase = GetOperatorSessionUseCase(counterRepository, counterSessionRepository)

    // =============================================================================
    // Customer Portal Use Cases
    // =============================================================================

    @Bean
    open fun getCustomerPortalUseCase(
        ticketRepository: TicketRepository,
        queueRepository: QueueRepository,
    ): GetCustomerPortalUseCase = GetCustomerPortalUseCase(ticketRepository, queueRepository)

    @Bean
    open fun getMyTicketHistoryUseCase(
        ticketRepository: TicketRepository,
        queueRepository: QueueRepository,
    ): GetMyTicketHistoryUseCase = GetMyTicketHistoryUseCase(ticketRepository, queueRepository)

    // =============================================================================
    // Sales System Use Cases
    // =============================================================================

    @Bean
    open fun referralService(
        sellerRepository: SellerRepository,
        sellerReferralRepository: SellerReferralRepository,
    ): ReferralService = ReferralService(sellerRepository, sellerReferralRepository)

    @Bean
    open fun commissionService(
        sellerRepository: SellerRepository,
        sellerReferralRepository: SellerReferralRepository,
        commissionEntryRepository: CommissionEntryRepository,
    ): CommissionService = CommissionService(sellerRepository, sellerReferralRepository, commissionEntryRepository)

    @Bean
    open fun createSellerUseCase(
        sellerRepository: SellerRepository,
    ): CreateSellerUseCase = CreateSellerUseCase(sellerRepository)

    @Bean
    open fun createOrganizationUseCase(
        organizationRepository: OrganizationRepository,
        sellerRepository: SellerRepository,
        sellerReferralRepository: SellerReferralRepository,
        sellerActivityLogRepository: SellerActivityLogRepository,
    ): CreateOrganizationUseCase = CreateOrganizationUseCase(
        organizationRepository,
        sellerRepository,
        sellerReferralRepository,
        sellerActivityLogRepository,
    )

    @Bean
    open fun getSellerDashboardUseCase(
        sellerRepository: SellerRepository,
        sellerReferralRepository: SellerReferralRepository,
        organizationRepository: OrganizationRepository,
        commissionEntryRepository: CommissionEntryRepository,
    ): GetSellerDashboardUseCase = GetSellerDashboardUseCase(
        sellerRepository,
        sellerReferralRepository,
        organizationRepository,
        commissionEntryRepository,
    )

    @Bean
    open fun getAdminSalesDashboardUseCase(
        sellerRepository: SellerRepository,
        sellerReferralRepository: SellerReferralRepository,
        organizationRepository: OrganizationRepository,
        commissionEntryRepository: CommissionEntryRepository,
        sellerPayoutRepository: SellerPayoutRepository,
    ): GetAdminSalesDashboardUseCase = GetAdminSalesDashboardUseCase(
        sellerRepository,
        sellerReferralRepository,
        organizationRepository,
        commissionEntryRepository,
        sellerPayoutRepository,
    )

    // =============================================================================
    // Queue Opening Hours Service
    // =============================================================================

    @Bean
    open fun queueOpeningHoursService(
        queueOpeningHoursRepository: QueueOpeningHoursRepository,
        queueClosedDateRepository: QueueClosedDateRepository,
    ): QueueOpeningHoursService = QueueOpeningHoursService(
        queueOpeningHoursRepository,
        queueClosedDateRepository,
    )

}
