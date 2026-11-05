import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from './App'

function stubFetch(implementation: () => Promise<Response>) {
  vi.stubGlobal('fetch', vi.fn(implementation))
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status })
}

describe('App backend connection', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('showsLoadingStateBeforeResponse', async () => {
    stubFetch(() => new Promise(() => {}))

    render(<App />)

    expect(screen.getByRole('status')).toHaveTextContent('Checking backend connection')
    expect(fetch).toHaveBeenCalledWith('/actuator/health')
  })

  it('showsConnectedWhenBackendIsUp', async () => {
    stubFetch(async () => jsonResponse(200, { status: 'UP' }))

    render(<App />)

    expect(await screen.findByText('Backend connected')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('showsErrorWhenBackendReportsDown', async () => {
    stubFetch(async () => jsonResponse(503, { status: 'DOWN' }))

    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Backend unavailable')
    expect(screen.queryByText('Backend connected')).not.toBeInTheDocument()
  })

  it('showsErrorWhenBackendIsUnreachable', async () => {
    stubFetch(async () => {
      throw new TypeError('Failed to fetch')
    })

    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Backend unavailable')
  })
})
