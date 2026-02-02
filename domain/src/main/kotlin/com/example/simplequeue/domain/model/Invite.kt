package com.example.simplequeue.domain.model

import java.time.Instant
import java.util.UUID

data class Invite(
    val id: UUID,
    val queueId: UUID,
    val email: String,
    val role: MemberRole,
    val token: String,
    var status: InviteStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
    var acceptedAt: Instant?,
    var acceptedByUserId: String?,
) {
    enum class InviteStatus {
        PENDING,
        ACCEPTED,
        DECLINED,
        EXPIRED,
        REVOKED,
    }

    companion object {
        private const val DEFAULT_EXPIRY_DAYS = 7L

        fun create(
            queueId: UUID,
            email: String,
            role: MemberRole,
            expiryDays: Long = DEFAULT_EXPIRY_DAYS,
        ): Invite {
            val now = Instant.now()
            return Invite(
                id = UUID.randomUUID(),
                queueId = queueId,
                email = email,
                role = role,
                token = UUID.randomUUID().toString(),
                status = InviteStatus.PENDING,
                createdAt = now,
                expiresAt = now.plusSeconds(expiryDays * 24 * 60 * 60),
                acceptedAt = null,
                acceptedByUserId = null,
            )
        }
    }

    fun isPending(): Boolean = status == InviteStatus.PENDING

    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    fun canBeAccepted(): Boolean = isPending() && !isExpired()

    fun accept(userId: String) {
        require(canBeAccepted()) { "Invite cannot be accepted" }
        status = InviteStatus.ACCEPTED
        acceptedAt = Instant.now()
        acceptedByUserId = userId
    }

    fun decline() {
        require(isPending()) { "Invite is not pending" }
        status = InviteStatus.DECLINED
    }

    fun revoke() {
        require(isPending()) { "Invite is not pending" }
        status = InviteStatus.REVOKED
    }

    fun markExpired() {
        if (isPending() && isExpired()) {
            status = InviteStatus.EXPIRED
        }
    }
}
