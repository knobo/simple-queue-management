package com.example.simplequeue.domain.model

import java.time.Instant
import java.util.UUID

data class Organization(
    val id: UUID,
    val name: String,
    val orgNumber: String?,

    val createdBySellerId: UUID?,
    val adminEmail: String,
    val adminUserId: String?,

    val subscriptionId: UUID?,

    val status: OrganizationStatus,
    val createdAt: Instant,
    val activatedAt: Instant?,

    // Business info
    val description: String? = null,
    val website: String? = null,
    val contactEmail: String? = null,
    val phone: String? = null,
    val logoUrl: String? = null,

    // Location
    val location: Location = Location.empty(),

    // Visibility settings
    val visibility: OrganizationVisibility = OrganizationVisibility.default(),
) {
    enum class OrganizationStatus {
        PENDING,
        ACTIVE,
        SUSPENDED,
    }

    fun isActive(): Boolean = status == OrganizationStatus.ACTIVE

    fun isPending(): Boolean = status == OrganizationStatus.PENDING

    fun activate(adminUserId: String): Organization =
        copy(
            adminUserId = adminUserId,
            status = OrganizationStatus.ACTIVE,
            activatedAt = Instant.now(),
        )

    fun updateBusinessInfo(
        description: String? = this.description,
        website: String? = this.website,
        contactEmail: String? = this.contactEmail,
        phone: String? = this.phone,
        logoUrl: String? = this.logoUrl,
    ): Organization = copy(
        description = description,
        website = website,
        contactEmail = contactEmail,
        phone = phone,
        logoUrl = logoUrl,
    )

    fun updateLocation(location: Location): Organization = copy(location = location)

    fun updateVisibility(visibility: OrganizationVisibility): Organization =
        copy(visibility = visibility)

    companion object {
        fun create(
            name: String,
            orgNumber: String?,
            adminEmail: String,
            createdBySellerId: UUID?,
        ): Organization {
            val now = Instant.now()
            return Organization(
                id = UUID.randomUUID(),
                name = name,
                orgNumber = orgNumber,
                createdBySellerId = createdBySellerId,
                adminEmail = adminEmail,
                adminUserId = null,
                subscriptionId = null,
                status = OrganizationStatus.PENDING,
                createdAt = now,
                activatedAt = null,
                description = null,
                website = null,
                contactEmail = null,
                phone = null,
                logoUrl = null,
                location = Location.empty(),
                visibility = OrganizationVisibility.default(),
            )
        }
    }
}
