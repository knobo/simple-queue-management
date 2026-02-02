package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.Category
import com.example.simplequeue.domain.model.Location
import com.example.simplequeue.domain.model.Organization
import com.example.simplequeue.domain.model.OrganizationVisibility
import com.example.simplequeue.domain.port.OrganizationCategoryRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class JdbcOrganizationCategoryRepository(
    private val jdbcClient: JdbcClient,
) : OrganizationCategoryRepository {

    override fun link(organizationId: UUID, categoryId: UUID) {
        val sql = """
            INSERT INTO organization_categories (organization_id, category_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
        """
        jdbcClient
            .sql(sql)
            .param(organizationId)
            .param(categoryId)
            .update()
    }

    override fun unlink(organizationId: UUID, categoryId: UUID) {
        jdbcClient
            .sql("DELETE FROM organization_categories WHERE organization_id = ? AND category_id = ?")
            .param(organizationId)
            .param(categoryId)
            .update()
    }

    override fun findCategoriesByOrganizationId(organizationId: UUID): List<Category> =
        jdbcClient
            .sql("""
                SELECT c.* FROM categories c
                JOIN organization_categories oc ON c.id = oc.category_id
                WHERE oc.organization_id = ?
                ORDER BY c.sort_order, c.name_en
            """)
            .param(organizationId)
            .query(this::mapCategory)
            .list()

    override fun findOrganizationsByCategoryId(categoryId: UUID): List<Organization> =
        jdbcClient
            .sql("""
                SELECT o.* FROM organizations o
                JOIN organization_categories oc ON o.id = oc.organization_id
                WHERE oc.category_id = ? AND o.public_listed = TRUE AND o.status = 'ACTIVE'
                ORDER BY o.name
            """)
            .param(categoryId)
            .query(this::mapOrganization)
            .list()

    override fun unlinkAll(organizationId: UUID) {
        jdbcClient
            .sql("DELETE FROM organization_categories WHERE organization_id = ?")
            .param(organizationId)
            .update()
    }

    private fun mapCategory(rs: ResultSet, rowNum: Int): Category =
        Category(
            id = rs.getObject("id", UUID::class.java),
            slug = rs.getString("slug"),
            nameNo = rs.getString("name_no"),
            nameEn = rs.getString("name_en"),
            icon = rs.getString("icon"),
            parentId = rs.getObject("parent_id", UUID::class.java),
            sortOrder = rs.getInt("sort_order"),
        )

    private fun mapOrganization(rs: ResultSet, rowNum: Int): Organization =
        Organization(
            id = rs.getObject("id", UUID::class.java),
            name = rs.getString("name"),
            orgNumber = rs.getString("org_number"),
            createdBySellerId = rs.getObject("created_by_seller_id", UUID::class.java),
            adminEmail = rs.getString("admin_email"),
            adminUserId = rs.getString("admin_user_id"),
            subscriptionId = rs.getObject("subscription_id", UUID::class.java),
            status = Organization.OrganizationStatus.valueOf(rs.getString("status")),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            activatedAt = rs.getTimestamp("activated_at")?.toInstant(),
            description = rs.getString("description"),
            website = rs.getString("website"),
            contactEmail = rs.getString("contact_email"),
            phone = rs.getString("phone"),
            logoUrl = rs.getString("logo_url"),
            location = Location(
                streetAddress = rs.getString("street_address"),
                postalCode = rs.getString("postal_code"),
                city = rs.getString("city"),
                country = rs.getString("country") ?: "Norge",
                latitude = rs.getObject("latitude") as? Double,
                longitude = rs.getObject("longitude") as? Double,
            ),
            visibility = OrganizationVisibility(
                showDescription = rs.getBoolean("show_description"),
                showAddress = rs.getBoolean("show_address"),
                showPhone = rs.getBoolean("show_phone"),
                showEmail = rs.getBoolean("show_email"),
                showWebsite = rs.getBoolean("show_website"),
                showHours = rs.getBoolean("show_hours"),
                publicListed = rs.getBoolean("public_listed"),
            ),
        )
}
