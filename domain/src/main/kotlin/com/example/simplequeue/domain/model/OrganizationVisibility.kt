package com.example.simplequeue.domain.model

data class OrganizationVisibility(
    val showDescription: Boolean = true,
    val showAddress: Boolean = true,
    val showPhone: Boolean = true,
    val showEmail: Boolean = true,
    val showWebsite: Boolean = true,
    val showHours: Boolean = true,
    val publicListed: Boolean = false,
) {
    companion object {
        fun default(): OrganizationVisibility = OrganizationVisibility()

        fun allVisible(): OrganizationVisibility = OrganizationVisibility(
            showDescription = true,
            showAddress = true,
            showPhone = true,
            showEmail = true,
            showWebsite = true,
            showHours = true,
            publicListed = true,
        )

        fun allHidden(): OrganizationVisibility = OrganizationVisibility(
            showDescription = false,
            showAddress = false,
            showPhone = false,
            showEmail = false,
            showWebsite = false,
            showHours = false,
            publicListed = false,
        )
    }
}
