import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { page, problem, requestBody, stubApi } from '../../test/stubApi'
import { DocumentsPanel } from './DocumentsPanel'

const document = {
  id: 'd1',
  profileId: 'p1',
  originalFilename: 'report.png',
  contentType: 'image/png',
  sizeBytes: 100,
  category: 'LAB_REPORT',
  documentOn: null,
  description: null,
  extractionStatus: 'READY_FOR_REVIEW',
  uploadedAt: '2026-11-06T10:00:00Z',
}

const lab = {
  id: 'i1',
  type: 'LAB_RESULT',
  payload: {
    testName: 'HbA1c',
    valueNumeric: 5.8,
    valueText: null,
    unit: null,
    resultOn: '2026-01-10',
    referenceRangeAsPrinted: null,
  },
  status: 'PENDING',
  confirmedRecordId: null,
  version: 0,
}

const allergy = {
  id: 'i2',
  type: 'ALLERGY',
  payload: { allergen: 'Synthetic pollen', reactionAsRecorded: null, severityAsRecorded: null, notes: null },
  status: 'PENDING',
  confirmedRecordId: null,
  version: 0,
}

const draft = {
  id: 'dr1',
  documentId: 'd1',
  status: 'PENDING_REVIEW',
  schemaVersion: 'extraction-v1',
  contentType: 'AI_GENERATED',
  disclaimer: 'AI-generated draft. Nothing is saved until you approve it.',
  limitations: ["Values the document doesn't show are left empty, never guessed."],
  items: [lab, allergy],
}

const LIST = 'GET /profiles/p1/documents?size=100'
const DRAFT = 'GET /profiles/p1/documents/d1/draft'

async function openReview() {
  render(<DocumentsPanel profileId="p1" canEdit canManage={false} />)
  fireEvent.click(await screen.findByRole('button', { name: 'Review AI draft' }))
  return screen.findByRole('form', { name: 'Draft lab result' })
}

describe('DraftReview', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('labelsTheDraftAsAiGeneratedAndMarksMissingValues', async () => {
    stubApi({ [LIST]: page([document]), [DRAFT]: { status: 200, body: draft } })

    const form = await openReview()

    expect(screen.getByText('AI generated · needs review')).toBeInTheDocument()
    expect(screen.getByText(/Nothing is saved until you approve it/)).toBeInTheDocument()
    expect(within(form).getByLabelText(/^Test/)).toHaveValue('HbA1c')
    expect(within(form).getByLabelText(/^Unit/)).toHaveValue('')
    expect(within(form).getAllByText('(not found in the document)').length).toBeGreaterThan(0)
  })

  it('approvesWithTheUsersCorrections', async () => {
    const stub = stubApi({
      [LIST]: page([document]),
      [DRAFT]: { status: 200, body: draft },
      'POST /profiles/p1/documents/d1/draft/items/i1/approve': {
        status: 200,
        body: { ...lab, status: 'APPROVED', confirmedRecordId: 'r1', version: 1 },
      },
    })
    const form = await openReview()

    fireEvent.change(within(form).getByLabelText(/^Unit/), { target: { value: '%' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Approve and save' }))

    expect(await screen.findByText(/saved as a record/)).toBeInTheDocument()
    expect(requestBody(stub, 'POST', '/profiles/p1/documents/d1/draft/items/i1/approve')).toEqual({
      version: 0,
      payload: {
        testName: 'HbA1c',
        valueNumeric: 5.8,
        valueText: null,
        unit: '%',
        resultOn: '2026-01-10',
        referenceRangeAsPrinted: null,
      },
    })
  })

  it('showsFieldErrorsAndKeepsTheItemPending', async () => {
    stubApi({
      [LIST]: page([document]),
      [DRAFT]: { status: 200, body: draft },
      'POST /profiles/p1/documents/d1/draft/items/i1/approve': problem(400, 'VALIDATION_FAILED', 'Validation failed', [
        { field: 'testName', message: 'must not be blank' },
      ]),
    })
    const form = await openReview()

    fireEvent.change(within(form).getByLabelText(/^Test/), { target: { value: '' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Approve and save' }))

    expect(await within(form).findByText('must not be blank')).toBeInTheDocument()
    expect(within(form).getByRole('button', { name: 'Approve and save' })).toBeEnabled()
  })

  it('discardsAnItemWithoutSavingIt', async () => {
    stubApi({
      [LIST]: page([document]),
      [DRAFT]: { status: 200, body: draft },
      'POST /profiles/p1/documents/d1/draft/items/i2/reject': {
        status: 200,
        body: { ...allergy, status: 'REJECTED', version: 1 },
      },
    })
    await openReview()
    const form = screen.getByRole('form', { name: 'Draft allergy' })

    fireEvent.click(within(form).getByRole('button', { name: 'Discard' }))

    expect(await screen.findByText(/discarded/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Discard the 1 remaining item' })).toBeInTheDocument()
  })
})

describe('ExtractionControls', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('startsReadingAndExplainsWhenAiIsUnavailable', async () => {
    stubApi({
      [LIST]: page([{ ...document, extractionStatus: 'NOT_REQUESTED' }]),
      'POST /profiles/p1/documents/d1/extraction': problem(
        503,
        'AI_UNAVAILABLE',
        "AI reading isn't available right now. You can still enter the details by hand.",
      ),
    })
    render(<DocumentsPanel profileId="p1" canEdit canManage={false} />)

    fireEvent.click(await screen.findByRole('button', { name: 'Read with AI: report.png' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('enter the details by hand')
  })

  it('explainsAFailureAndKeepsTheDocument', async () => {
    stubApi({
      [LIST]: page([{ ...document, extractionStatus: 'FAILED' }]),
      'GET /profiles/p1/documents/d1/extraction': {
        status: 200,
        body: { id: 'j1', documentId: 'd1', status: 'FAILED', errorCode: 'UNREADABLE', attempts: 1 },
      },
    })
    render(<DocumentsPanel profileId="p1" canEdit canManage={false} />)

    expect(await screen.findByText(/couldn't read this as a health document/)).toHaveTextContent(
      'The document is kept',
    )
    expect(screen.getByRole('button', { name: 'Read with AI again: report.png' })).toBeInTheDocument()
  })
})
