import { defineConfig, devices } from '@playwright/test'

/**
 * Browser end-to-end tests against the real stack: Spring Boot (mock catalogue,
 * so no TMDB token or network is needed) and the production build of the
 * frontend (`vite preview`, which proxies /api like the dev server), so the
 * tests exercise what is deployed, code-split chunks included. Needs MySQL:
 * locally the Compose database (`docker compose up -d` in the repo root,
 * credentials from `.env`); in CI a service container configured through the
 * DB_* environment variables.
 *
 * Locally, servers that are already running on these ports are reused.
 */
const backendPort = process.env.E2E_BACKEND_PORT ?? '8081'
const backendUrl = `http://localhost:${backendPort}`
const ci = !!process.env.CI

const inheritedEnv = Object.fromEntries(
  Object.entries(process.env).filter((entry): entry is [string, string] => entry[1] !== undefined),
)

export default defineConfig({
  testDir: './e2e',
  // One shared database and backend: run serially, like a single user would.
  workers: 1,
  fullyParallel: false,
  forbidOnly: ci,
  retries: ci ? 1 : 0,
  reporter: ci ? [['github'], ['html', { open: 'never' }]] : 'list',
  timeout: 60_000,
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // A fixed zone, so "today" in assertions matches the browser and the account.
    timezoneId: 'Asia/Colombo',
    locale: 'en-GB',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      // cmd.exe only runs a script from the current folder with an explicit ".\".
      command: process.platform === 'win32' ? '.\\mvnw.cmd -q spring-boot:run' : './mvnw -q spring-boot:run',
      cwd: '../backend',
      url: `${backendUrl}/actuator/health`,
      timeout: 240_000,
      reuseExistingServer: !ci,
      env: {
        ...inheritedEnv,
        SERVER_PORT: backendPort,
        METADATA_PROVIDER: 'mock',
        // Every run registers fresh accounts from 127.0.0.1; the production limit is 5 per hour.
        WATCHMARK_RATELIMIT_REGISTERPERCLIENT_LIMIT: '1000',
      },
    },
    {
      command: 'npm run build && npx vite preview --port 5173 --strictPort',
      url: 'http://localhost:5173',
      timeout: 120_000,
      reuseExistingServer: !ci,
      env: { ...inheritedEnv, WATCHMARK_BACKEND_URL: backendUrl },
    },
  ],
})
