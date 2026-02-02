package com.example.simplequeue.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SellerPayout(
    val id: UUID,
    val sellerId: UUID,

    val amount: BigDecimal,
    val payoutMethod: Seller.PayoutMethod,
    val payoutReference: String?,

    val status: PayoutStatus,

    val entriesFrom: LocalDate,
    val entriesTo: LocalDate,

    val createdAt: Instant,
    val processedAt: Instant?,
    val completedAt: Instant?,
    val notes: String?,
) {
    enum class PayoutStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
    }

    fun isPending(): Boolean = status == PayoutStatus.PENDING

    fun isCompleted(): Boolean = status == PayoutStatus.COMPLETED

    fun markProcessing(reference: String?): SellerPayout =
        copy(
            status = PayoutStatus.PROCESSING,
            payoutReference = reference,
            processedAt = Instant.now(),
        )

    fun markCompleted(): SellerPayout =
        copy(
            status = PayoutStatus.COMPLETED,
            completedAt = Instant.now(),
        )

    fun markFailed(notes: String): SellerPayout =
        copy(
            status = PayoutStatus.FAILED,
            notes = notes,
        )

    companion object {
        fun create(
            sellerId: UUID,
            amount: BigDecimal,
            payoutMethod: Seller.PayoutMethod,
            entriesFrom: LocalDate,
            entriesTo: LocalDate,
            notes: String? = null,
        ): SellerPayout {
            return SellerPayout(
                id = UUID.randomUUID(),
                sellerId = sellerId,
                amount = amount,
                payoutMethod = payoutMethod,
                payoutReference = null,
                status = PayoutStatus.PENDING,
                entriesFrom = entriesFrom,
                entriesTo = entriesTo,
                createdAt = Instant.now(),
                processedAt = null,
                completedAt = null,
                notes = notes,
            )
        }
    }
}
