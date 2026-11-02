import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'

import { signedInRoutes } from '../test/catalogFixtures'
import { json, mockApi, noContent, problem, setCsrfCookie, signedOut, testUser } from '../test/mockApi'
import { renderRoute } from '../test/renderRoute'

const NEW_PASSWORD = 'a much longer passphrase here'

describe('SettingsPage', () => {
  beforeEach(() => setCsrfCookie())

  it('saves the display name and time zone, and updates the header', async () => {
    const api = mockApi({
      ...signedInRoutes(),
      'PATCH /api/v1/me/profile': ({ body }) => json(200, { ...testUser, ...(body as object) }),
    })
    renderRoute('/settings')
    const user = userEvent.setup()

    const save = await screen.findByRole('button', { name: 'Save profile' })
    expect(save).toBeDisabled()
    expect(screen.getByLabelText('Time zone')).toHaveValue('Asia/Colombo')

    await user.clear(screen.getByLabelText('Display name'))
    await user.type(screen.getByLabelText('Display name'), 'Ana B')
    await user.selectOptions(screen.getByLabelText('Time zone'), 'Europe/Lisbon')
    await user.click(save)

    expect(await screen.findByText('Profile saved.')).toBeInTheDocument()
    expect(api.calls('PATCH /api/v1/me/profile')[0].body).toEqual({ displayName: 'Ana B', timeZone: 'Europe/Lisbon' })
    expect(within(screen.getByRole('navigation', { name: 'Account' })).getByText('Ana B')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save profile' })).toBeDisabled()
  })

  it('shows server validation errors next to the field', async () => {
    mockApi({
      ...signedInRoutes(),
      'PATCH /api/v1/me/profile': () =>
        json(
          400,
          { type: '/problems/validation-failed', detail: 'Invalid', errors: [{ field: 'timeZone', message: 'unknown time zone' }] },
          { 'Content-Type': 'application/problem+json' },
        ),
    })
    renderRoute('/settings')
    const user = userEvent.setup()

    await user.selectOptions(await screen.findByLabelText('Time zone'), 'UTC')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))

    expect(await screen.findByText('Unknown time zone.')).toBeInTheDocument()
  })

  it('changes the password and explains that other devices were signed out', async () => {
    const api = mockApi({ ...signedInRoutes(), 'PUT /api/v1/me/password': noContent })
    renderRoute('/settings')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Current password'), 'old password value')
    await user.type(screen.getByLabelText('New password'), NEW_PASSWORD)
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByText('Password changed. Other devices were signed out.')).toBeInTheDocument()
    expect(api.calls('PUT /api/v1/me/password')[0].body).toEqual({
      currentPassword: 'old password value',
      newPassword: NEW_PASSWORD,
    })
    expect(screen.getByLabelText('Current password')).toHaveValue('')
  })

  it('checks the new password locally and the current one on the server', async () => {
    const api = mockApi({
      ...signedInRoutes(),
      'PUT /api/v1/me/password': () => problem(403, 'password-incorrect', 'The password you entered is incorrect.'),
    })
    renderRoute('/settings')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('Current password'), 'wrong')
    await user.type(screen.getByLabelText('New password'), 'too short')
    await user.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByText('Use at least 15 characters.')).toBeInTheDocument()
    expect(api.calls('PUT /api/v1/me/password')).toHaveLength(0)

    await user.clear(screen.getByLabelText('New password'))
    await user.type(screen.getByLabelText('New password'), NEW_PASSWORD)
    await user.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByText('This password is incorrect.')).toBeInTheDocument()
  })

  it('deletes the account only with the right password, then signs out', async () => {
    let deleted = false
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/auth/me': () => (deleted ? signedOut() : json(200, testUser)),
      'DELETE /api/v1/me': ({ body }) => {
        if ((body as { password: string }).password !== 'right password') {
          return problem(403, 'password-incorrect', 'The password you entered is incorrect.')
        }
        deleted = true
        return noContent()
      },
    })
    const { router } = renderRoute('/settings')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Delete my account' }))
    const dialog = screen.getByRole('dialog', { name: 'Delete your account permanently?' })
    const confirm = within(dialog).getByRole('button', { name: 'Delete account' })
    expect(confirm).toBeDisabled()

    await user.type(within(dialog).getByLabelText('Enter your password to confirm'), 'wrong password')
    await user.click(confirm)
    expect(await within(dialog).findByText('This password is incorrect.')).toBeInTheDocument()

    await user.clear(within(dialog).getByLabelText('Enter your password to confirm'))
    await user.type(within(dialog).getByLabelText('Enter your password to confirm'), 'right password')
    await user.click(confirm)

    expect(await screen.findByRole('heading', { name: 'Track every episode.' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })
})
