package com.example.simplequeue.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(properties = ["spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration"])
@Testcontainers
@ActiveProfiles("test")
@Import(TestSecurityConfig::class, TestEmailConfig::class, TestJacksonConfig::class)
class DatabaseSchemaTest {
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var applicationContext: org.springframework.context.ApplicationContext

    @Test
    fun `flyway should migrate database and create tables in public schema`() {
        // Debug beans
        println("Bean names: " + applicationContext.beanDefinitionNames.filter { it.contains("flyway", ignoreCase = true) })

        // Check if 'queues' table exists in 'public' schema
        val tableExists =
            jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'queues')",
                Boolean::class.java,
            )
        assertThat(tableExists).isTrue()

        // Check if 'tickets' table exists
        val ticketsExists =
            jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'tickets')",
                Boolean::class.java,
            )
        assertThat(ticketsExists).isTrue()
    }

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
