# E2E Tests for Simple Queue

End-to-end tests using [Playwright](https://playwright.dev/) to catch frontend bugs that backend tests miss.

## Why E2E Tests?

We've had several bugs that backend tests didn't catch:
- Thymeleaf escaping issues (`th:text` vs `th:utext`)
- JavaScript parse errors
- Onclick handlers that fail at runtime

E2E tests run in a real browser and catch these issues.

## Setup

```bash
cd e2e
npm install
npx playwright install chromium  # Download browser (already done if you ran npm install)
```

## Running Tests

### Prerequisites
The app must be running on `http://localhost:8080`:

```bash
# In the root project directory
./gradlew bootRun
```

### Run all tests
```bash
npm run test:e2e
```

### Run with UI (interactive mode)
```bash
npm run test:e2e:ui
```

### Run specific test file
```bash
npm run test:tier-limits  # Only tier-limits tests
npm run test:landing      # Only landing page tests
```

### Run headed (see the browser)
```bash
npm run test:e2e:headed
```

### Debug mode (step through)
```bash
npm run test:e2e:debug
```

### View test report
```bash
npm run test:report
```

## Test Structure

```
e2e/
├── fixtures/
│   └── base.ts           # Custom test fixtures (JS error tracking)
├── tests/
│   ├── tier-limits.spec.ts   # Admin tier limits page tests
│   └── landing.spec.ts       # Landing/home page tests
├── playwright.config.ts   # Playwright configuration
└── package.json
```

## Writing New Tests

### Basic test structure

```typescript
import { test, expect, expectNoJSErrors } from '../fixtures/base';

test.describe('My Feature', () => {
  test('should work without JS errors', async ({ pageWithJsErrorTracking, jsErrors }) => {
    const page = pageWithJsErrorTracking;
    
    await page.goto('/my-page');
    await page.waitForLoadState('networkidle');
    
    // Your assertions here
    await expect(page.locator('h1')).toContainText('Expected Title');
    
    // Always check for JS errors
    await expectNoJSErrors(jsErrors);
  });
});
```

### Key patterns

1. **Always check for JS errors** - Use `pageWithJsErrorTracking` fixture and call `expectNoJSErrors(jsErrors)` at the end

2. **Wait for network idle** - Use `await page.waitForLoadState('networkidle')` after navigation

3. **Use semantic locators** - Prefer `locator('button:has-text("Save")')` over CSS selectors

4. **Test user flows, not implementation** - Focus on what users do, not internal details

## Configuration

### Change base URL

Set the `BASE_URL` environment variable:

```bash
BASE_URL=http://staging.example.com npm run test:e2e
```

### Running in CI

The config is already set up for CI:
- `forbidOnly: !!process.env.CI` - Fails if `.only` is committed
- `retries: process.env.CI ? 2 : 0` - Retry failed tests in CI
- `workers: process.env.CI ? 1 : undefined` - Single worker in CI

## Authentication

Currently, tests that require authentication will be skipped or fail gracefully.

To test authenticated pages:
1. Set up test users in Keycloak
2. Add login helper in fixtures
3. Use `storageState` to persist auth (see Playwright docs)

## Troubleshooting

### Tests fail with connection refused
Make sure the app is running on port 8080:
```bash
./gradlew bootRun
```

### Tests are flaky
1. Add `await page.waitForLoadState('networkidle')`
2. Use `await expect(...).toBeVisible()` instead of immediate assertions
3. Add small delays with `await page.waitForTimeout(500)` only if necessary

### Browser doesn't start
```bash
npx playwright install chromium
```
