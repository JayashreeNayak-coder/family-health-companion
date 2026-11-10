import { HeartPulse } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { fetchCurrentAccount, type Account } from './api/auth'
import { Button } from './components/Button'
import { Loading } from './components/Feedback'
import { ToastProvider } from './components/Toast'
import { AuthPage } from './features/auth/AuthPage'
import { ForgotPasswordPage, ResetPasswordPage } from './features/auth/PasswordResetPages'
import { HomePage } from './features/home/HomePage'
import { MealsPage } from './features/meals/MealsPage'
import { ProfilePage } from './features/profiles/ProfilePage'
import { AppShell } from './features/shell/AppShell'
import { NavProfilesProvider } from './features/shell/NavProfiles'
import { NotFoundPage } from './features/shell/NotFoundPage'
import { navigate, useRoute, type Route } from './router'

type Session =
  | { state: 'loading' }
  | { state: 'unavailable' }
  | { state: 'signedOut' }
  | { state: 'signedIn'; account: Account }

/** Pages for signed-out visitors. A signed-in visitor at one of them goes home. */
function isAuthRoute(route: Route) {
  return route.name === 'signIn' || route.name === 'register' || route.name === 'forgotPassword'
}

function Centered({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-screen place-items-center px-4">
      <div className="w-full max-w-sm text-center">
        <span className="mx-auto mb-6 grid size-12 place-items-center rounded-xl bg-brand-600 text-white">
          <HeartPulse aria-hidden="true" className="size-7" />
        </span>
        {children}
      </div>
    </div>
  )
}

export function App() {
  const route = useRoute()
  const [session, setSession] = useState<Session>({ state: 'loading' })
  const [attempt, setAttempt] = useState(0)
  // Shown on the sign-in page after a password reset.
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false

    fetchCurrentAccount().then(
      (account) => {
        if (!ignore) {
          setSession(account ? { state: 'signedIn', account } : { state: 'signedOut' })
        }
      },
      () => {
        if (!ignore) {
          setSession({ state: 'unavailable' })
        }
      },
    )

    return () => {
      ignore = true
    }
  }, [attempt])

  // A signed-in visitor at the sign-in address goes home. A signed-out visitor elsewhere sees
  // sign-in at the address they asked for, so signing in brings them back to it.
  const signedInAtAuth = session.state === 'signedIn' && isAuthRoute(route)
  useEffect(() => {
    if (signedInAtAuth) {
      navigate('/', { replace: true })
    }
  }, [signedInAtAuth])

  function retry() {
    setSession({ state: 'loading' })
    setAttempt((current) => current + 1)
  }

  let content: ReactNode
  if (session.state === 'loading') {
    content = (
      <Centered>
        <Loading lines={2} />
      </Centered>
    )
  } else if (session.state === 'unavailable') {
    content = (
      <Centered>
        <div role="alert">
          <p className="text-slate-700">The service is unavailable. Please try again later.</p>
          <Button variant="primary" size="md" className="mt-4" onClick={retry}>
            Try again
          </Button>
        </div>
      </Centered>
    )
  } else if (route.name === 'resetPassword') {
    // Works signed in or out: a reset signs the account out everywhere anyway.
    content = (
      <ResetPasswordPage
        onDone={(message) => {
          setNotice(message)
          setSession({ state: 'signedOut' })
        }}
      />
    )
  } else if (session.state === 'signedOut') {
    content =
      route.name === 'forgotPassword' ? (
        <ForgotPasswordPage />
      ) : (
        <AuthPage
          mode={route.name === 'register' ? 'register' : 'login'}
          notice={notice}
          onSignedIn={(account) => {
            setNotice(null)
            setSession({ state: 'signedIn', account })
          }}
        />
      )
  } else {
    const account = session.account
    content = (
      <NavProfilesProvider key={account.id}>
        <AppShell account={account} onSignedOut={() => setSession({ state: 'signedOut' })}>
          {(route.name === 'home' || isAuthRoute(route)) && <HomePage account={account} />}
          {route.name === 'meals' && <MealsPage />}
          {route.name === 'profile' && (
            <ProfilePage key={route.profileId} profileId={route.profileId} section={route.section} />
          )}
          {route.name === 'notFound' && <NotFoundPage />}
        </AppShell>
      </NavProfilesProvider>
    )
  }

  return <ToastProvider>{content}</ToastProvider>
}
