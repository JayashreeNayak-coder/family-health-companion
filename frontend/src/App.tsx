import { useEffect, useState } from 'react'
import { fetchCurrentAccount, type Account } from './api/auth'
import { AuthPage } from './features/auth/AuthPage'
import { SignedInPage } from './features/auth/SignedInPage'

type Session =
  | { state: 'loading' }
  | { state: 'unavailable' }
  | { state: 'signedOut' }
  | { state: 'signedIn'; account: Account }

export function App() {
  const [session, setSession] = useState<Session>({ state: 'loading' })
  const [attempt, setAttempt] = useState(0)

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

  function retry() {
    setSession({ state: 'loading' })
    setAttempt((current) => current + 1)
  }

  return (
    <div className="flex min-h-screen flex-col bg-slate-100 text-slate-900">
      <header className="bg-white px-6 py-4 shadow-sm">
        <h1 className="text-lg font-semibold text-teal-800">Family Health Companion</h1>
      </header>

      <main className="flex flex-1 items-start justify-center p-6">
        {session.state === 'loading' && <p role="status">Loading…</p>}
        {session.state === 'unavailable' && (
          <div role="alert" className="text-center">
            <p>The service is unavailable. Please try again later.</p>
            <button type="button" onClick={retry} className="mt-2 text-teal-800 underline">
              Try again
            </button>
          </div>
        )}
        {session.state === 'signedOut' && (
          <AuthPage onSignedIn={(account) => setSession({ state: 'signedIn', account })} />
        )}
        {session.state === 'signedIn' && (
          <SignedInPage
            account={session.account}
            onSignedOut={() => setSession({ state: 'signedOut' })}
          />
        )}
      </main>

      <footer className="px-6 py-3 text-center text-sm text-slate-600">
        Prototype. Use synthetic data only and do not enter real health information.
      </footer>
    </div>
  )
}
