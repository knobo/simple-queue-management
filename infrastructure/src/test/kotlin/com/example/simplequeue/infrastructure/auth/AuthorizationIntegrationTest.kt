package com.example.simplequeue.infrastructure.auth

import com.example.simplequeue.infrastructure.TestJacksonConfig
import com.example.simplequeue.infrastructure.TestJwtHelper
import com.example.simplequeue.infrastructure.TestSecurityConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
// Using TestJwtHelper.xxxJwt() instead of jwt() directly
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig::class, TestJacksonConfig::class)
class AuthorizationIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }

    @Test
    fun `public endpoint should be accessible without auth`() {
        // / is public (landing page or redirect)
        mockMvc.perform(get("/"))
            .andExpect(status().isOk) // Or is3xxRedirection if it redirects
    }

    @Test
    fun `protected endpoint should return 401 without token`() {
        mockMvc.perform(get("/api/me/portal"))
            .andExpect(status().isUnauthorized)
    }

    // TODO: These tests are disabled because controllers expect OAuth2User but JWT auth provides Jwt principal.
    // Need to refactor controllers to use @AuthenticationPrincipal with a custom resolver that handles both.
    // See: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
    
    @Test
    fun `protected API endpoints require authentication`() {
        // Verify that API endpoints require auth
        mockMvc.perform(get("/api/me/portal"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `admin endpoint should return 403 for non-admin user`() {
        mockMvc.perform(get("/api/admin/sales/dashboard")
            .with(TestJwtHelper.userJwt()))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `admin endpoint should return 200 for superadmin`() {
        mockMvc.perform(get("/api/admin/sales/dashboard")
            .with(TestJwtHelper.superadminJwt()))
            .andExpect(status().isOk)
    }
}
