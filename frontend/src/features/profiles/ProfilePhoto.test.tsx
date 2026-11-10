import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../App'
import { page, stubApi } from '../../test/stubApi'

const account = { id: 'a1', email: 'sam@example.test', displayName: 'Sam Synthetic' }

function profile(accessLevel: string, photoUpdatedAt: string | null = null) {
  return {
    id: 'p1',
    familyId: 'f1',
    fullName: 'Child Synthetic',
    dateOfBirth: null,
    dependent: true,
    linkedToAccount: false,
    accessLevel,
    photoUpdatedAt,
    version: 2,
  }
}

const summary = {
  id: 's1',
  profileId: 'p1',
  contentType: 'AI_GENERATED',
  promptVersion: 'summaries-v1',
  model: 'fake-model',
  periodFrom: null,
  periodTo: null,
  createdAt: '2026-11-09T10:00:00Z',
  points: [],
  gaps: [],
  conflicts: [],
  limitations: [],
  disclaimer: 'AI-generated summary of recorded information. It is not medical advice.',
}

function open(path: string) {
  window.history.replaceState(null, '', path)
  return render(<App />)
}

describe('Profile photo', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('anEditorAddsAPhotoAndTheHeaderShowsIt', async () => {
    const stub = stubApi({
      'GET /auth/me': { status: 200, body: account },
      'GET /profiles/p1': { status: 200, body: profile('EDIT') },
      'PUT /profiles/p1/photo': { status: 200, body: profile('EDIT', '2026-11-10T12:00:00Z') },
    })
    const { container } = open('/profiles/p1/details')

    const input = await screen.findByLabelText('Add a photo')
    expect(container.querySelector('img')).toBeNull()
    fireEvent.change(input, { target: { files: [new File(['png'], 'me.png', { type: 'image/png' })] } })

    expect(await screen.findByRole('button', { name: 'Remove photo' })).toBeInTheDocument()
    expect(container.querySelector('header img')?.getAttribute('src')).toBe(
      '/api/v1/profiles/p1/photo?v=2026-11-10T12%3A00%3A00Z',
    )
    expect(stub.mock.calls.some(([url, init]) => url === '/api/v1/profiles/p1/photo' && init?.method === 'PUT')).toBe(
      true,
    )
  })

  it('aLargeFileIsRefusedBeforeUploading', async () => {
    const stub = stubApi({
      'GET /auth/me': { status: 200, body: account },
      'GET /profiles/p1': { status: 200, body: profile('MANAGE') },
    })
    open('/profiles/p1/details')

    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.jpg', { type: 'image/jpeg' })
    fireEvent.change(await screen.findByLabelText('Add a photo'), { target: { files: [big] } })

    expect(await screen.findByText('A photo can be at most 5 MB.')).toBeInTheDocument()
    expect(stub.mock.calls.some(([url]) => String(url).endsWith('/photo'))).toBe(false)
  })

  it('viewersSeeThePhotoButCannotChangeIt', async () => {
    stubApi({
      'GET /auth/me': { status: 200, body: account },
      'GET /profiles/p1': { status: 200, body: profile('VIEW', '2026-11-10T12:00:00Z') },
    })
    const { container } = open('/profiles/p1/details')

    expect(await screen.findByText('You have view-only access to this profile.')).toBeInTheDocument()
    expect(screen.queryByLabelText(/photo/i)).not.toBeInTheDocument()
    expect(container.querySelector('header img')).not.toBeNull()
  })
})

describe('Summary PDF', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('downloadsThroughTheApiAsAFile', async () => {
    URL.createObjectURL = vi.fn(() => 'blob:synthetic-pdf')
    URL.revokeObjectURL = vi.fn()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    stubApi({
      'GET /auth/me': { status: 200, body: account },
      'GET /profiles/p1': { status: 200, body: profile('VIEW') },
      'GET /profiles/p1/summaries?size=20': page([summary]),
      'GET /profiles/p1/summaries/s1/pdf': { status: 200, body: '%PDF-synthetic' },
    })
    open('/profiles/p1/summaries')

    fireEvent.click(await screen.findByRole('button', { name: /^Download PDF/ }))

    await vi.waitFor(() => expect(click).toHaveBeenCalled())
    const link = click.mock.contexts[0] as HTMLAnchorElement
    expect(link.download).toBe('health-record-summary-2026-11-09.pdf')
    expect(link.href).toBe('blob:synthetic-pdf')
  })
})
