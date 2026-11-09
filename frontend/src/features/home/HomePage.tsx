import type { Account } from '../../api/auth'
import { useDocumentTitle } from '../../components/useDocumentTitle'
import { Dashboard } from './Dashboard'

/** Home: a greeting, then the family overview. */
export function HomePage({ account }: { account: Account }) {
  useDocumentTitle('Home')
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
