package com.example.simplequeue.infrastructure.config

import com.example.simplequeue.application.usecase.GetAllFeedbackUseCase
import com.example.simplequeue.application.usecase.GetMyFeedbackUseCase
import com.example.simplequeue.application.usecase.SubmitFeedbackUseCase
import com.example.simplequeue.domain.port.FeedbackNoteRepository
import com.example.simplequeue.domain.port.FeedbackRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FeedbackConfig {

    @Bean
    fun submitFeedbackUseCase(
        feedbackRepository: FeedbackRepository,
    ): SubmitFeedbackUseCase = SubmitFeedbackUseCase(feedbackRepository)

    @Bean
    fun getMyFeedbackUseCase(
        feedbackRepository: FeedbackRepository,
    ): GetMyFeedbackUseCase = GetMyFeedbackUseCase(feedbackRepository)

    @Bean
    fun getAllFeedbackUseCase(
        feedbackRepository: FeedbackRepository,
        feedbackNoteRepository: FeedbackNoteRepository,
    ): GetAllFeedbackUseCase = GetAllFeedbackUseCase(feedbackRepository, feedbackNoteRepository)
}
