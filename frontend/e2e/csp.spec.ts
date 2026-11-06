import { expect, test } from '@playwright/test'

import { registerNewUser, SHOW } from './support.ts'

/**
 * No page may trip the Content-Security-Policy. Without the production headers
 * (preview/dev) this simply finds nothing; run against the production stack
 * (E2E_BASE_URL, see playwright.config.ts) it proves the CSP in deploy/Caddyfile
 * does not block the app.
 */
test('no page is blocked by the Content-Security-Policy', async ({ page }) => {
  const violations: string[] = []
  page.on('console', (message) => {
    if (/Content Security Policy|Content-Security-Policy/i.test(message.text())) {
      violations.push(`${page.url()}: ${message.text()}`)
    }
  })
  page.on('pageerror', (error) => violations.push(`${page.url()}: ${error.message}`))

  await registerNewUser(page)
  for (const path of ['/', '/discover', `/shows/${SHOW.ref}`, `/shows/${SHOW.ref}/seasons/1`, '/library', '/calendar?view=month', '/history', '/stats', '/settings']) {
    await page.goto(path)
    await page.waitForLoadState('networkidle')
  }

  expect(violations).toEqual([])
})
