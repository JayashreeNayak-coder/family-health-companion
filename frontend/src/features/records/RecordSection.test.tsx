import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { page, problem, requestBody, stubApi } from '../../test/stubApi'
import { RecordSection } from './RecordSection'
import { ALLERGIES, CONTACTS } from './resources'

const allergy = {
  id: 'r1',
  allergen: 'Synthetic pollen',
  reactionAsRecorded: null,
  severityAsRecorded: 'Mild',
  notes: null,
  sourceType: 'USER_REPORTED',
  sourceDocumentId: null,
  sourceDocumentDeletedAt: null,
  version: 3,
}

const LIST = 'GET /profiles/p1/allergies?size=100'

function type(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

function renderAllergies(canEdit = true) {
  render(<RecordSection profileId="p1" canEdit={canEdit} resource={ALLERGIES} />)
}

describe('RecordSection', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('showsLoadingThenAnEmptyStateThatDoesNotClaimThereAreNone', async () => {
    stubApi({ [LIST]: page([]) })

    renderAllergies()

    expect(screen.getByRole('status')).toHaveTextContent('Loading')
    expect(await screen.findByText(/None recorded/)).toHaveTextContent("doesn't mean there are none")
  })

  it('showsALoadError', async () => {
    stubApi({ [LIST]: problem(500, 'INTERNAL_ERROR', 'Something went wrong') })

    renderAllergies()

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('showsMissingValuesAsMissingAndTheSourceLabel', async () => {
    stubApi({ [LIST]: page([allergy]) })

    renderAllergies()

    expect(await screen.findByText('Synthetic pollen')).toBeInTheDocument()
    expect(screen.getAllByText('Not recorded')).toHaveLength(2)
    expect(screen.getByText('Mild')).toBeInTheDocument()
    expect(screen.getByText('User reported')).toBeInTheDocument()
  })

  it('labelsDocumentedRecordsAndADeletedSource', async () => {
    stubApi({
      [LIST]: page([
        { ...allergy, sourceType: 'DOCUMENTED', sourceDocumentDeletedAt: '2026-11-05T00:00:00Z' },
      ]),
    })

    renderAllergies()

    expect(await screen.findByText('From document · source document deleted')).toBeInTheDocument()
  })

  it('hidesTheSourceLabelForContactsWhichAreNotHealthRecords', async () => {
    stubApi({
      'GET /profiles/p1/emergency-contacts?size=100': page([
        { id: 'c1', name: 'Synthetic Contact', relationship: null, phone: '555', version: 0 },
      ]),
    })

    render(<RecordSection profileId="p1" canEdit resource={CONTACTS} />)

    expect(await screen.findByText('Synthetic Contact')).toBeInTheDocument()
    expect(screen.queryByText('User reported')).not.toBeInTheDocument()
  })

  it('hidesEditingControlsWithoutEditAccess', async () => {
    stubApi({ [LIST]: page([allergy]) })

    renderAllergies(false)

    await screen.findByText('Synthetic pollen')
    expect(screen.queryByRole('button', { name: /Edit/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Delete/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add allergy' })).not.toBeInTheDocument()
  })

  it('createsARecordSendingBlankFieldsAsNull', async () => {
    const stub = stubApi({
      [LIST]: [page([]), page([allergy])],
      'POST /profiles/p1/allergies': { status: 201, body: allergy },
    })
    renderAllergies()
    fireEvent.click(await screen.findByRole('button', { name: 'Add allergy' }))

    type('Allergen', 'Synthetic pollen')
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Synthetic pollen')).toBeInTheDocument()
    expect(requestBody(stub, 'POST', '/profiles/p1/allergies')).toEqual({
      allergen: 'Synthetic pollen',
      reactionAsRecorded: null,
      severityAsRecorded: null,
      notes: null,
    })
  })

  it('updatesARecordWithItsVersionAndReportsAStaleEdit', async () => {
    const stub = stubApi({
      [LIST]: page([allergy]),
      'PUT /profiles/p1/allergies/r1': problem(409, 'STALE_VERSION', 'This item was changed by someone else'),
    })
    renderAllergies()
    fireEvent.click(await screen.findByRole('button', { name: 'Edit Synthetic pollen' }))

    expect(screen.getByLabelText('Severity, as recorded (optional)')).toHaveValue('Mild')
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('changed by someone else')
    expect(requestBody(stub, 'PUT', '/profiles/p1/allergies/r1')).toMatchObject({ version: 3 })
  })

  it('showsValidationErrorsBesideTheirFields', async () => {
    stubApi({
      'GET /profiles/p1/emergency-contacts?size=100': page([]),
      'POST /profiles/p1/emergency-contacts': problem(400, 'VALIDATION_FAILED', 'Validation failed', [
        { field: 'phone', message: 'must be a phone number' },
      ]),
    })
    render(<RecordSection profileId="p1" canEdit resource={CONTACTS} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Add contact' }))

    type('Name', 'Synthetic Contact')
    type('Phone', 'abc')
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('must be a phone number')).toBeInTheDocument()
  })

  it('deletesOnlyAfterConfirming', async () => {
    const stub = stubApi({
      [LIST]: [page([allergy]), page([])],
      'DELETE /profiles/p1/allergies/r1': { status: 204 },
    })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false)
    renderAllergies()
    const remove = await screen.findByRole('button', { name: 'Delete Synthetic pollen' })

    fireEvent.click(remove)
    expect(stub.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)

    confirm.mockReturnValue(true)
    fireEvent.click(remove)

    expect(await screen.findByText(/None recorded/)).toBeInTheDocument()
  })
})
