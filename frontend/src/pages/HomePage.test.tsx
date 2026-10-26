import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { renderRoute } from '../test/renderRoute'

function mockFetch(impl: () => Promise<Response>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(impl)
}

describe('HomePage backend status', () => {
  it('shows a loading state while checking', () => {
    mockFetch(() => new Promise(() => {}))
    renderRoute('/')
    expect(screen.getByRole('status')).toHaveTextContent('Checking backend')
  })

  it('reports the backend as up when health is UP', async () => {
    const fetchSpy = mockFetch(async () => Response.json({ status: 'UP' }))
    renderRoute('/')
    expect(await screen.findByText(/backend is up/i)).toBeInTheDocument()
    expect(fetchSpy).toHaveBeenCalledWith('/actuator/health', expect.objectContaining({ credentials: 'same-origin' }))
  })

  it('reports the backend as unavailable on a server error', async () => {
    mockFetch(async () => new Response(null, { status: 503 }))
    renderRoute('/')
    expect(await screen.findByText(/backend unavailable/i)).toBeInTheDocument()
  })

  it('reports the backend as unavailable on a network failure', async () => {
    mockFetch(async () => {
      throw new TypeError('Failed to fetch')
    })
    renderRoute('/')
    expect(await screen.findByText(/backend unavailable/i)).toBeInTheDocument()
  })
})

describe('routing', () => {
  it('renders a not-found page for unknown paths', () => {
    mockFetch(() => new Promise(() => {}))
    renderRoute('/does-not-exist')
    expect(screen.getByRole('heading', { name: /page not found/i })).toBeInTheDocument()
  })
})
