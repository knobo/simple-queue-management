import { Page } from '@playwright/test';

/**
 * Authentication helpers for E2E tests.
 * 
 * To use authenticated tests:
 * 1. Set up a test user in Keycloak with appropriate roles
 * 2. Set environment variables E2E_TEST_USER and E2E_TEST_PASSWORD
 * 3. Set E2E_AUTH_ENABLED=true
 * 
 * Example:
 *   E2E_AUTH_ENABLED=true E2E_TEST_USER=admin E2E_TEST_PASSWORD=secret npm run test:e2e
 */

export interface AuthConfig {
  username: string;
  password: string;
  keycloakUrl?: string;
}

/**
 * Get auth config from environment variables.
 */
export function getAuthConfig(): AuthConfig | null {
  const username = process.env.E2E_TEST_USER;
  const password = process.env.E2E_TEST_PASSWORD;
  
  if (!username || !password) {
    return null;
  }
  
  return {
    username,
    password,
    keycloakUrl: process.env.E2E_KEYCLOAK_URL,
  };
}

/**
 * Login via Keycloak login form.
 * 
 * This assumes Keycloak is configured with standard login form.
 * Adjust selectors if your Keycloak theme is customized.
 */
export async function loginWithKeycloak(page: Page, config: AuthConfig): Promise<void> {
  // Wait for Keycloak login form
  await page.waitForSelector('#username', { timeout: 10000 });
  
  // Fill credentials
  await page.fill('#username', config.username);
  await page.fill('#password', config.password);
  
  // Submit
  await page.click('#kc-login');
  
  // Wait for redirect back to app
  await page.waitForURL(/^(?!.*keycloak).*/, { timeout: 10000 });
}

/**
 * Login helper that triggers auth flow from a protected page.
 */
export async function loginToApp(page: Page, protectedUrl: string): Promise<void> {
  const config = getAuthConfig();
  if (!config) {
    throw new Error('Auth not configured. Set E2E_TEST_USER and E2E_TEST_PASSWORD');
  }
  
  // Navigate to protected page (will redirect to Keycloak)
  await page.goto(protectedUrl);
  
  // Complete login
  await loginWithKeycloak(page, config);
  
  // Should now be on the protected page
  await page.waitForLoadState('networkidle');
}

/**
 * Setup authenticated state that can be reused across tests.
 * Use this with Playwright's storageState feature.
 */
export async function setupAuthState(page: Page): Promise<string> {
  // Login once
  await loginToApp(page, '/dashboard');
  
  // Save storage state
  const storagePath = './auth-state.json';
  await page.context().storageState({ path: storagePath });
  
  return storagePath;
}
