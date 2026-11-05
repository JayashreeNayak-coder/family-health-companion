import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { problem, stubApi } from '../../test/stubApi'
import { TimelinePanel } from './TimelinePanel'

const entry = {
  type: 'VISIT',
  id: 'v1',
  date: '2026-03-10',
  time: '08:30',
  title: 'Checkup',
  detail: 'Dr. Synthetic',
  sourceType: 'USER_REPORTED',
  sourceDocumentId: null,
  sourceDocumentDeletedAt: null,
}

const PATH = 'GET /profiles/p1/timeline'

describe('TimelinePanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('showsLoadingThenTheEmptyState', async () => {
    stubApi({ [PATH]: { status: 200, body: { entries: [], dateUnknown: [] } } })

    render(<TimelinePanel profileId="p1" />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading')
    expect(await screen.findByText('No records to show.')).toBeInTheDocument()
  })

  it('showsAnError', async () => {
    stubApi({ [PATH]: problem(500, 'INTERNAL_ERROR', 'Something went wrong') })

    render(<TimelinePanel profileId="p1" />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('showsEntriesWithSourceAndUndatedRecordsInTheirOwnGroup', async () => {
    stubApi({
      [PATH]: {
        status: 200,
        body: {
          entries: [entry],
          dateUnknown: [{ ...entry, id: 'v2', date: null, time: null, title: 'Undated visit', detail: null }],
        },
      },
    })

    render(<TimelinePanel profileId="p1" />)

    expect(await screen.findByText('Checkup')).toBeInTheDocument()
    expect(screen.getByText('2026-03-10 08:30')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Date unknown' })).toBeInTheDocument()
    expect(screen.getByText('Undated visit')).toBeInTheDocument()
    expect(screen.getAllByText('User reported')).toHaveLength(2)
  })

  it('sendsTheChosenFilters', async () => {
    const stub = stubApi({
      [PATH]: { status: 200, body: { entries: [], dateUnknown: [] } },
      [`${PATH}?types=VISIT`]: { status: 200, body: { entries: [entry], dateUnknown: [] } },
    })
    render(<TimelinePanel profileId="p1" />)
    await screen.findByText('No records to show.')

    fireEvent.click(screen.getByLabelText('Visits'))

    expect(await screen.findByText('Checkup')).toBeInTheDocument()
    expect(stub.mock.calls.some(([url]) => url.endsWith('?types=VISIT'))).toBe(true)
  })
})
