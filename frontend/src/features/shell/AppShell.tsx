import { HeartPulse, House, LogOut, Menu, X } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { logout, type Account } from '../../api/auth'
import { ApiError } from '../../api/client'
import { ErrorNote } from '../../components/Feedback'
import { Link, navigate, profilePath, usePathname, useRoute } from '../../router'
import { initials } from './initials'
import { useNavProfiles } from './NavProfiles'

type Props = {
  account: Account
  onSignedOut: () => void
  children: ReactNode
}

function Brand({ inverted = true }: { inverted?: boolean }) {
  return (
    <Link
      to="/"
      className={`flex items-center gap-2.5 rounded-lg font-semibold ${inverted ? 'text-white' : 'text-brand-900'}`}
    >
      <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-brand-600 text-white">
        <HeartPulse aria-hidden="true" className="size-5" />
      </span>
      <span className="leading-tight">Family Health Companion</span>
    </Link>
  )
}

const navItem =
  'flex min-h-10 items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-sidebar-text transition-colors hover:bg-white/5 hover:text-white'
const navItemActive = 'bg-sidebar-active text-white'

/** The sidebar's contents, shared by the desktop column and the phone drawer. */
function Navigation({
  account,
  signingOut,
  onSignOut,
  inDrawer = false,
}: {
  account: Account
  signingOut: boolean
  onSignOut: () => void
  inDrawer?: boolean
}) {
  const route = useRoute()
  const { profiles } = useNavProfiles()
  const activeProfile = route.name === 'profile' ? route.profileId : null

  return (
    <div className="flex h-full flex-col gap-6 px-3 py-5">
      <div className={inDrawer ? 'pr-12 pl-2' : 'px-2'}>
        <Brand />
      </div>

      <nav aria-label="Main" className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto">
        <ul className="space-y-1">
          <li>
            <Link
              to="/"
              aria-current={route.name === 'home' ? 'page' : undefined}
              className={`${navItem} ${route.name === 'home' ? navItemActive : ''}`}
            >
              <House aria-hidden="true" className="size-4.5" />
              Home
            </Link>
          </li>
        </ul>

        <div>
          <h2 className="px-3 text-xs font-semibold tracking-wider text-sidebar-muted uppercase">
            Profiles
          </h2>
          {profiles && profiles.length === 0 && (
            <p className="mt-2 px-3 text-sm text-sidebar-muted">None yet. Add one from Home.</p>
          )}
          <ul className="mt-2 space-y-1">
            {profiles?.map((profile) => {
              const active = profile.id === activeProfile
              return (
                <li key={profile.id}>
                  <Link
                    to={profilePath(profile.id)}
                    aria-current={active ? 'page' : undefined}
                    className={`${navItem} ${active ? navItemActive : ''}`}
                  >
                    <span
                      aria-hidden="true"
                      className="grid size-7 shrink-0 place-items-center rounded-full bg-brand-800 text-xs font-semibold text-brand-100"
                    >
                      {initials(profile.fullName)}
                    </span>
                    <span className="min-w-0 truncate">{profile.fullName}</span>
                  </Link>
                </li>
              )
            })}
          </ul>
        </div>
      </nav>

      <div className="border-t border-white/10 px-2 pt-4">
        <p className="truncate text-sm font-medium text-white">{account.displayName}</p>
        <p className="truncate text-xs text-sidebar-muted">{account.email}</p>
        <button
          type="button"
          onClick={onSignOut}
          disabled={signingOut}
          className={`${navItem} mt-3 w-full disabled:opacity-60`}
        >
          <LogOut aria-hidden="true" className="size-4.5" />
          {signingOut ? 'Signing out…' : 'Sign out'}
        </button>
      </div>
    </div>
  )
}

/**
 * The signed-in frame: a sidebar on wide screens, and a top bar with a slide-in menu on phones.
 * Pages render inside it and own their headings.
 */
export function AppShell({ account, onSignedOut, children }: Props) {
  const pathname = usePathname()
  const [menuOpen, setMenuOpen] = useState(false)
  const [signingOut, setSigningOut] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Following a link closes the phone menu.
  const [menuPath, setMenuPath] = useState(pathname)
  if (menuPath !== pathname) {
    setMenuPath(pathname)
    setMenuOpen(false)
  }

  useEffect(() => {
    if (!menuOpen) {
      return
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setMenuOpen(false)
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [menuOpen])

  async function handleSignOut() {
    setSigningOut(true)
    setError(null)
    try {
      await logout()
      onSignedOut()
      navigate('/sign-in')
    } catch (failure) {
      // The session has already ended, so the user is signed out.
      if (failure instanceof ApiError && failure.status === 401) {
        onSignedOut()
        navigate('/sign-in')
        return
      }
      setMenuOpen(false)
      setError('You could not be signed out. Please try again.')
      setSigningOut(false)
    }
  }

  const navigationProps = { account, signingOut, onSignOut: handleSignOut }

  return (
    <div className="min-h-screen lg:bg-[linear-gradient(to_right,var(--color-sidebar)_16rem,transparent_16rem)] lg:pl-64">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded-lg focus:bg-white focus:px-4 focus:py-2 focus:text-brand-800 focus:shadow"
      >
        Skip to content
      </a>

      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 bg-sidebar lg:block">
        <Navigation {...navigationProps} />
      </aside>

      <header className="sticky top-0 z-20 flex items-center gap-3 border-b border-slate-200 bg-white/95 px-4 py-3 backdrop-blur lg:hidden">
        <button
          type="button"
          onClick={() => setMenuOpen(true)}
          aria-label="Open menu"
          aria-expanded={menuOpen}
          className="grid size-10 place-items-center rounded-lg text-slate-700 hover:bg-slate-100"
        >
          <Menu aria-hidden="true" className="size-5" />
        </button>
        <Brand inverted={false} />
      </header>

      {menuOpen && (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          <div className="absolute inset-0 bg-slate-900/50" onClick={() => setMenuOpen(false)} />
          <div className="absolute inset-y-0 left-0 w-72 max-w-[85vw] bg-sidebar shadow-xl">
            <button
              type="button"
              onClick={() => setMenuOpen(false)}
              aria-label="Close menu"
              autoFocus
              className="absolute top-4 right-3 grid size-9 place-items-center rounded-lg text-sidebar-text hover:bg-white/10"
            >
              <X aria-hidden="true" className="size-5" />
            </button>
            <Navigation {...navigationProps} inDrawer />
          </div>
        </div>
      )}

      <main id="main" className="mx-auto w-full max-w-5xl px-4 py-6 sm:px-6 lg:px-10 lg:py-10">
        {error && (
          <div className="mb-4">
            <ErrorNote message={error} />
          </div>
        )}
        {children}
      </main>

      <footer className="mx-auto max-w-5xl px-4 pb-8 text-center text-xs text-slate-500 sm:px-6 lg:px-10">
        Prototype. Use synthetic data only and do not enter real health information.
      </footer>
    </div>
  )
}
