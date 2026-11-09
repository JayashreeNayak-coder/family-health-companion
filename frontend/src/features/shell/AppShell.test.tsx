import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../App'
import { page, problem, stubApi } from '../../test/stubApi'
import { initials } from './initials'

const account = { id: 'a1', email: 'sam@example.test', displayName: 'Sam Synthetic' }
const signedIn = { status: 200, body: account }
const signedOut = problem(401, 'UNAUTHENTICATED', 'Sign in to continue')

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

function at(path: string) {
  window.history.replaceState(null, '', path)
}

describe('AppShell', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('listsProfilesInTheSidebarAndMarksTheOpenOne', async () => {
    stubApi({
      'GET /auth/me': signedIn,
      'GET /profiles?size=100': page([profile]),
      'GET /profiles/p1': { status: 200, body: profile },
    })
    at('/profiles/p1')

    render(<App />)

    const link = await within(await screen.findByRole('navigation', { name: 'Main' })).findByRole('link', { name: 'Child Synthetic' })
    expect(link).toHaveAttribute('aria-current', 'page')
    expect(within(screen.getByRole('navigation', { name: 'Main' })).getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current')
    expect(await screen.findByRole('heading', { level: 1, name: 'Child Synthetic' })).toBeInTheDocument()
  })

  it('opensTheTabInTheAddressAndChangesTheAddressWithTheTab', async () => {
    stubApi({
      'GET /auth/me': signedIn,
      'GET /profiles/p1': { status: 200, body: profile },
      'GET /profiles/p1/timeline?size=100': page([]),
    })
    at('/profiles/p1/medications')

    render(<App />)

    const tabs = await screen.findByRole('navigation', { name: 'Profile sections' })
    expect(within(tabs).getByRole('link', { name: 'Medications' })).toHaveAttribute('aria-current', 'page')

    fireEvent.click(within(tabs).getByRole('link', { name: 'Timeline' }))

    expect(window.location.pathname).toBe('/profiles/p1/timeline')
    expect(within(tabs).getByRole('link', { name: 'Timeline' })).toHaveAttribute('aria-current', 'page')
  })

  it('showsTheOverviewWhenANonManagerOpensTheAccessAddress', async () => {
    stubApi({ 'GET /auth/me': signedIn, 'GET /profiles/p1': { status: 200, body: profile } })
    at('/profiles/p1/access')

    render(<App />)

    const tabs = await screen.findByRole('navigation', { name: 'Profile sections' })
    expect(within(tabs).queryByRole('link', { name: 'Access' })).not.toBeInTheDocument()
    expect(within(tabs).getByRole('link', { name: 'Overview' })).toHaveAttribute('aria-current', 'page')
  })

  it('showsNotFoundForAnUnknownAddress', async () => {
    stubApi({ 'GET /auth/me': signedIn })
    at('/no-such-page')

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: 'Go to Home' }))
    expect(await screen.findByText('Welcome, Sam Synthetic')).toBeInTheDocument()
  })

  it('sendsASignedInVisitorFromSignInToHome', async () => {
    stubApi({ 'GET /auth/me': signedIn })
    at('/sign-in')

    render(<App />)

    expect(await screen.findByText('Welcome, Sam Synthetic')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/')
  })

  it('keepsTheRequestedAddressThroughSignIn', async () => {
    stubApi({
      'GET /auth/me': signedOut,
      'POST /auth/login': { status: 200, body: account },
      'GET /profiles/p1': { status: 200, body: profile },
    })
    at('/profiles/p1')

    render(<App />)
    await screen.findByRole('heading', { name: 'Sign in' })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'sam@example.test' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'synthetic-password-1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Child Synthetic' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/profiles/p1')
  })

  it('opensTheRegistrationFormAtItsOwnAddress', async () => {
    stubApi({ 'GET /auth/me': signedOut })
    at('/register')

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Create an account' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('link', { name: 'Sign in' }))
    expect(window.location.pathname).toBe('/sign-in')
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('signsOutToTheSignInAddress', async () => {
    stubApi({ 'GET /auth/me': signedIn, 'POST /auth/logout': { status: 204 } })

    render(<App />)
    await screen.findByText('Welcome, Sam Synthetic')
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/sign-in')
  })

  it('opensAndClosesThePhoneMenu', async () => {
    stubApi({
      'GET /auth/me': signedIn,
      'GET /profiles?size=100': page([profile]),
      'GET /profiles/p1': { status: 200, body: profile },
    })

    render(<App />)
    await screen.findByText('Welcome, Sam Synthetic')

    fireEvent.click(screen.getByRole('button', { name: 'Open menu' }))
    const menu = screen.getByRole('dialog', { name: 'Menu' })
    expect(within(menu).getByRole('button', { name: 'Close menu' })).toHaveFocus()

    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.queryByRole('dialog', { name: 'Menu' })).not.toBeInTheDocument()

    // Following a link from the menu closes it too.
    fireEvent.click(screen.getByRole('button', { name: 'Open menu' }))
    const reopened = screen.getByRole('dialog', { name: 'Menu' })
    const link = await within(reopened).findByRole('link', { name: 'Child Synthetic' })
    act(() => {
      fireEvent.click(link)
    })
    expect(window.location.pathname).toBe('/profiles/p1')
    expect(screen.queryByRole('dialog', { name: 'Menu' })).not.toBeInTheDocument()
  })

  it('makesInitialsFromAName', () => {
    expect(initials('Child Synthetic')).toBe('CS')
    expect(initials('Asha Kumari Demo')).toBe('AD')
    expect(initials('ravi')).toBe('RA')
    expect(initials('  ')).toBe('?')
  })
})
