import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../App'
import { page, problem, requestBody, stubApi } from '../../test/stubApi'

const TOKEN = 'A'.repeat(43)
const account = { id: 'a1', email: 'sam@example.test', displayName: 'Sam Synthetic' }

function profile(accessLevel: string) {
  return {
    id: 'p1',
    familyId: 'f1',
    fullName: 'Child Synthetic',
    dateOfBirth: null,
    dependent: true,
    linkedToAccount: false,
    accessLevel,
    version: 0,
  }
}

const emptyCard = { enabled: false, linkCreatedAt: null, items: [], version: null }
const allergy = { id: 'al1', version: 0, allergen: 'Synthetic peanut', reactionAsRecorded: 'Hives' }
const contact = { id: 'c1', version: 0, name: 'Synthetic Parent', relationship: 'Parent', phone: '+10000000000' }

const savedCard = {
  enabled: false,
  linkCreatedAt: null,
  version: 0,
  items: [
    { type: 'PERSON', text: 'Child S.', sourceRecordId: null, sourceStatus: 'NONE' },
    { type: 'ALLERGY', text: 'Synthetic peanut: Hives', sourceRecordId: 'al1', sourceStatus: 'CURRENT' },
  ],
}

function openCard(routes: Parameters<typeof stubApi>[0]) {
  const stub = stubApi({
    'GET /auth/me': { status: 200, body: account },
    'GET /profiles/p1': { status: 200, body: profile('MANAGE') },
    ...routes,
  })
  window.history.replaceState(null, '', '/profiles/p1/emergency-card')
  render(<App />)
  return stub
}

describe('Public emergency card', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.head.querySelectorAll('meta[name="robots"]').forEach((meta) => meta.remove())
  })

  it('showsTheApprovedLinesGroupedWithoutSigningIn', async () => {
    const stub = stubApi({
      'GET /auth/me': problem(401, 'UNAUTHENTICATED', 'Sign in to continue'),
      [`GET /public/emergency-cards/${TOKEN}`]: {
        status: 200,
        body: {
          items: [
            { type: 'PERSON', text: 'Child S.' },
            { type: 'ALLERGY', text: 'Synthetic peanut: Hives' },
            { type: 'EMERGENCY_CONTACT', text: 'Synthetic Parent (Parent): +10000000000' },
          ],
        },
      },
    })
    window.history.replaceState(null, '', `/emergency-card#${TOKEN}`)
    render(<App />)

    expect(await screen.findByText('Synthetic peanut: Hives')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Emergency information' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Allergies' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Emergency contacts' })).toBeInTheDocument()
    expect(screen.getByText('Child S.')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Medications' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /sign in/i })).not.toBeInTheDocument()
    expect(document.head.querySelector('meta[name="robots"]')?.getAttribute('content')).toContain('noindex')
    expect(stub.mock.calls.some(([url]) => String(url).includes('/profiles'))).toBe(false)
  })

  it('explainsALinkThatNoLongerWorks', async () => {
    stubApi({
      'GET /auth/me': problem(401, 'UNAUTHENTICATED', 'Sign in to continue'),
      [`GET /public/emergency-cards/${TOKEN}`]: problem(404, 'NOT_FOUND', 'Not found'),
    })
    window.history.replaceState(null, '', `/emergency-card#${TOKEN}`)
    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent("This emergency card link doesn't work")
  })

  it('needsNoRequestWhenTheLinkHasNoToken', async () => {
    const stub = stubApi({ 'GET /auth/me': problem(401, 'UNAUTHENTICATED', 'Sign in to continue') })
    window.history.replaceState(null, '', '/emergency-card')
    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent("This emergency card link doesn't work")
    expect(stub.mock.calls.some(([url]) => String(url).includes('/public/'))).toBe(false)
  })
})

