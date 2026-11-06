import { expect, type Page } from '@playwright/test'

export const PASSWORD = 'correct horse battery staple'

/** Registers a fresh account through the real form and waits for the signed-in home page. */
export async function registerNewUser(page: Page, displayName = 'Playwright'): Promise<string> {
  const email = `e2e-${crypto.randomUUID()}@example.com`
  await page.goto('/register')
  await page.getByLabel('Display name').fill(displayName)
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: `Welcome back, ${displayName}.` })).toBeVisible()
  return email
}

/** The mock catalogue's always-present show: seasons with aired, airing-today and future episodes. */
export const SHOW = { ref: 'mock:1001', name: 'Northern Lights Station' }
