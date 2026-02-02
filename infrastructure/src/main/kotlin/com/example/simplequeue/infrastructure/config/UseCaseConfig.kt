package com.example.simplequeue.infrastructure.config

import com.example.simplequeue.application.service.CommissionService
import com.example.simplequeue.application.service.ReferralService
import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.application.usecase.CreateOrganizationUseCase
import com.example.simplequeue.application.usecase.CreateSellerUseCase
import com.example.simplequeue.application.usecase.GetAdminSalesDashboardUseCase
import com.example.simplequeue.application.usecase.GetSellerDashboardUseCase
import com.example.simplequeue.domain.port.CommissionEntryRepository
import com.example.simplequeue.domain.port.OrganizationRepository
import com.example.simplequeue.domain.port.SellerActivityLogRepository
import com.example.simplequeue.domain.port.SellerPayoutRepository
import com.example.simplequeue.domain.port.SellerReferralRepository
import com.example.simplequeue.domain.port.SellerRepository
import com.example.simplequeue.domain.port.SubscriptionRepository
import com.example.simplequeue.domain.port.TierLimitRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Management Platform UseCase Configuration.
 * Provides beans for subscription, seller, and organization management.
 * Note: Queue Core functionality (tickets, queues, counters) is handled by Queue Core service.
 */
@Configuration
class UseCaseConfig {

    @Bean
    open fun subscriptionService(
        subscriptionRepository: SubscriptionRepository,
        tierLimitRepository: TierLimitRepository,
        sellerReferralRepository: SellerReferralRepository,
    ): SubscriptionService = SubscriptionService(
        subscriptionRepository = subscriptionRepository,
        queueRepository = null, // Not needed in Management Platform
        queueMemberRepository = null, // Not needed in Management Platform
        tierLimitRepository = tierLimitRepository,
        sellerReferralRepository = sellerReferralRepository,
    )

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

}
