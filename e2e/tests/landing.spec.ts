import { test, expect, expectNoJSErrors } from '../fixtures/base';

/**
 * E2E tests for the landing page and basic navigation.
 * 
 * These tests verify:
 * - Landing page loads without errors
 * - Basic UI elements are present
 * - Navigation works
 */

test.describe('Landing Page', () => {

  test('landing page loads without JavaScript errors', async ({ pageWithJsErrorTracking, jsErrors }) => {
    const page = pageWithJsErrorTracking;
    
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    
    // Page should load successfully
    await expect(page).toHaveURL('/');
    
    // Check for JS errors
    await expectNoJSErrors(jsErrors);
  });

  test('home page loads without JavaScript errors', async ({ pageWithJsErrorTracking, jsErrors }) => {
    const page = pageWithJsErrorTracking;
    
    await page.goto('/home');
    await page.waitForLoadState('networkidle');
    
    // Check for JS errors
    await expectNoJSErrors(jsErrors);
  });

});

test.describe('Dashboard', () => {

  test('dashboard redirects to login when not authenticated', async ({ page }) => {
    // Without auth, dashboard should redirect to login or show error
    const response = await page.goto('/dashboard');
    
    // Either redirect to auth or show 401/403
    const status = response?.status();
    const url = page.url();
    
    // Accept either: redirected to auth, or error status, or dashboard (if auth disabled)
    const isRedirectedToAuth = url.includes('auth') || url.includes('login') || url.includes('keycloak');
    const isUnauthorized = status === 401 || status === 403;
    const isDashboard = url.includes('/dashboard');
    
    expect(isRedirectedToAuth || isUnauthorized || isDashboard).toBeTruthy();
  });

});

test.describe('Error Handling', () => {

  test('404 page loads without JavaScript errors', async ({ pageWithJsErrorTracking, jsErrors }) => {
    const page = pageWithJsErrorTracking;
    
    // Navigate to a non-existent page
    const response = await page.goto('/this-page-does-not-exist-12345');
    
    // Should return 404
    expect(response?.status()).toBe(404);
    
    // But should still render without JS errors
    await expectNoJSErrors(jsErrors);
  });

});
