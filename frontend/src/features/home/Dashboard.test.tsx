import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { page, problem, requestBody, stubApi } from '../../test/stubApi'
import { App } from '../../App'
import { Dashboard } from './Dashboard'

const family = { id: 'f1', name: 'Synthetic Family', role: 'MANAGER' }

const familyDetail = {
  ...family,
  members: [{ accountId: 'a1', displayName: 'Sam', email: 'sam@example.test', role: 'MANAGER' }],
}

const profile = {
  id: 'p1',
  familyId: 'f1',
  fullName: 'Child Synthetic',
  dateOfBirth: null,
  dependent: true,
  linkedToAccount: false,
  accessLevel: 'MANAGE',
  version: 0,
}

function type(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

function click(name: string) {
  fireEvent.click(screen.getByRole('button', { name }))
}

const signedIn = { status: 200, body: { id: 'a1', email: 'sam@example.test', displayName: 'Sam' } }

/** Opens a profile from Home through the whole app, as a user would. */
async function openProfileFromHome(name: string) {
  render(<App />)
  const main = await screen.findByRole('main')
  fireEvent.click(await within(main).findByRole('link', { name }))
  expect(window.location.pathname).toBe('/profiles/p1')
}

describe('Dashboard', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('showsLoadingThenTheEmptyState', async () => {
    stubApi({})

    render(<Dashboard />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading')
    expect(await screen.findByText(/No profiles yet/)).toBeInTheDocument()
  })

  it('showsAnErrorWhenTheListsCannotLoad', async () => {
    stubApi({ 'GET /profiles?size=100': problem(500, 'INTERNAL_ERROR', 'Something went wrong') })

    render(<Dashboard />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })

  it('createsAFamilyAndRefreshes', async () => {
    const stub = stubApi({
      'POST /families': { status: 201, body: family },
      'GET /families?size=100': [page([]), page([family])],
      'GET /families/f1': { status: 200, body: familyDetail },
    })
    render(<Dashboard />)
    await screen.findByText(/No profiles yet/)

    type('Family name', 'Synthetic Family')
    click('Create family')

    expect(await screen.findByRole('heading', { name: 'Synthetic Family' })).toBeInTheDocument()
    expect(screen.getByText('You are a manager')).toBeInTheDocument()
    expect(requestBody(stub, 'POST', '/families')).toEqual({ name: 'Synthetic Family' })
  })

  it('addsADependentProfile', async () => {
    const stub = stubApi({
      'GET /families?size=100': page([family]),
      'GET /families/f1': { status: 200, body: familyDetail },
      'POST /families/f1/profiles': { status: 201, body: profile },
      'GET /profiles?size=100': [page([]), page([profile])],
    })
    render(<Dashboard />)
    await screen.findByRole('heading', { name: 'Synthetic Family' })
    expect(screen.getByText(/No profiles in this family/)).toBeInTheDocument()

    click('Add a person')
    expect(screen.getByRole('dialog', { name: 'Add a person to Synthetic Family' })).toBeInTheDocument()
    type('Full name', 'Child Synthetic')
    type('Who is this for?', 'DEPENDENT')
    click('Add profile')

    const card = await screen.findByRole('link', { name: 'Child Synthetic' })
    expect(card).toHaveAttribute('href', '/profiles/p1')
    expect(card).toHaveTextContent('Dependent')
    expect(card).toHaveTextContent('You manage')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(requestBody(stub, 'POST', '/families/f1/profiles')).toEqual({
      fullName: 'Child Synthetic',
      dateOfBirth: null,
      kind: 'DEPENDENT',
    })
  })

  it('hidesManagerControlsFromAMember', async () => {
    const member = { ...family, role: 'MEMBER' }
    stubApi({
      'GET /families?size=100': page([member]),
      'GET /families/f1': { status: 200, body: { ...familyDetail, role: 'MEMBER' } },
    })
    render(<Dashboard />)
    await screen.findByRole('heading', { name: 'Synthetic Family' })

    expect(screen.queryByRole('button', { name: 'Invite someone' })).not.toBeInTheDocument()
    click('Add a person')
    expect(within(screen.getByLabelText('Who is this for?')).queryByText('A dependent')).toBeNull()
  })

  it('showsTheInvitationCodeOnce', async () => {
    stubApi({
      'GET /families?size=100': page([family]),
      'GET /families/f1': { status: 200, body: familyDetail },
      'POST /families/f1/invitations': {
        status: 201,
        body: { id: 'i1', email: 'new@example.test', expiresAt: '2026-11-12T00:00:00Z', token: 'code-1' },
      },
    })
    render(<Dashboard />)
    const writeText = vi.fn(() => Promise.resolve())
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } })
    await screen.findByRole('heading', { name: 'Synthetic Family' })

    click('Invite someone')
    type('Their email', 'new@example.test')
    click('Create invitation')

    expect(await screen.findByText('code-1')).toBeInTheDocument()
    expect(screen.getByText(/shown only once/)).toBeInTheDocument()

    click('Copy')
    expect(await screen.findByRole('button', { name: 'Copied' })).toBeInTheDocument()
    expect(writeText).toHaveBeenCalledWith('code-1')
  })

  it('asksTheAdultWhatAccessOthersKeepWhenAnInvitationLinksAProfile', async () => {
    const stub = stubApi({
      'GET /families/invitations/code-1': {
        status: 200,
        body: { familyName: 'Synthetic Family', role: 'MEMBER', profileName: 'Adult Synthetic' },
      },
      'POST /families/invitations/code-1/accept': { status: 200, body: family },
    })
    render(<Dashboard />)
    await screen.findByText(/No profiles yet/)

    type('Invitation code', 'code-1')
    click('Check invitation')
    await screen.findByText(/will become yours/)
    type('The profile “Adult Synthetic” will become yours. How much access should others keep?', 'VIEW')
    click('Accept invitation')

    await screen.findByLabelText('Invitation code')
    expect(requestBody(stub, 'POST', '/families/invitations/code-1/accept')).toEqual({
      keepAccess: 'VIEW',
    })
  })

  it('showsAnInvalidInvitationError', async () => {
    stubApi({
      'GET /families/invitations/bad': problem(404, 'INVITATION_NOT_FOUND', 'This invitation is not valid'),
    })
    render(<Dashboard />)
    await screen.findByText(/No profiles yet/)

    type('Invitation code', 'bad')
    click('Check invitation')

    expect(await screen.findByRole('alert')).toHaveTextContent('not valid')
  })

  it('opensAProfileAndHidesEditingForViewAccess', async () => {
    const viewOnly = { ...profile, accessLevel: 'VIEW', dependent: false }
    stubApi({
      'GET /profiles?size=100': page([viewOnly]),
      'GET /profiles/p1': { status: 200, body: viewOnly },
      'GET /auth/me': signedIn,
    })
    await openProfileFromHome('Child Synthetic')

    expect(await screen.findByText(/view-only access/)).toBeInTheDocument()
    expect(screen.getByText('Not recorded')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save details' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Access' })).not.toBeInTheDocument()
  })

  it('managesAccessAndShowsTheLastManagerConflict', async () => {
    stubApi({
      'GET /profiles?size=100': page([profile]),
      'GET /profiles/p1': { status: 200, body: profile },
      'GET /profiles/p1/grants': {
        status: 200,
        body: [{ accountId: 'a1', displayName: 'Sam', email: 'sam@example.test', level: 'MANAGE', linkedAccount: false }],
      },
      'GET /families/f1': { status: 200, body: familyDetail },
      'DELETE /profiles/p1/grants/a1': problem(409, 'LAST_MANAGER', 'At least one person must keep manage access'),
      'GET /auth/me': signedIn,
    })
    await openProfileFromHome('Child Synthetic')
    fireEvent.click(await screen.findByRole('link', { name: 'Access' }))
    expect(window.location.pathname).toBe('/profiles/p1/access')

    fireEvent.click(await screen.findByRole('button', { name: 'Remove' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('must keep manage access')
  })

  it('deletesAProfileOnlyAfterConfirming', async () => {
    const stub = stubApi({
      'GET /profiles?size=100': page([profile]),
      'GET /profiles/p1': { status: 200, body: profile },
      'DELETE /profiles/p1': { status: 204 },
      'GET /auth/me': signedIn,
    })
    await openProfileFromHome('Child Synthetic')
    await screen.findByRole('button', { name: 'Delete profile' })

    click('Delete profile')
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(stub.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)

    click('Delete profile')
    const dialog = screen.getByRole('dialog', { name: 'Delete Child Synthetic?' })
    expect(dialog).toHaveTextContent("can't be undone")
    fireEvent.click(within(dialog).getByRole('button', { name: 'Delete profile' }))

    await waitFor(() => expect(window.location.pathname).toBe('/'))
    await screen.findByText('Welcome, Sam')
    expect(stub.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true)
  })
})
