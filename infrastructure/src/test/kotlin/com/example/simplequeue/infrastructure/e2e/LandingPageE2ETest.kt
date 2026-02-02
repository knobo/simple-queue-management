package com.example.simplequeue.infrastructure.e2e

import com.example.simplequeue.infrastructure.TestEmailConfig
import com.example.simplequeue.infrastructure.TestJacksonConfig
import com.example.simplequeue.infrastructure.TestSecurityConfig
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import java.nio.file.Paths
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

// @Disabled("E2E tests require Playwright browser setup - run manually or in CI with browser installed")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@Import(TestSecurityConfig::class, TestEmailConfig::class, TestJacksonConfig::class)
class LandingPageE2ETest {
    companion object {
        @Container
        @ServiceConnection
        val postgres =
            PostgreSQLContainer<Nothing>("postgres:18-alpine").apply {
                start()
            }
    }

    @LocalServerPort
    private var port: Int = 0

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var context: BrowserContext
    private lateinit var page: Page

    @BeforeAll
    fun setup() {
        playwright = Playwright.create()
        browser =
            playwright.chromium().launch(
                BrowserType
                    .LaunchOptions()
                    .setHeadless(true),
            )
        context = browser.newContext(
            Browser.NewContextOptions()
                .setRecordVideoDir(Paths.get("/tmp/playwright-videos"))
                .setRecordVideoSize(1280, 720)
        )
    }

    @BeforeEach
    fun createPage() {
        page = context.newPage()
    }

    @AfterAll
    fun tearDown() {
        context.close()  // This saves the video
        browser.close()
        playwright.close()
        println("🎬 Videos saved to /tmp/playwright-videos/")
    }

    @Test
    fun `landing page should display correct title and hero content`() {
        // Navigate to the landing page
        page.navigate("http://localhost:$port/")

        // Verify page title
        assertEquals("Simple Queue - Professional Queue Management", page.title())

        // Verify hero section content
        val heroHeading = page.locator(".hero h1").textContent()
        assertEquals("Streamline Your Queue Management", heroHeading)

        // Verify hero description
        val heroDescription = page.locator(".hero p").textContent()
        assertTrue(heroDescription!!.contains("Create digital queues"))
        assertTrue(heroDescription.contains("manage customers efficiently"))
    }

    @Test
    fun `landing page should display all three pricing tiers`() {
        page.navigate("http://localhost:$port/")

        // Verify all pricing cards are present
        val pricingCards = page.locator(".pricing-card")
        assertEquals(3, pricingCards.count().toInt())

        // Verify all three pricing plan headings exist
        val headings = page.locator(".pricing-card h3").allTextContents()
        assertTrue(headings.contains("Starter"))
        assertTrue(headings.contains("Professional"))
        assertTrue(headings.contains("Enterprise"))
    }

    @Test
    fun `landing page should have working navigation links`() {
        page.navigate("http://localhost:$port/")

        // Verify navigation links exist
        val navLinks = page.locator(".nav-links a")
        assertTrue(navLinks.count() >= 3L)

        // Verify "How It Works" section is accessible via anchor link
        page.click("a[href='#how-it-works']")
        page.waitForTimeout(500.0) // Wait for smooth scroll

        val howItWorksSection = page.locator("#how-it-works")
        assertTrue(howItWorksSection.isVisible())

        // Verify all 4 steps are present
        val steps = page.locator(".step")
        assertEquals(4, steps.count().toInt())
    }

    @Test
    fun `landing page pricing should show correct agent limits`() {
        page.navigate("http://localhost:$port/")

        // Scroll to pricing section
        page.click("a[href='#pricing']")
        page.waitForTimeout(500.0)

        // Get all pricing cards and verify agent limits in features
        val pricingCards = page.locator(".pricing-card").all()

        var foundStarterAgent = false
        var foundProAgent = false

        for (card in pricingCards) {
            val features = card.locator(".features").textContent() ?: ""
            if (features.contains("1 agent") && !features.contains("Unlimited agents")) {
                foundStarterAgent = true
            }
            if (features.contains("Unlimited agents")) {
                foundProAgent = true
            }
        }

        assertTrue(foundStarterAgent, "Starter plan should have 1 agent limit")
        assertTrue(foundProAgent, "Professional plan should have unlimited agents")
    }

    @Test
    fun `landing page should have call to action buttons`() {
        page.navigate("http://localhost:$port/")

        // Verify primary CTA button exists
        val primaryCta = page.locator(".hero .btn-primary")
        assertTrue(primaryCta.isVisible())
        assertEquals("Start Free Trial", primaryCta.textContent())

        // Verify secondary CTA button exists
        val secondaryCta = page.locator(".hero .btn-secondary")
        assertTrue(secondaryCta.isVisible())
        assertEquals("Learn More", secondaryCta.textContent())

        // Verify all pricing cards have CTA buttons
        val pricingButtons = page.locator(".pricing-card .btn-pricing")
        assertEquals(3, pricingButtons.count().toInt())
    }
}
