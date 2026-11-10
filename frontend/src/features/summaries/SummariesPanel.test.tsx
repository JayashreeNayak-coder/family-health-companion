import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../App'
import { page, requestBody, stubApi } from '../../test/stubApi'

const account = { id: 'a1', email: 'sam@example.test', displayName: 'Sam Synthetic' }
const profile = {
  id: 'p1',
  familyId: 'f1',
  fullName: 'Child Synthetic',
  dateOfBirth: null,
  dependent: true,
  linkedToAccount: false,
  accessLevel: 'EDIT',
  version: 0,
}

const summary = {
  id: 's1',
  profileId: 'p1',
  contentType: 'AI_GENERATED',
  promptVersion: 'summaries-v1',
  model: 'fake-model',
  periodFrom: '2026-01-01',
  periodTo: '2026-12-31',
  createdAt: '2026-11-09T10:00:00Z',
  points: [
    {
      topic: 'LAB_RESULTS',
      text: 'Synthetic HbA1c 5.8 % on 2026-01-10.',
      sources: [
        { ref: 'R1', type: 'LAB_RESULT', id: 'l1', date: '2026-01-10', title: 'Synthetic HbA1c', detail: '5.8 %', sourceType: 'DOCUMENTED' },
      ],
    },
  ],
  gaps: ['The condition has no onset date.'],
  conflicts: [],
  limitations: ['No visits are recorded in this period.'],
  disclaimer: 'AI-generated summary of recorded information. It is not medical advice.',
}

function openSummaries() {
  window.history.replaceState(null, '', '/profiles/p1/summaries')
  render(<App />)
}

describe('SummariesPanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('showsEachStatementWithTheRecordsItRestates', async () => {
    stubApi({
      'GET /auth/me': { status: 200, body: account },
      'GET /profiles/p1': { status: 200, body: profile },
      'GET /profiles/p1/summaries?size=20': page([summary]),
    })
    openSummaries()

    const statement = await screen.findByText('Synthetic HbA1c 5.8 % on 2026-01-10.')
    const item = statement.closest('li')!
    expect(within(item).getByText('Lab result')).toBeInTheDocument()
    expect(within(item).getByText('From document')).toBeInTheDocument()
    expect(screen.getByText('The condition has no onset date.')).toBeInTheDocument()
    expect(screen.getByText('No visits are recorded in this period.')).toBeInTheDocument()
    expect(screen.getAllByText('AI generated').length).toBeGreaterThan(0)
  })

  it('needsAConfirmationBeforeSendingRecords', async () => {
    const stub = stubApi({
      'GET /auth/me': { status: 200, body: account },
      'GET /profiles/p1': { status: 200, body: profile },
      'GET /profiles/p1/summaries?size=20': page([]),
      'POST /profiles/p1/summaries': { status: 201, body: summary },
    })
    openSummaries()

    fireEvent.change(await screen.findByLabelText('From'), { target: { value: '2026-01-01' } })
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-12-31' } })
    fireEvent.click(screen.getByRole('button', { name: 'Summarise records' }))
    expect(await screen.findByRole('alert')).toHaveTextContent("Confirm that Child Synthetic's records")
    expect(stub.mock.calls.some(([url, init]) => String(url).endsWith('/summaries') && init?.method === 'POST')).toBe(
      false,
    )

    fireEvent.click(screen.getByLabelText(/I understand that Child Synthetic/))
    fireEvent.click(screen.getByRole('button', { name: 'Summarise records' }))

    expect(await screen.findByText('Synthetic HbA1c 5.8 % on 2026-01-10.')).toBeInTheDocument()
    expect(requestBody(stub, 'POST', '/profiles/p1/summaries')).toEqual({
      periodFrom: '2026-01-01',
      periodTo: '2026-12-31',
    })
  })

  it('deletesASummaryAfterConfirming', async () => {
    const stub = stubApi({
      'GET /auth/me': { status: 200, body: account },
      'GET /profiles/p1': { status: 200, body: profile },
      'GET /profiles/p1/summaries?size=20': page([summary]),
      'DELETE /profiles/p1/summaries/s1': { status: 204 },
    })
    openSummaries()

    fireEvent.click(await screen.findByRole('button', { name: 'Delete summary: 2026-01-01 to 2026-12-31' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Delete' }))

    await waitFor(() => expect(screen.queryByText('Synthetic HbA1c 5.8 % on 2026-01-10.')).not.toBeInTheDocument())
    expect(stub.mock.calls.some(([url, init]) => url === '/api/v1/profiles/p1/summaries/s1' && init?.method === 'DELETE')).toBe(true)
  })
})
