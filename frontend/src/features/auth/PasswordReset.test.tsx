import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../App'
import { problem, requestBody, stubApi } from '../../test/stubApi'

const signedOut = problem(401, 'UNAUTHENTICATED', 'Sign in to continue')

function at(path: string) {
  window.history.replaceState(null, '', path)
}

describe('Password reset and Google sign-in', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('asksForAResetLinkFromTheSignInPage', async () => {
    const stub = stubApi({
      'GET /auth/me': signedOut,
      'POST /auth/password-reset/request': { status: 202 },
    })
    at('/sign-in')
    render(<App />)

    fireEvent.click(await screen.findByRole('link', { name: 'Forgot password?' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Forgot your password?' })).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: ' sam@example.test ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(await screen.findByRole('status')).toHaveTextContent('If an account uses sam@example.test')
    expect(requestBody(stub, 'POST', '/auth/password-reset/request')).toEqual({ email: 'sam@example.test' })
  })

  it('setsANewPasswordFromTheLinkAndReturnsToSignIn', async () => {
    const stub = stubApi({
      'GET /auth/me': signedOut,
      'POST /auth/password-reset/confirm': { status: 204 },
    })
    at('/reset-password#token=synthetic-token')
    render(<App />)

    const password = await screen.findByLabelText('New password')
    fireEvent.change(password, { target: { value: 'a-new-synthetic-password' } })
    fireEvent.change(screen.getByLabelText('New password again'), { target: { value: 'something else' } })
    fireEvent.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByText("The two passwords don't match.")).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('New password again'), { target: { value: 'a-new-synthetic-password' } })
    fireEvent.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your password has been changed')
    expect(requestBody(stub, 'POST', '/auth/password-reset/confirm')).toEqual({
      token: 'synthetic-token',
      newPassword: 'a-new-synthetic-password',
    })
    expect(window.location.hash).toBe('')
  })

  it('explainsALinkWithNoCode', async () => {
    stubApi({ 'GET /auth/me': signedOut })
    at('/reset-password')
    render(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('This link is missing its code')
  })

  it('offersGoogleOnlyWhenItIsSwitchedOnAndExplainsARefusal', async () => {
    stubApi({
      'GET /auth/me': signedOut,
      'GET /auth/providers': { status: 200, body: { google: true } },
    })
    at('/sign-in?google=email-not-verified')
    render(<App />)

    expect(await screen.findByRole('link', { name: 'Continue with Google' })).toHaveAttribute(
      'href',
      '/oauth2/authorization/google',
    )
    expect(screen.getByRole('alert')).toHaveTextContent("Your Google account's email address isn't verified")
  })

  it('hidesGoogleWhenItIsOff', async () => {
    stubApi({ 'GET /auth/me': signedOut })
    at('/sign-in')
    render(<App />)

    expect(await screen.findByRole('heading', { level: 1, name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Continue with Google' })).not.toBeInTheDocument()
  })
})
