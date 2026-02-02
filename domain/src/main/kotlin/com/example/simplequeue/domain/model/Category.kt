package com.example.simplequeue.domain.model

import java.util.UUID

data class Category(
    val id: UUID,
    val slug: String,
    val nameNo: String,
    val nameEn: String,
    val icon: String?,
    val parentId: UUID?,
    val sortOrder: Int,
) {
    fun isTopLevel(): Boolean = parentId == null

    /**
     * Get localized name based on locale.
     */
    fun getName(locale: String): String {
        return when (locale.lowercase().take(2)) {
            "no", "nb", "nn" -> nameNo
            else -> nameEn
        }
    }

    companion object {
        fun create(
            slug: String,
            nameNo: String,
            nameEn: String,
            icon: String? = null,
            parentId: UUID? = null,
            sortOrder: Int = 0,
        ): Category {
            return Category(
                id = UUID.randomUUID(),
                slug = slug,
                nameNo = nameNo,
                nameEn = nameEn,
                icon = icon,
                parentId = parentId,
                sortOrder = sortOrder,
            )
        }
    }
}