describe('EmergencyCardPanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('isATabForManagersOnly', async () => {
    stubApi({
      'GET /auth/me': { status: 200, body: account },
      'GET /profiles/p1': { status: 200, body: profile('EDIT') },
    })
    window.history.replaceState(null, '', '/profiles/p1/emergency-card')
    render(<App />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Child Synthetic' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Emergency card' })).not.toBeInTheDocument()
    expect(screen.queryByText('What the card shows')).not.toBeInTheDocument()
  })

  it('savesOnlyTheTickedLinesWithTheTextTheOwnerApproved', async () => {
    const stub = openCard({
      'GET /profiles/p1/emergency-card': { status: 200, body: emptyCard },
      'GET /profiles/p1/allergies?size=100': page([allergy]),
      'GET /profiles/p1/emergency-contacts?size=100': page([contact]),
      'PUT /profiles/p1/emergency-card': { status: 200, body: savedCard },
    })

    const turnOn = await screen.findByRole('button', { name: 'Turn on' })
    expect(turnOn).toBeDisabled()
    expect(screen.getByText('Choose and save at least one line first.')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Synthetic peanut: Hives'))
    expect(screen.getByLabelText('Card text for Synthetic peanut: Hives')).toHaveValue('Synthetic peanut: Hives')
    fireEvent.change(screen.getByLabelText('Name on the card'), { target: { value: 'Child S.' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save card' }))

    expect(await screen.findByText('Emergency card saved')).toBeInTheDocument()
    expect(requestBody(stub, 'PUT', '/profiles/p1/emergency-card')).toEqual({
      version: null,
      items: [
        { type: 'PERSON', text: 'Child S.', sourceRecordId: null },
        { type: 'ALLERGY', text: 'Synthetic peanut: Hives', sourceRecordId: 'al1' },
      ],
    })
    expect(screen.getByRole('button', { name: 'Turn on' })).toBeEnabled()
  })

  it('warnsBeforeTurningOnAndShowsTheLinkOnce', async () => {
    openCard({
      'GET /profiles/p1/emergency-card': { status: 200, body: savedCard },
      'GET /profiles/p1/allergies?size=100': page([allergy]),
      'POST /profiles/p1/emergency-card/enable': {
        status: 200,
        body: {
          link: `http://localhost:5173/emergency-card#${TOKEN}`,
          card: { ...savedCard, enabled: true, linkCreatedAt: '2026-11-10T10:00:00Z' },
        },
      },
    })

    fireEvent.click(await screen.findByRole('button', { name: 'Turn on' }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Anyone with the link or QR code will be able to see the 2 saved lines')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Turn on' }))

    expect(await screen.findByText('Save this link now. It is shown only once.')).toBeInTheDocument()
    expect(screen.getByLabelText('Emergency card link')).toHaveValue(`http://localhost:5173/emergency-card#${TOKEN}`)
    expect(screen.getByRole('img', { name: 'QR code for the emergency card link' })).toBeInTheDocument()
    expect(screen.getByText('On')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Make a new link' })).toBeInTheDocument()
  })

  it('flagsLinesWhoseRecordChangedOrWasDeleted', async () => {
    openCard({
      'GET /profiles/p1/emergency-card': {
        status: 200,
        body: {
          ...savedCard,
          items: [
            { type: 'ALLERGY', text: 'Synthetic peanut: Hives', sourceRecordId: 'al1', sourceStatus: 'CHANGED' },
            { type: 'MEDICATION', text: 'Old synthetic medicine', sourceRecordId: 'm1', sourceStatus: 'DELETED' },
          ],
        },
      },
      'GET /profiles/p1/allergies?size=100': page([{ ...allergy, reactionAsRecorded: 'Swelling' }]),
    })

    expect(await screen.findByText(/This record has changed since you saved the line/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: "Use the record's current text" }))
    expect(screen.getByLabelText('Card text for Synthetic peanut: Swelling')).toHaveValue('Synthetic peanut: Swelling')
    expect(screen.getByText(/The record this line came from was deleted/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save card' })).toBeEnabled()
  })
})
