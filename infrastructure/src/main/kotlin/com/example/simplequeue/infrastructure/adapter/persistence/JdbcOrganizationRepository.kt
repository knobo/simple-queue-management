package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.Location
import com.example.simplequeue.domain.model.Organization
import com.example.simplequeue.domain.model.OrganizationVisibility
import com.example.simplequeue.domain.port.OrganizationRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcOrganizationRepository(
    private val jdbcClient: JdbcClient,
) : OrganizationRepository {

    override fun save(organization: Organization) {
        val sql = """
            INSERT INTO organizations (
                id, name, org_number,
                created_by_seller_id, admin_email, admin_user_id,
                subscription_id, status, created_at, activated_at,
                description, website, contact_email, phone, logo_url,
                street_address, postal_code, city, country, latitude, longitude,
                show_description, show_address, show_phone, show_email, 
                show_website, show_hours, public_listed
            )
            VALUES (
                :id, :name, :org_number,
                :created_by_seller_id, :admin_email, :admin_user_id,
                :subscription_id, :status, :created_at, :activated_at,
                :description, :website, :contact_email, :phone, :logo_url,
                :street_address, :postal_code, :city, :country, :latitude, :longitude,
                :show_description, :show_address, :show_phone, :show_email,
                :show_website, :show_hours, :public_listed
            )
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                org_number = EXCLUDED.org_number,
                admin_email = EXCLUDED.admin_email,
                admin_user_id = EXCLUDED.admin_user_id,
                subscription_id = EXCLUDED.subscription_id,
                status = EXCLUDED.status,
                activated_at = EXCLUDED.activated_at,
                description = EXCLUDED.description,
                website = EXCLUDED.website,
                contact_email = EXCLUDED.contact_email,
                phone = EXCLUDED.phone,
                logo_url = EXCLUDED.logo_url,
                street_address = EXCLUDED.street_address,
                postal_code = EXCLUDED.postal_code,
                city = EXCLUDED.city,
                country = EXCLUDED.country,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                show_description = EXCLUDED.show_description,
                show_address = EXCLUDED.show_address,
                show_phone = EXCLUDED.show_phone,
                show_email = EXCLUDED.show_email,
                show_website = EXCLUDED.show_website,
                show_hours = EXCLUDED.show_hours,
                public_listed = EXCLUDED.public_listed
        """
        jdbcClient
            .sql(sql)
            .param("id", organization.id)
            .param("name", organization.name)
            .param("org_number", organization.orgNumber)
            .param("created_by_seller_id", organization.createdBySellerId)
            .param("admin_email", organization.adminEmail)
            .param("admin_user_id", organization.adminUserId)
            .param("subscription_id", organization.subscriptionId)
            .param("status", organization.status.name)
            .param("created_at", Timestamp.from(organization.createdAt))
            .param("activated_at", organization.activatedAt?.let { Timestamp.from(it) })
            .param("description", organization.description)
            .param("website", organization.website)
            .param("contact_email", organization.contactEmail)
            .param("phone", organization.phone)
            .param("logo_url", organization.logoUrl)
            .param("street_address", organization.location.streetAddress)
            .param("postal_code", organization.location.postalCode)
            .param("city", organization.location.city)
            .param("country", organization.location.country)
            .param("latitude", organization.location.latitude)
            .param("longitude", organization.location.longitude)
            .param("show_description", organization.visibility.showDescription)
            .param("show_address", organization.visibility.showAddress)
            .param("show_phone", organization.visibility.showPhone)
            .param("show_email", organization.visibility.showEmail)
            .param("show_website", organization.visibility.showWebsite)
            .param("show_hours", organization.visibility.showHours)
            .param("public_listed", organization.visibility.publicListed)
            .update()
    }

    override fun findById(id: UUID): Organization? =
        jdbcClient
            .sql("SELECT * FROM organizations WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findByAdminUserId(userId: String): List<Organization> =
        jdbcClient
            .sql("SELECT * FROM organizations WHERE admin_user_id = ? ORDER BY created_at DESC")
            .param(userId)
            .query(this::mapRow)
            .list()

    override fun findByAdminEmail(email: String): Organization? =
        jdbcClient
            .sql("SELECT * FROM organizations WHERE admin_email = ? AND status = 'PENDING'")
            .param(email)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findBySellerId(sellerId: UUID): List<Organization> =
        jdbcClient
            .sql("SELECT * FROM organizations WHERE created_by_seller_id = ? ORDER BY created_at DESC")
            .param(sellerId)
            .query(this::mapRow)
            .list()

    override fun findAll(): List<Organization> =
        jdbcClient
            .sql("SELECT * FROM organizations ORDER BY created_at DESC")
            .query(this::mapRow)
            .list()

    override fun findPublicListed(): List<Organization> =
        jdbcClient
            .sql("SELECT * FROM organizations WHERE public_listed = TRUE AND status = 'ACTIVE' ORDER BY name")
            .query(this::mapRow)
            .list()

    override fun findByCity(city: String): List<Organization> =
        jdbcClient
            .sql("""
                SELECT * FROM organizations 
                WHERE city ILIKE ? AND public_listed = TRUE AND status = 'ACTIVE' 
                ORDER BY name
            """)
            .param("%$city%")
            .query(this::mapRow)
            .list()

    override fun findNearby(latitude: Double, longitude: Double, radiusKm: Double): List<Organization> {
        // Using Haversine formula for distance calculation
        val sql = """
            SELECT *, (
                6371 * acos(
                    cos(radians(:lat)) * cos(radians(latitude)) *
                    cos(radians(longitude) - radians(:lng)) +
                    sin(radians(:lat)) * sin(radians(latitude))
                )
            ) AS distance
            FROM organizations
            WHERE latitude IS NOT NULL 
              AND longitude IS NOT NULL 
              AND public_listed = TRUE 
              AND status = 'ACTIVE'
            HAVING distance < :radius
            ORDER BY distance
        """
        return jdbcClient
            .sql(sql)
            .param("lat", latitude)
            .param("lng", longitude)
            .param("radius", radiusKm)
            .query(this::mapRow)
            .list()
    }

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM organizations WHERE id = ?")
            .param(id)
            .update()
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): Organization =
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
