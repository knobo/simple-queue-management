package com.example.simplequeue.infrastructure.e2e

import com.example.simplequeue.application.service.SubscriptionService
import com.example.simplequeue.domain.model.Queue
import com.example.simplequeue.domain.model.Ticket
import com.example.simplequeue.domain.port.QueueRepository
import com.example.simplequeue.domain.port.TicketRepository
import com.example.simplequeue.infrastructure.TestEmailConfig
import com.example.simplequeue.infrastructure.TestJacksonConfig
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.WaitUntilState
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
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
import java.nio.file.Paths
import java.time.Instant
import java.util.*

/**
 * E2E tests for authenticated user flows.
 * 
 * Uses E2ESecurityConfig to automatically mock authentication,
 * allowing Playwright to access protected pages without real OAuth.
 * 
 * Tests cover:
 * - Dashboard (list of queues)
 * - Creating new queues
 * - Queue management (call next, serve, complete tickets)
 * - Display/kiosk view
 * 
 * TODO: Fix CI timeout issues and re-enable these tests
 */
@Disabled("Temporarily disabled - CI timeout issues")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@Import(E2ESecurityConfig::class, TestEmailConfig::class, TestJacksonConfig::class)
class AuthenticatedE2ETest {

    companion object {
        @Container
        @ServiceConnection
        val postgres = PostgreSQLContainer<Nothing>("postgres:18-alpine").apply {
            start()
        }
    }

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var queueRepository: QueueRepository

    @Autowired
    private lateinit var ticketRepository: TicketRepository

    @Autowired
    private lateinit var subscriptionService: SubscriptionService

    private lateinit var playwright: Playwright
    private lateinit var browser: Browser
    private lateinit var context: BrowserContext
    private lateinit var page: Page

    private val baseUrl: String
        get() = "http://localhost:$port"

