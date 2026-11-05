import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from './App'

const account = { id: 'a1', email: 'sam@example.test', displayName: 'Sam Synthetic' }

type Reply = { status: number; body?: unknown } | Error

function problem(status: number, code: string, title: string, errors?: unknown[]): Reply {
  return { status, body: { type: 'about:blank', status, code, title, errors } }
}

const signedOut = problem(401, 'UNAUTHENTICATED', 'Sign in to continue')

/**
 * Stubs fetch by "METHOD path". Each route answers with its replies in order, and repeats the
 * last one. The CSRF route is always answered, like the real API.
 */
function stubApi(routes: Record<string, Reply | Reply[]>) {
  const remaining = new Map(
    Object.entries(routes).map(([route, replies]) => [route, [replies].flat()]),
  )
  const stub = vi.fn(async (url: string, init?: RequestInit) => {
    const route = `${init?.method ?? 'GET'} ${url.replace('/api/v1', '')}`
    if (route === 'GET /auth/csrf') {
      return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf-1' }))
    }
    const replies = remaining.get(route)
    if (!replies) {
      throw new Error(`Unexpected request: ${route}`)
    }
    const reply = replies.length > 1 ? replies.shift()! : replies[0]
    if (reply instanceof Error) {
      throw reply
    }
    return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
      status: reply.status,
    })
  })
  vi.stubGlobal('fetch', stub)
  return stub
}

function requestBody(stub: ReturnType<typeof stubApi>, path: string) {
  const call = stub.mock.calls.find(([url]) => url === `/api/v1${path}`)
  return { body: JSON.parse(call![1]!.body as string), headers: call![1]!.headers }
}

function type(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } })
}

function submit(name: string) {
  fireEvent.click(screen.getByRole('button', { name }))
}

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('showsLoadingBeforeTheSessionIsKnown', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})))

    render(<App />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading')
  })

  it('showsTheSignInFormWhenSignedOut', async () => {
    stubApi({ 'GET /auth/me': signedOut })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByText(/use synthetic data only/i)).toBeInTheDocument()
  })

  it('showsTheAccountWhenAlreadySignedIn', async () => {
    stubApi({ 'GET /auth/me': { status: 200, body: account } })

    render(<App />)

    expect(await screen.findByText('Welcome, Sam Synthetic')).toBeInTheDocument()
    expect(screen.getByText('Signed in as sam@example.test')).toBeInTheDocument()
  })

  it('showsAnErrorAndRecoversWhenTheServiceIsUnavailable', async () => {
    stubApi({ 'GET /auth/me': [new TypeError('Failed to fetch'), signedOut] })

    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('service is unavailable')

    submit('Try again')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('signsInWithTheCsrfTokenAndShowsTheAccount', async () => {
    const stub = stubApi({
      'GET /auth/me': signedOut,
      'POST /auth/login': { status: 200, body: account },
    })
    render(<App />)
    await screen.findByRole('heading', { name: 'Sign in' })

    type('Email', 'sam@example.test')
    type('Password', 'synthetic-password-1')
    submit('Sign in')

    expect(await screen.findByText('Welcome, Sam Synthetic')).toBeInTheDocument()
    const { body, headers } = requestBody(stub, '/auth/login')
    expect(body).toEqual({ email: 'sam@example.test', password: 'synthetic-password-1' })
    expect(headers).toMatchObject({ 'X-CSRF-TOKEN': 'csrf-1' })
  })

  it('showsTheGenericMessageAndClearsThePasswordWhenLoginFails', async () => {
    stubApi({
      'GET /auth/me': signedOut,
      'POST /auth/login': problem(401, 'INVALID_CREDENTIALS', 'Incorrect email or password'),
    })
    render(<App />)
    await screen.findByRole('heading', { name: 'Sign in' })

    type('Email', 'sam@example.test')
    type('Password', 'wrong-password-value')
    submit('Sign in')

    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password')
    expect(screen.getByLabelText('Password')).toHaveValue('')
  })

  it('showsTheRateLimitMessage', async () => {
    stubApi({
      'GET /auth/me': signedOut,
      'POST /auth/login': problem(429, 'RATE_LIMITED', 'Too many attempts. Try again later.'),
    })
    render(<App />)
    await screen.findByRole('heading', { name: 'Sign in' })

    type('Email', 'sam@example.test')
    type('Password', 'synthetic-password-1')
    submit('Sign in')

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts')
  })

  it('registersThenSignsIn', async () => {
    const stub = stubApi({
      'GET /auth/me': signedOut,
      'POST /auth/register': { status: 201, body: account },
      'POST /auth/login': { status: 200, body: account },
    })
    render(<App />)
    await screen.findByRole('heading', { name: 'Sign in' })

    submit('New here? Create an account')
    type('Your name', 'Sam Synthetic')
    type('Email', 'sam@example.test')
    type('Password', 'synthetic-password-1')
    submit('Create account')

    expect(await screen.findByText('Welcome, Sam Synthetic')).toBeInTheDocument()
    expect(requestBody(stub, '/auth/register').body).toEqual({
      email: 'sam@example.test',
      password: 'synthetic-password-1',
      displayName: 'Sam Synthetic',
    })
  })

  it('showsValidationErrorsBesideTheirFields', async () => {
    stubApi({
      'GET /auth/me': signedOut,
      'POST /auth/register': problem(400, 'VALIDATION_FAILED', 'Validation failed', [
        { field: 'password', message: 'must be 12 to 72 characters' },
      ]),
    })
    render(<App />)
    await screen.findByRole('heading', { name: 'Sign in' })

    submit('New here? Create an account')
    type('Your name', 'Sam Synthetic')
    type('Email', 'sam@example.test')
    type('Password', 'short')
    submit('Create account')

    expect(await screen.findByText('must be 12 to 72 characters')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('showsAnErrorWhenTheEmailIsAlreadyRegistered', async () => {
    stubApi({
      'GET /auth/me': signedOut,
      'POST /auth/register': problem(409, 'EMAIL_ALREADY_REGISTERED', 'This email is already registered'),
    })
    render(<App />)
    await screen.findByRole('heading', { name: 'Sign in' })

    submit('New here? Create an account')
    type('Your name', 'Sam Synthetic')
    type('Email', 'sam@example.test')
    type('Password', 'synthetic-password-1')
    submit('Create account')

    expect(await screen.findByRole('alert')).toHaveTextContent('already registered')
  })

  it('signsOutAndReturnsToTheSignInForm', async () => {
    const stub = stubApi({
      'GET /auth/me': { status: 200, body: account },
      'POST /auth/logout': { status: 204 },
    })
    render(<App />)
    await screen.findByText('Welcome, Sam Synthetic')

    submit('Sign out')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    await waitFor(() => expect(requestBody(stub, '/auth/logout').headers).toBeDefined())
  })

  it('staysSignedInAndShowsAnErrorWhenSignOutFails', async () => {
    stubApi({
      'GET /auth/me': { status: 200, body: account },
      'POST /auth/logout': problem(500, 'INTERNAL_ERROR', 'Something went wrong'),
    })
    render(<App />)
    await screen.findByText('Welcome, Sam Synthetic')

    submit('Sign out')

    expect(await screen.findByRole('alert')).toHaveTextContent('could not be signed out')
    expect(screen.getByText('Welcome, Sam Synthetic')).toBeInTheDocument()
  })
})
