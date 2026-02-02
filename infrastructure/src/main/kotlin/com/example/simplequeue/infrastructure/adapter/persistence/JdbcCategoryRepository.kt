package com.example.simplequeue.infrastructure.adapter.persistence

import com.example.simplequeue.domain.model.Category
import com.example.simplequeue.domain.port.CategoryRepository
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class JdbcCategoryRepository(
    private val jdbcClient: JdbcClient,
) : CategoryRepository {

    override fun save(category: Category) {
        val sql = """
            INSERT INTO categories (id, slug, name_no, name_en, icon, parent_id, sort_order)
            VALUES (:id, :slug, :name_no, :name_en, :icon, :parent_id, :sort_order)
            ON CONFLICT (id) DO UPDATE SET
                slug = EXCLUDED.slug,
                name_no = EXCLUDED.name_no,
                name_en = EXCLUDED.name_en,
                icon = EXCLUDED.icon,
                parent_id = EXCLUDED.parent_id,
                sort_order = EXCLUDED.sort_order
        """
        jdbcClient
            .sql(sql)
            .param("id", category.id)
            .param("slug", category.slug)
            .param("name_no", category.nameNo)
            .param("name_en", category.nameEn)
            .param("icon", category.icon)
            .param("parent_id", category.parentId)
            .param("sort_order", category.sortOrder)
            .update()
    }

    override fun findById(id: UUID): Category? =
        jdbcClient
            .sql("SELECT * FROM categories WHERE id = ?")
            .param(id)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findBySlug(slug: String): Category? =
        jdbcClient
            .sql("SELECT * FROM categories WHERE slug = ?")
            .param(slug)
            .query(this::mapRow)
            .optional()
            .orElse(null)

    override fun findAll(): List<Category> =
        jdbcClient
            .sql("SELECT * FROM categories ORDER BY sort_order, name_en")
            .query(this::mapRow)
            .list()

    override fun findTopLevel(): List<Category> =
        jdbcClient
            .sql("SELECT * FROM categories WHERE parent_id IS NULL ORDER BY sort_order, name_en")
            .query(this::mapRow)
            .list()

    override fun findByParentId(parentId: UUID): List<Category> =
        jdbcClient
            .sql("SELECT * FROM categories WHERE parent_id = ? ORDER BY sort_order, name_en")
            .param(parentId)
            .query(this::mapRow)
            .list()

    override fun delete(id: UUID) {
        jdbcClient
            .sql("DELETE FROM categories WHERE id = ?")
            .param(id)
            .update()
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): Category =
        Category(
            id = rs.getObject("id", UUID::class.java),
            slug = rs.getString("slug"),
            nameNo = rs.getString("name_no"),
            nameEn = rs.getString("name_en"),
            icon = rs.getString("icon"),
            parentId = rs.getObject("parent_id", UUID::class.java),
            sortOrder = rs.getInt("sort_order"),
        )
}
