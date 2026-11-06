import { expect, test } from '@playwright/test'

import { registerNewUser, SHOW } from './support.ts'

/**
 * The core journey in a real browser against the real backend: register, find a
 * show, add it, mark an episode watched, undo it, mark it again, and see the
 * result on the dashboard, in the history and in the statistics.
 */
test('register, add a show and track an episode end to end', async ({ page }) => {
  await registerNewUser(page)
  await expect(page.getByText('Your dashboard is empty')).toBeVisible()

  // Discover the show and add it.
  await page.getByRole('link', { name: 'Discover shows' }).click()
  await page.getByRole('link', { name: new RegExp(SHOW.name) }).first().click()
  await expect(page.getByRole('heading', { level: 1, name: SHOW.name })).toBeVisible()
  await page.getByRole('button', { name: 'Add to library' }).click()
  await expect(page.getByLabel('Status')).toHaveValue('PLAN_TO_WATCH')
  await expect(page.getByRole('heading', { name: 'Your progress' })).toBeVisible()

  // Season 1: mark episode 1 watched, undo it, then mark it again.
  await page.getByRole('link', { name: /^Season 1/ }).click()
  const episode1 = page.getByRole('button', { name: 'S1 E1 Watched', exact: true })
  await episode1.click()
  const notice = page.getByRole('status').filter({ hasText: 'Marked S1 E1 watched.' })
  await expect(notice).toBeVisible()
  await expect(episode1).toHaveAttribute('aria-pressed', 'true')

  await notice.getByRole('button', { name: 'Undo' }).click()
  await expect(page.getByText('Undone.')).toBeVisible()
  await expect(episode1).toHaveAttribute('aria-pressed', 'false')

  await episode1.click()
  await expect(episode1).toHaveAttribute('aria-pressed', 'true')
  await expect(page.getByText('1 of 8 aired episodes watched')).toBeVisible()

  // Tracking moved the show from "Plan to watch" to "Watching" (R9).
  await page.getByRole('link', { name: SHOW.name }).first().click()
  await expect(page.getByLabel('Status')).toHaveValue('WATCHING')

  // Dashboard: continue with episode 2.
  await page.getByRole('link', { name: 'Watchmark' }).click()
  const card = page.getByRole('article', { name: SHOW.name })
  await expect(card.getByText('S1 E2 · Episode 2')).toBeVisible()

  // History and statistics show the one viewing.
  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'History' }).click()
  await expect(page.getByRole('region', { name: 'Today' }).getByText('S1 E1 · Episode 1')).toBeVisible()

  await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Stats' }).click()
  const episodesTile = page.locator('div').filter({ has: page.getByText('Episodes watched', { exact: true }) }).last()
  await expect(episodesTile).toContainText('1 viewing including rewatches')
})

test('signing out ends the session and protects pages', async ({ page }) => {
  await registerNewUser(page)
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Track every episode.' })).toBeVisible()

  await page.goto('/library')
  await expect(page).toHaveURL(/\/login\?next=%2Flibrary/)
})
