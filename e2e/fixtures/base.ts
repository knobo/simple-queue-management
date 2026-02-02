import { test as base, expect, Page } from '@playwright/test';

/**
 * Custom test fixture that captures JavaScript console errors.
 * Use this to catch runtime JS errors that backend tests miss.
 */
export const test = base.extend<{
  jsErrors: string[];
  networkErrors: string[];
  pageWithJsErrorTracking: Page;
}>({
  jsErrors: [[], { scope: 'test' }],
  networkErrors: [[], { scope: 'test' }],
  
  pageWithJsErrorTracking: async ({ page, jsErrors, networkErrors }, use) => {
    // Capture console errors
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        const text = msg.text();
        // Separate network errors from JS errors
        if (text.includes('Failed to load resource') || 
            text.includes('net::ERR_') ||
            text.includes('404') ||
            text.includes('403') ||
            text.includes('401')) {
          networkErrors.push(`Network Error: ${text}`);
        } else {
          jsErrors.push(`Console Error: ${text}`);
        }
      }
    });
    
    // Capture page errors (uncaught exceptions) - these are always critical
    page.on('pageerror', (error) => {
      jsErrors.push(`Page Error: ${error.message}`);
    });
    
    await use(page);
  },
});

export { expect };

/**
 * Assert that no JavaScript errors occurred during the test.
 * Network errors (404, etc.) are tracked separately and not included here.
 * Call this at the end of tests to ensure frontend code is working.
 */
export async function expectNoJSErrors(jsErrors: string[]) {
  expect(jsErrors, 'JavaScript errors detected on page').toEqual([]);
}

/**
 * Assert that no network errors occurred (404s, etc.).
 * Use this when you want to verify all resources load correctly.
 */
export async function expectNoNetworkErrors(networkErrors: string[]) {
  expect(networkErrors, 'Network errors detected on page').toEqual([]);
}

/**
 * Assert that no errors of any kind occurred.
 */
export async function expectNoErrors(jsErrors: string[], networkErrors: string[]) {
  await expectNoJSErrors(jsErrors);
  await expectNoNetworkErrors(networkErrors);
}
