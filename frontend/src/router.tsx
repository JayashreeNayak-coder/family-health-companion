/**
 * A small in-house router (DECISIONS.md, D-023): page addresses from the browser's History API,
 * with no router library. It knows only this app's few pages. nginx and the Vite dev server
 * serve index.html for any path, so every address here can be refreshed or bookmarked.
 */
import { useMemo, useSyncExternalStore, type AnchorHTMLAttributes, type MouseEvent } from 'react'

export const PROFILE_SECTIONS = [
  'overview',
  'details',
  'history',
  'visits',
  'medications',
  'labs',
  'food',
  'documents',
  'timeline',
  'access',
] as const

export type ProfileSection = (typeof PROFILE_SECTIONS)[number]

export type Route =
  | { name: 'home' }
  | { name: 'signIn' }
  | { name: 'register' }
  | { name: 'profile'; profileId: string; section: ProfileSection }
  | { name: 'notFound' }

function isSection(value: string): value is ProfileSection {
  return (PROFILE_SECTIONS as readonly string[]).includes(value)
}

export function parseRoute(pathname: string): Route {
  const parts = pathname.split('/').filter(Boolean).map(decodeURIComponent)
  if (parts.length === 0) {
    return { name: 'home' }
  }
  if (parts.length === 1 && parts[0] === 'sign-in') {
    return { name: 'signIn' }
  }
  if (parts.length === 1 && parts[0] === 'register') {
    return { name: 'register' }
  }
  if (parts[0] === 'profiles' && parts.length >= 2 && parts.length <= 3) {
    const section = parts[2] ?? 'overview'
    if (isSection(section)) {
      return { name: 'profile', profileId: parts[1], section }
    }
  }
  return { name: 'notFound' }
}

export function profilePath(profileId: string, section: ProfileSection = 'overview'): string {
  const base = `/profiles/${encodeURIComponent(profileId)}`
  return section === 'overview' ? base : `${base}/${section}`
}

const NAVIGATED = 'fhc:navigate'

export function navigate(path: string, options: { replace?: boolean } = {}) {
  if (path === window.location.pathname) {
    return
  }
  if (options.replace) {
    window.history.replaceState(null, '', path)
  } else {
    window.history.pushState(null, '', path)
    window.scrollTo?.(0, 0)
  }
  window.dispatchEvent(new Event(NAVIGATED))
}

function subscribe(onChange: () => void) {
  window.addEventListener('popstate', onChange)
  window.addEventListener(NAVIGATED, onChange)
  return () => {
    window.removeEventListener('popstate', onChange)
    window.removeEventListener(NAVIGATED, onChange)
  }
}

export function usePathname(): string {
  return useSyncExternalStore(subscribe, () => window.location.pathname)
}

export function useRoute(): Route {
  const pathname = usePathname()
  return useMemo(() => parseRoute(pathname), [pathname])
}

type LinkProps = AnchorHTMLAttributes<HTMLAnchorElement> & { to: string }

/**
 * A real link, so it can be opened in a new tab or copied. A plain click stays in the app
 * instead of reloading the page.
 */
export function Link({ to, onClick, ...rest }: LinkProps) {
  function handleClick(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event)
    const modified = event.metaKey || event.ctrlKey || event.shiftKey || event.altKey
    if (event.defaultPrevented || event.button !== 0 || modified || rest.target) {
      return
    }
    event.preventDefault()
    navigate(to)
  }
  return <a href={to} onClick={handleClick} {...rest} />
}
