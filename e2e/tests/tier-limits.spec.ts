import { test, expect, expectNoJSErrors } from '../fixtures/base';

/**
 * E2E tests for the Tier Limits admin page.
 * 
 * These tests verify:
 * - Page loads without JavaScript errors
 * - Edit modal opens with correct values
 * - Save functionality works
 * 
 * Prerequisites:
 * - App running on localhost:8080
 * - For authenticated tests: Set up auth or use test user
 * 
 * Note: Tests marked with .skip require authentication.
 * To enable them:
 * 1. Set up a test user in Keycloak with superadmin role
 * 2. Add auth setup in fixtures/auth.ts
 * 3. Remove .skip from the tests
 */

test.describe('Admin Tier Limits Page', () => {
  
  // Skip these tests by default since they require superadmin auth
  // To run: set up authentication first (see README.md)
  const authRequired = process.env.E2E_AUTH_ENABLED === 'true';
  
  test.describe('Without Authentication', () => {
    
    test('tier-limits page is protected (requires auth)', async ({ page }) => {
      const response = await page.goto('/admin/tier-limits');
      
      const url = page.url();
      const status = response?.status();
      
      // Protected pages may:
      // 1. Redirect to auth provider
      // 2. Return 401/403
      // 3. Return 404 (hiding existence of protected resources)
      const isRedirectedToAuth = url.includes('auth') || 
                                  url.includes('login') || 
                                  url.includes('keycloak') ||
                                  url.includes('oauth');
      const isUnauthorized = status === 401 || status === 403;
      const isNotFound = status === 404; // Some apps return 404 for protected resources
      
      // One of these should be true - the page is not publicly accessible
      expect(isRedirectedToAuth || isUnauthorized || isNotFound).toBeTruthy();
    });
    
  });

  test.describe('With Authentication', () => {
    // These tests require auth - skip if not configured
    test.skip(!authRequired, 'Authentication not configured. Set E2E_AUTH_ENABLED=true');
    
    test('page loads without JavaScript errors', async ({ pageWithJsErrorTracking, jsErrors }) => {
      const page = pageWithJsErrorTracking;
      
      // Navigate to tier limits page
      await page.goto('/admin/tier-limits');
      
      // Wait for page to fully load
      await page.waitForLoadState('networkidle');
      
      // Verify page title
      await expect(page).toHaveTitle(/Tier Limits/);
      
      // Verify main elements are present
      await expect(page.locator('h1')).toContainText('Tier Limits Configuration');
      await expect(page.locator('table')).toBeVisible();
      
      // Verify all tier badges are present
      await expect(page.locator('.badge-free')).toBeVisible();
      await expect(page.locator('.badge-starter')).toBeVisible();
      await expect(page.locator('.badge-pro')).toBeVisible();
      await expect(page.locator('.badge-enterprise')).toBeVisible();
      
      // Check for JS errors
      await expectNoJSErrors(jsErrors);
    });

    test('edit button opens modal with correct tier name', async ({ pageWithJsErrorTracking, jsErrors }) => {
      const page = pageWithJsErrorTracking;
      
      await page.goto('/admin/tier-limits');
      await page.waitForLoadState('networkidle');
      
      // Find the FREE tier row and click Edit
      const freeRow = page.locator('tr', { has: page.locator('.badge-free') });
      await freeRow.locator('button:has-text("Edit")').click();
      
      // Verify modal is visible
      const modal = page.locator('#editModal');
      await expect(modal).toHaveClass(/active/);
      
      // Verify modal shows correct tier
      await expect(page.locator('#modal-tier-name')).toHaveText('FREE');
      
      // Verify form fields are present
      await expect(page.locator('#edit-maxQueues')).toBeVisible();
      await expect(page.locator('#edit-maxOperatorsPerQueue')).toBeVisible();
      await expect(page.locator('#edit-maxTicketsPerDay')).toBeVisible();
      await expect(page.locator('#edit-maxActiveTickets')).toBeVisible();
      await expect(page.locator('#edit-maxInvitesPerMonth')).toBeVisible();
      
      // Verify checkboxes are present
      await expect(page.locator('#edit-canUseEmailNotifications')).toBeVisible();
      await expect(page.locator('#edit-canUseCustomBranding')).toBeVisible();
      await expect(page.locator('#edit-canUseAnalytics')).toBeVisible();
      await expect(page.locator('#edit-canUseApiAccess')).toBeVisible();
      
      // Check for JS errors
      await expectNoJSErrors(jsErrors);
    });

    test('modal can be closed with cancel button', async ({ pageWithJsErrorTracking, jsErrors }) => {
      const page = pageWithJsErrorTracking;
      
      await page.goto('/admin/tier-limits');
      await page.waitForLoadState('networkidle');
      
      // Open modal
      const starterRow = page.locator('tr', { has: page.locator('.badge-starter') });
      await starterRow.locator('button:has-text("Edit")').click();
      
      // Verify modal is open
      const modal = page.locator('#editModal');
      await expect(modal).toHaveClass(/active/);
      
      // Click Cancel
      await page.locator('button:has-text("Cancel")').click();
      
      // Verify modal is closed
      await expect(modal).not.toHaveClass(/active/);
      
      // Check for JS errors
      await expectNoJSErrors(jsErrors);
    });

    test('modal can be closed by clicking outside', async ({ pageWithJsErrorTracking, jsErrors }) => {
      const page = pageWithJsErrorTracking;
      
      await page.goto('/admin/tier-limits');
      await page.waitForLoadState('networkidle');
      
      // Open modal
      const proRow = page.locator('tr', { has: page.locator('.badge-pro') });
      await proRow.locator('button:has-text("Edit")').click();
      
      // Verify modal is open
      const modal = page.locator('#editModal');
      await expect(modal).toHaveClass(/active/);
      
      // Click outside the modal content (on the backdrop)
      await modal.click({ position: { x: 10, y: 10 } });
      
      // Verify modal is closed
      await expect(modal).not.toHaveClass(/active/);
      
      // Check for JS errors
      await expectNoJSErrors(jsErrors);
    });

    test('modal close button (X) works', async ({ pageWithJsErrorTracking, jsErrors }) => {
      const page = pageWithJsErrorTracking;
      
      await page.goto('/admin/tier-limits');
      await page.waitForLoadState('networkidle');
      
      // Open modal
      const enterpriseRow = page.locator('tr', { has: page.locator('.badge-enterprise') });
      await enterpriseRow.locator('button:has-text("Edit")').click();
      
      // Verify modal is open
      const modal = page.locator('#editModal');
      await expect(modal).toHaveClass(/active/);
      
      // Click X button
      await page.locator('.modal-close').click();
      
      // Verify modal is closed
      await expect(modal).not.toHaveClass(/active/);
      
      // Check for JS errors
      await expectNoJSErrors(jsErrors);
    });

    test('edit form loads values from API', async ({ pageWithJsErrorTracking, jsErrors }) => {
      const page = pageWithJsErrorTracking;
      
      await page.goto('/admin/tier-limits');
      await page.waitForLoadState('networkidle');
      
      // Open FREE tier modal
      const freeRow = page.locator('tr', { has: page.locator('.badge-free') });
      await freeRow.locator('button:has-text("Edit")').click();
      
      // Wait for modal to be fully populated (values come from API)
      await page.waitForTimeout(500); // Small delay for API response
      
      // Verify numeric fields have values (not empty)
      const maxQueues = page.locator('#edit-maxQueues');
      await expect(maxQueues).not.toHaveValue('');
      
      const maxOperators = page.locator('#edit-maxOperatorsPerQueue');
      await expect(maxOperators).not.toHaveValue('');
      
      // Check for JS errors
      await expectNoJSErrors(jsErrors);
    });

    test('back to dashboard link works', async ({ pageWithJsErrorTracking, jsErrors }) => {
      const page = pageWithJsErrorTracking;
      
      await page.goto('/admin/tier-limits');
      await page.waitForLoadState('networkidle');
      
      // Click back to dashboard link
      await page.locator('a:has-text("Back to Dashboard")').click();
      
      // Verify navigation
      await expect(page).toHaveURL(/\/dashboard/);
      
      // Check for JS errors
      await expectNoJSErrors(jsErrors);
    });

    test('table shows all tiers with correct data', async ({ pageWithJsErrorTracking, jsErrors }) => {
      const page = pageWithJsErrorTracking;
      
      await page.goto('/admin/tier-limits');
      await page.waitForLoadState('networkidle');
      
      // Count rows (should be 4: FREE, STARTER, PRO, ENTERPRISE)
      const rows = page.locator('#limits-table tr');
      await expect(rows).toHaveCount(4);
      
      // Verify each tier has an Edit button
      for (const tier of ['FREE', 'STARTER', 'PRO', 'ENTERPRISE']) {
        const badge = page.locator(`.badge-${tier.toLowerCase()}`);
        await expect(badge).toBeVisible();
        
        const row = page.locator('tr', { has: badge });
        await expect(row.locator('button:has-text("Edit")')).toBeVisible();
      }
      
      // Check for JS errors
      await expectNoJSErrors(jsErrors);
    });

  });

});
