import { SearchX } from 'lucide-react'
import { buttonClass } from '../../components/Button'
import { EmptyState } from '../../components/Feedback'
import { Link } from '../../router'

export function NotFoundPage() {
  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold text-slate-900">Page not found</h1>
      <EmptyState
        icon={SearchX}
        title="There's nothing at this address."
        action={
          <Link to="/" className={buttonClass('primary', 'md')}>
            Go to Home
          </Link>
        }
      >
        The link may be old or mistyped.
      </EmptyState>
    </div>
  )
}
