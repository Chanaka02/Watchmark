import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { details, notInLibrary, signedInRoutes } from '../../test/catalogFixtures'
import { json, mockApi, noContent, problem, setCsrfCookie } from '../../test/mockApi'
import { renderRoute } from '../../test/renderRoute'
import type { LibraryEntry } from './types'

const SHOW = 'GET /api/v1/catalog/shows/tmdb%3A1'
const ENTRY = '/api/v1/library/tmdb%3A1'

function entry(overrides: Partial<LibraryEntry> = {}): LibraryEntry {
  return {
    show: { ref: 'tmdb:1', name: 'Fixture Harbour', posterUrl: null, firstAirDate: null, status: 'RETURNING' },
    status: 'WATCHING',
    rating: null,
    notes: null,
    cycle: 1,
    addedAt: '2026-11-01T10:00:00Z',
    updatedAt: '2026-11-01T10:00:00Z',
    ...overrides,
  }
}

function renderShow(routes: Parameters<typeof mockApi>[0]) {
  const api = mockApi({ ...signedInRoutes(), [SHOW]: () => json(200, details()), ...routes })
  renderRoute('/shows/tmdb:1')
  return api
}

describe('LibraryPanel', () => {
  beforeEach(() => setCsrfCookie())

  it('adds a show with the chosen status', async () => {
    const api = renderShow({
      [`GET ${ENTRY}`]: notInLibrary,
      [`PUT ${ENTRY}`]: () => json(201, entry({ status: 'WATCHING' })),
    })
    const user = userEvent.setup()

    await user.selectOptions(await screen.findByLabelText('Add with status'), 'Watching')
    await user.click(screen.getByRole('button', { name: 'Add to library' }))

    expect(await screen.findByLabelText('Status')).toHaveValue('WATCHING')
    expect(api.calls(`PUT ${ENTRY}`)[0].body).toEqual({ status: 'WATCHING' })
  })

  it('shows why adding failed', async () => {
    renderShow({
      [`GET ${ENTRY}`]: notInLibrary,
      [`PUT ${ENTRY}`]: () => problem(503, 'metadata-unavailable', 'Show information is temporarily unavailable.'),
    })
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Add to library' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Show information is temporarily unavailable.')
    expect(screen.getByRole('button', { name: 'Add to library' })).toBeInTheDocument()
  })

  it('changes status and rating immediately, and can clear the rating', async () => {
    const api = renderShow({
      [`GET ${ENTRY}`]: () => json(200, entry({ rating: 7 })),
      [`PUT ${ENTRY}`]: () => json(200, entry({ status: 'COMPLETED', rating: 7 })),
      [`PUT ${ENTRY}/rating`]: ({ body }) => json(200, entry({ status: 'COMPLETED', ...(body as object) })),
    })
    const user = userEvent.setup()

    expect(await screen.findByLabelText('Your rating')).toHaveValue('7')
    await user.selectOptions(screen.getByLabelText('Status'), 'Completed')
    await vi.waitFor(() => expect(screen.getByLabelText('Status')).toHaveValue('COMPLETED'))
    await user.selectOptions(screen.getByLabelText('Your rating'), 'No rating')

    await vi.waitFor(() => expect(screen.getByLabelText('Your rating')).toHaveValue(''))
    expect(api.calls(`PUT ${ENTRY}/rating`)[0].body).toEqual({ rating: null })
  })

  it('saves notes only when they changed', async () => {
    const api = renderShow({
      [`GET ${ENTRY}`]: () => json(200, entry({ notes: 'old' })),
      [`PUT ${ENTRY}/notes`]: () => json(200, entry({ notes: 'Rewatch the finale' })),
    })
    const user = userEvent.setup()

    const notes = await screen.findByLabelText('Private notes')
    const save = screen.getByRole('button', { name: 'Save notes' })
    expect(save).toBeDisabled()

    await user.clear(notes)
    await user.type(notes, 'Rewatch the finale')
    expect(screen.getByText('18 / 2000')).toBeInTheDocument()
    await user.click(save)

    await vi.waitFor(() => expect(screen.getByRole('button', { name: 'Save notes' })).toBeDisabled())
    expect(api.calls(`PUT ${ENTRY}/notes`)[0].body).toEqual({ notes: 'Rewatch the finale' })
  })

  it('explains that removing keeps history, and can be cancelled with Escape', async () => {
    const api = renderShow({
      [`GET ${ENTRY}`]: () => json(200, entry()),
      [`DELETE ${ENTRY}`]: () => noContent(),
    })
    const user = userEvent.setup()

    const removeButton = await screen.findByRole('button', { name: 'Remove from library' })
    await user.click(removeButton)
    const dialog = screen.getByRole('dialog', { name: 'Remove Fixture Harbour from your library?' })
    expect(dialog).toHaveTextContent('Your rating, notes and viewing history are kept.')
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(removeButton).toHaveFocus()
    expect(api.calls(`DELETE ${ENTRY}`)).toHaveLength(0)

    await user.click(removeButton)
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Remove' }))

    expect(await screen.findByRole('button', { name: 'Add to library' })).toBeInTheDocument()
    expect(api.calls(`DELETE ${ENTRY}`)).toHaveLength(1)
  })

  it('requires the exact show name before permanently deleting', async () => {
    const api = renderShow({
      [`GET ${ENTRY}`]: () => json(200, entry()),
      [`POST ${ENTRY}/purge`]: () => noContent(),
    })
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Delete my data for this show' }))
    const dialog = screen.getByRole('dialog', { name: 'Permanently delete your data for this show?' })
    const confirm = within(dialog).getByRole('button', { name: 'Delete permanently' })
    const input = within(dialog).getByLabelText('Type the show name to confirm')
    expect(input).toHaveFocus()
    expect(confirm).toBeDisabled()

    await user.type(input, 'Fixture')
    expect(confirm).toBeDisabled()
    await user.type(input, ' harbour')
    expect(input).toHaveFocus()
    expect(confirm).toBeEnabled()
    await user.click(confirm)

    expect(await screen.findByRole('button', { name: 'Add to library' })).toBeInTheDocument()
    expect(api.calls(`POST ${ENTRY}/purge`)[0].body).toEqual({ confirmName: 'Fixture harbour' })
  })
})
