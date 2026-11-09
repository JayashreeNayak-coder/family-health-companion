import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { page, requestBody, stubApi } from '../../test/stubApi'
import { RecordSection } from './RecordSection'
import { VISITS } from './resources'

const visit = {
  id: 'v1',
  reason: 'Synthetic checkup',
  occurredOn: '2026-02-01',
  occurredTime: null,
  doctorName: null,
  facility: null,
  notes: null,
  sourceType: 'USER_REPORTED',
  sourceDocumentId: null,
  sourceDocumentDeletedAt: null,
  version: 0,
}

const document = {
  id: 'd1',
  profileId: 'p1',
  originalFilename: 'bill.png',
  contentType: 'image/png',
  sizeBytes: 100,
  category: 'BILL',
  documentOn: null,
  description: 'Synthetic clinic bill',
  extractionStatus: 'NOT_REQUESTED',
  uploadedAt: '2026-11-06T10:00:00Z',
}

const LIST = 'GET /profiles/p1/visits?size=100'
const ATTACHMENTS = 'GET /profiles/p1/attachments?recordType=VISIT'
const DOCUMENTS = 'GET /profiles/p1/documents?size=100'

describe('Record attachments', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('showsEachRecordsAttachmentsAndKeepsTheDocumentWhenOneIsRemoved', async () => {
    const stub = stubApi({
      [LIST]: page([visit]),
      [ATTACHMENTS]: [
        { status: 200, body: [{ id: 'a1', recordType: 'VISIT', recordId: 'v1', document }] },
        { status: 200, body: [] },
      ],
      'DELETE /profiles/p1/attachments/a1': { status: 204 },
    })
    render(<RecordSection profileId="p1" canEdit resource={VISITS} />)

    const list = await screen.findByRole('list', { name: 'Attachments for Synthetic checkup' })
    expect(within(list).getByText('Synthetic clinic bill')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Remove bill.png from Synthetic checkup' }))
    const dialog = screen.getByRole('dialog', { name: 'Remove this attachment?' })
    expect(dialog).toHaveTextContent('document itself is kept')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Remove' }))

    await waitFor(() => expect(screen.queryByText('Synthetic clinic bill')).not.toBeInTheDocument())
    expect(stub).toHaveBeenCalledWith('/api/v1/profiles/p1/attachments/a1', expect.anything())
  })

  it('attachesAnAlreadyUploadedDocument', async () => {
    const stub = stubApi({
      [LIST]: page([visit]),
      [DOCUMENTS]: page([document]),
      'POST /profiles/p1/attachments': {
        status: 201,
        body: { id: 'a1', recordType: 'VISIT', recordId: 'v1', document },
      },
    })
    render(<RecordSection profileId="p1" canEdit resource={VISITS} />)

    fireEvent.click(await screen.findByRole('button', { name: 'Attach from Documents to Synthetic checkup' }))
    fireEvent.change(await screen.findByLabelText('Document'), { target: { value: 'd1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Attach' }))

    await screen.findByRole('button', { name: 'Attach a file to Synthetic checkup' })
    expect(requestBody(stub, 'POST', '/profiles/p1/attachments')).toEqual({
      recordType: 'VISIT',
      recordId: 'v1',
      documentId: 'd1',
    })
  })

  it('offersNoAttachingWithoutEditAccess', async () => {
    stubApi({ [LIST]: page([visit]) })

    render(<RecordSection profileId="p1" canEdit={false} resource={VISITS} />)

    expect(await screen.findByText('Synthetic checkup')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Attach/ })).not.toBeInTheDocument()
  })

  it('letsANewRecordNameItsSourceDocument', async () => {
    const stub = stubApi({
      [LIST]: page([]),
      [DOCUMENTS]: page([document]),
      'POST /profiles/p1/visits': { status: 201, body: visit },
    })
    render(<RecordSection profileId="p1" canEdit resource={VISITS} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Add visit' }))

    fireEvent.change(await screen.findByLabelText('Source'), { target: { value: 'd1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await screen.findByRole('button', { name: 'Add visit' })
    expect(requestBody(stub, 'POST', '/profiles/p1/visits').sourceDocumentId).toBe('d1')
  })
})
