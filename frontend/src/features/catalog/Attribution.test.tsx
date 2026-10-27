import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { json, mockApi, signedOut } from '../../test/mockApi'
import { renderRoute } from '../../test/renderRoute'

describe('TMDB attribution', () => {
  it('shows the required notice and logo when TMDB is the data source', async () => {
    mockApi({
      'GET /api/v1/auth/me': signedOut,
      'GET /api/v1/catalog/source': () => json(200, { provider: 'tmdb', mock: false }),
    })
    renderRoute('/')

    expect(
      await screen.findByText('This product uses the TMDB API but is not endorsed or certified by TMDB.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'TMDB' })).toHaveAttribute('src', '/tmdb-logo.svg')
  })

  it('does not claim TMDB as the source when mock data is active', async () => {
    mockApi({
      'GET /api/v1/auth/me': signedOut,
      'GET /api/v1/catalog/source': () => json(200, { provider: 'mock', mock: true }),
    })
    renderRoute('/')

    await screen.findByRole('heading', { name: 'Track every episode.' })
    expect(screen.queryByText(/uses the TMDB API/)).not.toBeInTheDocument()
  })
})
