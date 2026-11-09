import { act, fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Link, navigate, parseRoute, profilePath, useRoute } from './router'

function ShowRoute() {
  const route = useRoute()
  return (
    <>
      <p data-testid="route">{JSON.stringify(route)}</p>
      <Link to="/profiles/p1/labs">Labs</Link>
    </>
  )
}

describe('router', () => {
  it('parsesEveryPage', () => {
    expect(parseRoute('/')).toEqual({ name: 'home' })
    expect(parseRoute('/sign-in')).toEqual({ name: 'signIn' })
    expect(parseRoute('/register')).toEqual({ name: 'register' })
    expect(parseRoute('/profiles/p1')).toEqual({ name: 'profile', profileId: 'p1', section: 'details' })
    expect(parseRoute('/profiles/p1/timeline/')).toEqual({ name: 'profile', profileId: 'p1', section: 'timeline' })
  })

  it('treatsUnknownAddressesAsNotFound', () => {
    expect(parseRoute('/profiles')).toEqual({ name: 'notFound' })
    expect(parseRoute('/profiles/p1/secrets')).toEqual({ name: 'notFound' })
    expect(parseRoute('/profiles/p1/labs/extra')).toEqual({ name: 'notFound' })
    expect(parseRoute('/admin')).toEqual({ name: 'notFound' })
  })

  it('buildsProfileAddressesThatParseBack', () => {
    expect(profilePath('p1')).toBe('/profiles/p1')
    expect(profilePath('p1', 'labs')).toBe('/profiles/p1/labs')
    expect(parseRoute(profilePath('a/b', 'food'))).toEqual({ name: 'profile', profileId: 'a/b', section: 'food' })
  })

  it('followsLinksWithoutReloadingAndGoesBack', async () => {
    render(<ShowRoute />)

    fireEvent.click(screen.getByRole('link', { name: 'Labs' }))

    expect(window.location.pathname).toBe('/profiles/p1/labs')
    expect(screen.getByTestId('route')).toHaveTextContent('"section":"labs"')

    act(() => {
      window.history.back()
    })
    // jsdom fires popstate asynchronously.
    expect(await screen.findByText('{"name":"home"}')).toBeInTheDocument()
  })

  it('leavesModifiedClicksToTheBrowser', () => {
    render(<ShowRoute />)

    const event = new MouseEvent('click', { bubbles: true, cancelable: true, ctrlKey: true })
    screen.getByRole('link', { name: 'Labs' }).dispatchEvent(event)

    expect(window.location.pathname).toBe('/')
  })

  it('replacesTheAddressWhenAsked', () => {
    const before = window.history.length
    render(<ShowRoute />)

    act(() => navigate('/register', { replace: true }))

    expect(window.history.length).toBe(before)
    expect(screen.getByTestId('route')).toHaveTextContent('register')
  })
})
