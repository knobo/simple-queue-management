package com.example.simplequeue.domain.model

data class Location(
    val streetAddress: String?,
    val postalCode: String?,
    val city: String?,
    val country: String = "Norge",
    val latitude: Double?,
    val longitude: Double?,
) {
    fun hasAddress(): Boolean = !streetAddress.isNullOrBlank() || !city.isNullOrBlank()

    fun hasCoordinates(): Boolean = latitude != null && longitude != null

    fun formatAddress(): String? {
        val parts = listOfNotNull(
            streetAddress?.takeIf { it.isNotBlank() },
            listOfNotNull(
                postalCode?.takeIf { it.isNotBlank() },
                city?.takeIf { it.isNotBlank() }
            ).joinToString(" ").takeIf { it.isNotBlank() },
            country.takeIf { it.isNotBlank() && it != "Norge" }
        )
        return parts.joinToString(", ").takeIf { it.isNotBlank() }
    }

    companion object {
        fun empty(): Location = Location(
            streetAddress = null,
            postalCode = null,
            city = null,
            country = "Norge",
            latitude = null,
            longitude = null,
        )
    }
}
