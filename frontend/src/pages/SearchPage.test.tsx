import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'

import { page, signedInRoutes, summary } from '../test/catalogFixtures'
import { json, mockApi, problem } from '../test/mockApi'
import { renderRoute } from '../test/renderRoute'

describe('SearchPage', () => {
  it('prompts for a query without calling the search API', async () => {
    const api = mockApi(signedInRoutes())
    renderRoute('/search')

    expect(await screen.findByText('Find a series')).toBeInTheDocument()
    expect(api.spy.mock.calls.some(([url]) => String(url).includes('/catalog/search'))).toBe(false)
  })

  it('searches on submit, encodes the query and reports the result count', async () => {
    const api = mockApi({
      ...signedInRoutes(),
      'GET /api/v1/catalog/search?q=dr%20who%20%26%20co&page=1': () =>
        json(200, page([summary(1, { name: 'Fixture Doctor' })], 1, 1, 1)),
    })
    const { router } = renderRoute('/search')
    const user = userEvent.setup()

    await user.type(await screen.findByRole('searchbox', { name: /search for a tv series/i }), '  dr who & co ')
    await user.click(screen.getByRole('button', { name: 'Search' }))

    expect(await screen.findByRole('link', { name: /fixture doctor/i })).toBeInTheDocument()
    expect(screen.getByText(/1 result for “dr who & co”/)).toBeInTheDocument()
    expect(router.state.location.search).toBe('?q=dr+who+%26+co')
    api.assertNoUnexpectedRequests()
  })

  it('shows a no-results state', async () => {
    mockApi({ ...signedInRoutes(), 'GET /api/v1/catalog/search?q=zzz&page=1': () => json(200, page([], 1, 0, 0)) })
    renderRoute('/search?q=zzz')

    expect(await screen.findByText('No series match “zzz”')).toBeInTheDocument()
    expect(screen.getByRole('searchbox')).toHaveValue('zzz')
  })

  it('explains search rate limiting', async () => {
    mockApi({
      ...signedInRoutes(),
      'GET /api/v1/catalog/search?q=lights&page=1': () =>
        problem(429, 'rate-limited', 'Too many attempts. Please wait before trying again.'),
    })
    renderRoute('/search?q=lights')

    expect(await screen.findByRole('alert')).toHaveTextContent('Search failed')
  })
})
