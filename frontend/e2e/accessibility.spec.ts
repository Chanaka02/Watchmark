import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

import { registerNewUser, SHOW } from './support.ts'

/**
 * Automated accessibility checks (axe-core, WCAG 2.2 A/AA rules) in a real
 * browser, where colour contrast can be measured, in both colour schemes; and
 * no sideways scrolling at phone width. Automated checks find only part of the
 * problems; they complement, not replace, keyboard and screen reader testing.
 */

const signedOutPages = ['/', '/login', '/register']

const signedInPages = [
  '/',
  '/discover',
  '/search?q=light',
  '/library',
  `/shows/${SHOW.ref}`,
  `/shows/${SHOW.ref}/seasons/1`,
  '/calendar',
  '/calendar?view=month',
  '/history',
  '/stats',
  '/settings',
  '/this-page-does-not-exist',
]

async function expectNoViolations(page: Page, path: string) {
  await page.goto(path)
  await page.waitForLoadState('networkidle')
  // Wait until no loader is showing, so the scan sees the real page.
  await expect(page.getByRole('status').filter({ hasText: /^Loading/ })).toHaveCount(0)
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze()
  const summary = results.violations.map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)
  // Soft: one run reports every page, not just the first failing one.
  expect.soft(summary, `${path} has accessibility violations`).toEqual([])
}

async function expectNoSidewaysScroll(page: Page, path: string) {
  await page.goto(path)
  await page.waitForLoadState('networkidle')
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect.soft(overflow, `${path} scrolls sideways at phone width`).toBeLessThanOrEqual(0)
}

/** A signed-in user with a show in the library, an episode watched and a rewatch-free history. */
async function userWithData(page: Page) {
  await registerNewUser(page)
  await page.goto(`/shows/${SHOW.ref}`)
  await page.getByRole('button', { name: 'Add to library' }).click()
  await page.goto(`/shows/${SHOW.ref}/seasons/1`)
  await page.getByRole('button', { name: 'S1 E1 Watched', exact: true }).click()
  await expect(page.getByRole('button', { name: 'S1 E1 Watched', exact: true })).toHaveAttribute('aria-pressed', 'true')
}

for (const colorScheme of ['light', 'dark'] as const) {
  test.describe(`${colorScheme} colour scheme`, () => {
    test.use({ colorScheme })

    test('signed-out pages have no detectable accessibility violations', async ({ page }) => {
      for (const path of signedOutPages) {
        await expectNoViolations(page, path)
      }
    })

    test('signed-in pages have no detectable accessibility violations', async ({ page }) => {
      await userWithData(page)
      for (const path of signedInPages) {
        await expectNoViolations(page, path)
      }
    })
  })
}

test.describe('phone width', () => {
  test.use({ viewport: { width: 375, height: 740 } })

  test('no page scrolls sideways', async ({ page }) => {
    for (const path of signedOutPages) {
      await expectNoSidewaysScroll(page, path)
    }
    await userWithData(page)
    for (const path of signedInPages) {
      await expectNoSidewaysScroll(page, path)
    }
  })
})
