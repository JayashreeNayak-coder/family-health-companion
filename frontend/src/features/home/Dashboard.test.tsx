import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { page, problem, requestBody, stubApi } from '../../test/stubApi'
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

    type('Full name', 'Child Synthetic')
    type('Who is this for?', 'DEPENDENT')
    click('Add profile')

    expect(await screen.findByRole('button', { name: 'Child Synthetic' })).toBeInTheDocument()
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

    expect(screen.queryByRole('button', { name: 'Create invitation' })).not.toBeInTheDocument()
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
    await screen.findByRole('heading', { name: 'Synthetic Family' })

    type('Their email', 'new@example.test')
    click('Create invitation')

    expect(await screen.findByText('code-1')).toBeInTheDocument()
    expect(screen.getByText(/shown only once/)).toBeInTheDocument()
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
    })
    render(<Dashboard />)

    fireEvent.click(await screen.findByRole('button', { name: 'Child Synthetic' }))

    expect(await screen.findByText(/view-only access/)).toBeInTheDocument()
    expect(screen.getByText('Not recorded')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save details' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Access' })).not.toBeInTheDocument()
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
    })
    render(<Dashboard />)
    fireEvent.click(await screen.findByRole('button', { name: 'Child Synthetic' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Access' }))

    fireEvent.click(await screen.findByRole('button', { name: 'Remove' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('must keep manage access')
  })

  it('deletesAProfileOnlyAfterConfirming', async () => {
    const stub = stubApi({
      'GET /profiles?size=100': page([profile]),
      'GET /profiles/p1': { status: 200, body: profile },
      'DELETE /profiles/p1': { status: 204 },
    })
    render(<Dashboard />)
    fireEvent.click(await screen.findByRole('button', { name: 'Child Synthetic' }))
    await screen.findByRole('button', { name: 'Delete profile' })

    click('Delete profile')
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(stub.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)

    click('Delete profile')
    const dialog = screen.getByRole('dialog', { name: 'Delete Child Synthetic?' })
    expect(dialog).toHaveTextContent("can't be undone")
    fireEvent.click(within(dialog).getByRole('button', { name: 'Delete profile' }))

    await screen.findByRole('heading', { name: 'Health profiles' })
    expect(stub.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true)
  })
})
