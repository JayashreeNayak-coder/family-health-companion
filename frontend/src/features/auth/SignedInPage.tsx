import { useState } from 'react'
import { logout, type Account } from '../../api/auth'
import { ApiError } from '../../api/client'
import { Dashboard } from '../home/Dashboard'

type Props = {
  account: Account
  onSignedOut: () => void
}

export function SignedInPage({ account, onSignedOut }: Props) {
  const [signingOut, setSigningOut] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleLogout() {
    setSigningOut(true)
    setError(null)
    try {
      await logout()
      onSignedOut()
    } catch (failure) {
      // The session has already ended, so the user is signed out.
      if (failure instanceof ApiError && failure.status === 401) {
        onSignedOut()
        return
      }
      setError('You could not be signed out. Please try again.')
      setSigningOut(false)
    }
  }

  return (
    <div className="mx-auto w-full max-w-4xl space-y-4">
      <section className="rounded-lg bg-white p-5 shadow">
        <h2 className="text-xl font-semibold">Welcome, {account.displayName}</h2>
        <p className="mt-1 text-sm text-slate-600">Signed in as {account.email}</p>

      {error && (
        <p role="alert" className="mt-4 rounded bg-red-50 p-2 text-sm text-red-800">
          {error}
        </p>
      )}

      <button
        type="button"
        onClick={handleLogout}
        disabled={signingOut}
        className="mt-4 rounded border border-slate-300 px-4 py-2 font-medium hover:bg-slate-50 disabled:opacity-60"
      >
        {signingOut ? 'Signing out…' : 'Sign out'}
      </button>
      </section>
      <Dashboard />
    </div>
  )
}
