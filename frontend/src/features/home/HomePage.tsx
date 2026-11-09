import type { Account } from '../../api/auth'
import { Dashboard } from './Dashboard'

/** Home. UI-3 replaces the dashboard below with the family overview. */
export function HomePage({ account }: { account: Account }) {
  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900">Welcome, {account.displayName}</h1>
        <p className="mt-1 text-sm text-slate-600">Signed in as {account.email}</p>
      </header>
      <Dashboard />
    </div>
  )
}
