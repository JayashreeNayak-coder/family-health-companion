import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { page, problem, requestBody, stubApi } from '../../test/stubApi'
import { ToastProvider } from '../../components/Toast'
import { Dashboard } from './Dashboard'

const family = { id: 'f1', name: 'Synthetic Family', role: 'MANAGER', photoUpdatedAt: null, version: 3 }
const sam = { accountId: 'a1', displayName: 'Sam', email: 'sam@example.test', role: 'MANAGER' }
const alex = { accountId: 'a2', displayName: 'Alex', email: 'alex@example.test', role: 'MEMBER' }

const profile = {
  id: 'p1',
  familyId: 'f1',
  fullName: 'Child Synthetic',
  dateOfBirth: null,
  dependent: true,
  linkedToAccount: false,
  accessLevel: 'MANAGE',
  photoUpdatedAt: '2026-11-10T10:00:00Z',
  version: 0,
}

function renderHome() {
  return render(
    <ToastProvider>
      <Dashboard />
    </ToastProvider>,
  )
}

async function openManage() {
  renderHome()
  fireEvent.click(await screen.findByRole('button', { name: 'Manage family' }))
  return screen.findByRole('dialog', { name: 'Manage Synthetic Family' })
}

describe('Manage family', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('isOnlyOfferedToManagers', async () => {
    stubApi({
      'GET /families?size=100': page([{ ...family, role: 'MEMBER' }]),
      'GET /families/f1': { status: 200, body: { ...family, role: 'MEMBER', members: [sam, alex] } },
    })
    render(<Dashboard />)
    expect(await screen.findByRole('heading', { name: 'Synthetic Family' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Manage family' })).not.toBeInTheDocument()
  })

  it('renamesWithTheCurrentVersionAndShowsANameClash', async () => {
    const stub = stubApi({
      'GET /families?size=100': page([family]),
      'GET /families/f1': { status: 200, body: { ...family, members: [sam] } },
      'PUT /families/f1': [
        problem(409, 'FAMILY_NAME_TAKEN', 'You already belong to a family with this name'),
        { status: 200, body: { ...family, name: 'Renamed Family', version: 4 } },
      ],
    })
    const dialog = await openManage()

    fireEvent.change(within(dialog).getByLabelText('Family name'), { target: { value: 'Other Family' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save name' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('You already belong to a family with this name')
    expect(requestBody(stub, 'PUT', '/families/f1')).toEqual({ name: 'Other Family', version: 3 })

    fireEvent.change(within(dialog).getByLabelText('Family name'), { target: { value: 'Renamed Family' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save name' }))
    expect(await screen.findByText('Family renamed')).toBeInTheDocument()
  })

  it('deletesOnlyAfterTheNameIsTyped', async () => {
    const stub = stubApi({
      'GET /families?size=100': [page([family]), page([])],
      'GET /profiles?size=100': [page([profile]), page([])],
      'GET /families/f1': { status: 200, body: { ...family, members: [sam] } },
      'DELETE /families/f1': { status: 204 },
    })
    const dialog = await openManage()

    expect(within(dialog).getByText(/its 1 profile, with every record/)).toBeInTheDocument()
    const remove = within(dialog).getByRole('button', { name: 'Delete family' })
    expect(remove).toBeDisabled()
    fireEvent.change(within(dialog).getByLabelText(/Type the family name/), { target: { value: 'Synthetic' } })
    expect(remove).toBeDisabled()
    fireEvent.change(within(dialog).getByLabelText(/Type the family name/), { target: { value: 'Synthetic Family' } })
    fireEvent.click(remove)

    expect(await screen.findByText('Synthetic Family deleted')).toBeInTheDocument()
    expect(stub.mock.calls.some(([url, init]) => url === '/api/v1/families/f1' && init?.method === 'DELETE')).toBe(true)
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('explainsWhyAFamilyWithOtherMembersCantBeDeleted', async () => {
    stubApi({
      'GET /families?size=100': page([family]),
      'GET /families/f1': { status: 200, body: { ...family, members: [sam, alex] } },
    })
    const dialog = await openManage()

    expect(within(dialog).getByText(/only when you are its only member. It has 2 members/)).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Delete family' })).not.toBeInTheDocument()
  })

  it('uploadsAFamilyPhotoAndChecksTheFileFirst', async () => {
    const stub = stubApi({
      'GET /families?size=100': page([family]),
      'GET /families/f1': { status: 200, body: { ...family, members: [sam] } },
      'PUT /families/f1/photo': { status: 200, body: { ...family, photoUpdatedAt: '2026-11-10T12:00:00Z', version: 4 } },
    })
    const dialog = await openManage()
    const input = within(dialog).getByLabelText('Add a photo')

    fireEvent.change(input, { target: { files: [new File(['gif'], 'family.gif', { type: 'image/gif' })] } })
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Choose a JPEG or PNG photo.')
    expect(stub.mock.calls.some(([url]) => String(url).endsWith('/photo'))).toBe(false)

    fireEvent.change(input, { target: { files: [new File(['png'], 'family.png', { type: 'image/png' })] } })
    expect(await within(dialog).findByRole('button', { name: 'Remove photo' })).toBeInTheDocument()
    const upload = stub.mock.calls.find(([url, init]) => url === '/api/v1/families/f1/photo' && init?.method === 'PUT')
    expect(upload![1]!.body).toBeInstanceOf(FormData)
  })

  it('showsProfilePhotosOnHome', async () => {
    stubApi({
      'GET /families?size=100': page([family]),
      'GET /profiles?size=100': page([profile]),
      'GET /families/f1': { status: 200, body: { ...family, members: [sam] } },
    })
    const { container } = renderHome()
    await screen.findByRole('link', { name: 'Child Synthetic' })
    expect(container.querySelector('img')?.getAttribute('src')).toBe(
      '/api/v1/profiles/p1/photo?v=2026-11-10T10%3A00%3A00Z',
    )
  })
})