    @BeforeAll
    fun setup() {
        playwright = Playwright.create()
        browser = playwright.chromium().launch(
            BrowserType.LaunchOptions()
                .setHeadless(true)
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
        waitForAppReady()  // Vent på at appen er oppe
        // Ensure test user has subscription (needed for queue creation)
        subscriptionService.getOrCreateSubscription(E2ESecurityConfig.TEST_USER_ID)
    }

    @AfterEach
    fun cleanupData() {
        // Clean up test data after each test
        try {
            val queues = queueRepository.findByOwnerId(E2ESecurityConfig.TEST_USER_ID)
            queues.forEach { queue ->
                queueRepository.delete(queue.id)
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @AfterAll
    fun tearDown() {
        context.close()
        browser.close()
        playwright.close()
        println("🎬 Videos saved to /tmp/playwright-videos/")
    }

    // ==================== Helper for Dashboard Navigation ====================
    
    /**
     * Navigate to dashboard using DOMCONTENTLOADED.
     * The dashboard uses SSE (EventSource) for real-time updates which keeps
     * a persistent connection open. This prevents the "load" event from firing
     * within the default timeout. Using DOMCONTENTLOADED avoids this issue.
     */
    private fun navigateToDashboard() {
        page.navigate("$baseUrl/dashboard", 
            Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(60000.0))  // 60 sekunder for CI
        page.waitForLoadState(LoadState.DOMCONTENTLOADED)
    }

    /**
     * Wait for the application to be ready by polling the root endpoint.
     */
    private fun waitForAppReady(maxAttempts: Int = 30, delayMs: Long = 1000) {
        repeat(maxAttempts) { attempt ->
            try {
                val response = java.net.URL("$baseUrl/").openConnection() as java.net.HttpURLConnection
                response.connectTimeout = 2000
                response.readTimeout = 2000
                if (response.responseCode in 200..399) {
                    println("App ready after ${attempt + 1} attempts")
                    return
                }
            } catch (e: Exception) {
                // App not ready yet
            }
            Thread.sleep(delayMs)
        }
        throw RuntimeException("App not ready after $maxAttempts attempts")
    }

    // ==================== Dashboard Tests ====================

    @Test
    fun `dashboard shows empty state when user has no queues`() {
        navigateToDashboard()

        // Should show "no queues" message
        val noQueuesText = page.locator("text=You have no queues yet").first()
        noQueuesText.waitFor()
        assertTrue(noQueuesText.isVisible(), "Should show 'no queues' message")

        // Should have create button
        val createButton = page.locator("a[href='/create-queue']").first()
        createButton.waitFor()
        assertTrue(createButton.isVisible(), "Should have 'Create New Queue' button")
    }

    @Test
    fun `dashboard shows queue list when user has queues`() {
        // Create a test queue directly
        val queue = createTestQueue("Test Queue for Dashboard")

        navigateToDashboard()

        // Should show the queue
        val queueName = page.locator("h3:has-text('Test Queue for Dashboard')").first()
        queueName.waitFor()
        assertTrue(queueName.isVisible(), "Should show queue name")

        // Should show queue status
        val statusBadge = page.locator(".badge-success, .badge-danger").first()
        statusBadge.waitFor()
        assertTrue(statusBadge.isVisible(), "Should show queue status badge")
    }

    @Test
    fun `dashboard shows waiting and active tickets`() {
        // Create queue with tickets
        val queue = createTestQueue("Queue with Tickets")
        createTestTicket(queue.id, "Waiting Customer 1", Ticket.TicketStatus.WAITING)
        createTestTicket(queue.id, "Waiting Customer 2", Ticket.TicketStatus.WAITING)
        createTestTicket(queue.id, "Active Customer", Ticket.TicketStatus.CALLED)

        navigateToDashboard()

        // Should show waiting tickets section
        val waitingSection = page.locator("h4:has-text('Waiting')").first()
        waitingSection.waitFor()
        assertTrue(waitingSection.isVisible(), "Should have 'Waiting' section")

        // Should show active tickets section
        val activeSection = page.locator("h4:has-text('Now Serving')").first()
        activeSection.waitFor()
        assertTrue(activeSection.isVisible(), "Should have 'Now Serving' section")

        // Should show ticket rows
        val waitingRows = page.locator("table:has(th:has-text('Code')) tr:has-text('Waiting Customer')").first()
        waitingRows.waitFor()
        assertTrue(page.locator("table:has(th:has-text('Code')) tr:has-text('Waiting Customer')").count() >= 1, "Should show waiting customer tickets")
    }

    // ==================== Create Queue Tests ====================

    @Test
    fun `create queue page loads correctly`() {
        page.navigate("$baseUrl/create-queue")
        page.waitForLoadState(LoadState.NETWORKIDLE)

        // Should show create queue form
        val nameInput = page.locator("input[name='name'], input#name, input[type='text']").first()
        nameInput.waitFor()
        assertTrue(nameInput.isVisible(), "Should have queue name input")

        // Should have submit button
        val submitButton = page.locator("button[type='submit'], input[type='submit']").first()
        submitButton.waitFor()
        assertTrue(submitButton.isVisible(), "Should have submit button")
    }

    @Test
    fun `can create new queue via form`() {
        page.navigate("$baseUrl/create-queue")
        page.waitForLoadState(LoadState.NETWORKIDLE)

        // Fill in queue name
        val nameInput = page.locator("input[name='name'], input#name, input[type='text']").first()
        nameInput.fill("My New E2E Test Queue")

        // Submit form
        val submitButton = page.locator("button[type='submit'], input[type='submit']").first()
        submitButton.click()

        // Should redirect to dashboard - use DOMCONTENTLOADED because dashboard has SSE
        page.waitForURL("**/dashboard**", Page.WaitForURLOptions()
            .setTimeout(10000.0)
            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED))

        // Should show new queue on dashboard
        val queueName = page.locator("h3:has-text('My New E2E Test Queue')").first()
        queueName.waitFor()
        assertTrue(queueName.isVisible(), "New queue should appear on dashboard")
    }

    // ==================== Queue Management Tests ====================

    @Test
    fun `can toggle queue status from open to closed`() {
        val queue = createTestQueue("Toggle Status Queue", open = true)

        navigateToDashboard()

        // Find the toggle button (should say "Close Queue" when open)
        val toggleButton = page.locator("button:has-text('Close'), button:has-text('Lukk')").first()
        toggleButton.waitFor()
        assertTrue(toggleButton.isVisible(), "Should have close/toggle button")

        // Click toggle
        toggleButton.click()
        page.waitForTimeout(500.0)

        // Status should change
        val closedBadge = page.locator(".badge-danger:has-text('CLOSED'), .badge-danger:has-text('STENGT')").first()
        closedBadge.waitFor()
        assertTrue(closedBadge.isVisible(), "Queue should show as closed")
    }

    @Test
    fun `can call next ticket`() {
        val queue = createTestQueue("Call Next Queue")
        createTestTicket(queue.id, "Customer 1", Ticket.TicketStatus.WAITING)
        createTestTicket(queue.id, "Customer 2", Ticket.TicketStatus.WAITING)

        navigateToDashboard()

        // Click "Call Next" button
        val callNextButton = page.locator("button:has-text('Call Next'), button:has-text('Kall neste')").first()
        callNextButton.waitFor()
        assertTrue(callNextButton.isVisible(), "Should have 'Call Next' button")
        callNextButton.click()

        // Wait for page reload - use DOMCONTENTLOADED because dashboard has SSE
        page.waitForLoadState(LoadState.DOMCONTENTLOADED)

        // Should have an active ticket now
        val activeSection = page.locator("h4:has-text('Now Serving'), h4:has-text('Betjenes nå')").first()
        activeSection.waitFor()
        assertTrue(activeSection.isVisible(), "Should show 'Now Serving' section")
    }

    @Test
    fun `can complete a ticket`() {
        val queue = createTestQueue("Complete Ticket Queue")
        createTestTicket(queue.id, "Active Customer", Ticket.TicketStatus.CALLED)

        navigateToDashboard()

        // Find "Done" button in active tickets section
        val doneButton = page.locator("button:has-text('Done'), button:has-text('Ferdig')").first()
        doneButton.waitFor()
        assertTrue(doneButton.isVisible(), "Should have 'Done' button for active ticket")
        doneButton.click()

        // Wait for page update - use DOMCONTENTLOADED because dashboard has SSE
        page.waitForLoadState(LoadState.DOMCONTENTLOADED)

        // Active section should be empty or ticket gone
        val noActiveText = page.locator("text=No active tickets, text=Ingen aktive billetter")
        // Either no active text visible OR the ticket row is gone
        page.waitForTimeout(500.0)
    }

    @Test
    fun `can serve specific ticket from waiting list`() {
        val queue = createTestQueue("Serve Specific Queue")
        createTestTicket(queue.id, "First Customer", Ticket.TicketStatus.WAITING)
        createTestTicket(queue.id, "Second Customer", Ticket.TicketStatus.WAITING)

        navigateToDashboard()

        // Find "Call" button in waiting list
        val callButton = page.locator("button:has-text('Call'):not(:has-text('Call Next')), button:has-text('Kall'):not(:has-text('Kall neste'))").first()
        callButton.waitFor()
        assertTrue(callButton.isVisible(), "Should have 'Call' button for waiting ticket")
        callButton.click()

        // Wait for page reload - use DOMCONTENTLOADED because dashboard has SSE
        page.waitForLoadState(LoadState.DOMCONTENTLOADED)

        // Should now have an active ticket
        val activeSection = page.locator("h4:has-text('Now Serving'), h4:has-text('Betjenes nå')").first()
        activeSection.waitFor()
        assertTrue(activeSection.isVisible())
    }

    // ==================== Display/Kiosk Tests ====================

    @Test
    fun `public display page shows queue status`() {
        val queue = createTestQueue("Display Test Queue")
        createTestTicket(queue.id, "Waiting Person", Ticket.TicketStatus.WAITING)
        createTestTicket(queue.id, "Active Person", Ticket.TicketStatus.CALLED)

        // Public display doesn't require auth
        page.navigate("$baseUrl/public/q/${queue.id}/display")
        page.waitForLoadState(LoadState.NETWORKIDLE)

        // Should show queue name
        val queueTitle = page.locator("text=Display Test Queue").first()
        queueTitle.waitFor()
        assertTrue(queueTitle.isVisible(), "Should show queue name on display")
    }

    @Test
    fun `public join page works`() {
        val queue = createTestQueue("Join Test Queue")

        // Navigate to join page with secret
        page.navigate("$baseUrl/public/q/${queue.id}/join?secret=${queue.qrCodeSecret}")
        page.waitForLoadState(LoadState.NETWORKIDLE)

        // Should show join form
        val joinForm = page.locator("form, button:has-text('Join'), button:has-text('Ta')").first()
        joinForm.waitFor()
        assertTrue(joinForm.isVisible(), "Should show join queue form or button")
    }

    @Test
    fun `queue admin page loads for queue owner`() {
        val queue = createTestQueue("Admin Test Queue")

        page.navigate("$baseUrl/queue/${queue.id}/admin")
        page.waitForLoadState(LoadState.NETWORKIDLE)

        // Should show admin page
        val queueName = page.locator("text=Admin Test Queue").first()
        queueName.waitFor()
        assertTrue(queueName.isVisible(), "Should show queue name on admin page")
    }

    // ==================== Helper Methods ====================

    private fun createTestQueue(name: String, open: Boolean = true): Queue {
        val queue = Queue.create(name, E2ESecurityConfig.TEST_USER_ID).copy(open = open)
        queueRepository.save(queue)
        return queue
    }

    private fun createTestTicket(
        queueId: UUID,
        name: String,
        status: Ticket.TicketStatus
    ): Ticket {
        val ticket = Ticket(
            id = UUID.randomUUID(),
            queueId = queueId,
            number = ticketRepository.getNextNumber(queueId),
            name = name,
            status = status,
            ntfyTopic = UUID.randomUUID().toString(),
            createdAt = Instant.now()
        )
        ticketRepository.save(ticket)
        return ticket
    }
}
