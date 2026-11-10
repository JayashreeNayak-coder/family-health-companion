import { HeartPulse } from 'lucide-react'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { CARD_GROUPS, readPublicEmergencyCard, type PublicCard } from '../../api/emergencyCard'
import { Loading } from '../../components/Feedback'
import { useDocumentTitle } from '../../components/useDocumentTitle'

/** The token is after "#", so it never reaches a server log when this page loads. */
function tokenFromAddress(): string {
  return window.location.hash.slice(1)
}

type State =
  | { status: 'loading' }
  | { status: 'ready'; card: PublicCard }
  | { status: 'failed'; message: string }

function failureMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 404) {
    return "This emergency card link doesn't work. It may have been turned off or replaced with a new link."
  }
  if (error instanceof ApiError && error.status === 429) {
    return 'This card has been opened too many times from this network. Please wait a minute and try again.'
  }
  return "The card couldn't be loaded. Check the connection and try again."
}

/**
 * What anyone with the link sees, signed in or not: the lines the owner approved, nothing else,
 * and no route into the app. Search engines are asked to skip it (docs/06 section 6).
 */
export function PublicEmergencyCardPage() {
  const [token] = useState(tokenFromAddress)
  const [state, setState] = useState<State>(() =>
    token ? { status: 'loading' } : { status: 'failed', message: failureMessage(new ApiError(404, '', '')) },
  )
  useDocumentTitle('Emergency information')

  useEffect(() => {
    const robots = document.createElement('meta')
    robots.name = 'robots'
    robots.content = 'noindex, nofollow, noarchive'
    document.head.appendChild(robots)
    return () => robots.remove()
  }, [])

  useEffect(() => {
    if (!token) {
      return
    }
    let ignore = false
    readPublicEmergencyCard(token).then(
      (card) => !ignore && setState({ status: 'ready', card }),
      (error) => !ignore && setState({ status: 'failed', message: failureMessage(error) }),
    )
    return () => {
      ignore = true
    }
  }, [token])

  return (
    <div className="min-h-screen bg-slate-50 px-4 py-8">
      <main className="mx-auto w-full max-w-lg">
        <div className="flex items-center gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-red-700 text-white">
            <HeartPulse aria-hidden="true" className="size-6" />
          </span>
          <h1 className="text-2xl font-bold text-slate-900">Emergency information</h1>
        </div>

        <div className="mt-6 rounded-xl border border-slate-200 bg-white p-5 sm:p-6">
          {state.status === 'loading' && <Loading lines={4} />}
          {state.status === 'failed' && (
            <p role="alert" className="text-slate-800">
              {state.message}
            </p>
          )}
          {state.status === 'ready' && <CardLines card={state.card} />}
        </div>

        <p className="mt-6 text-sm text-slate-600">
          Shared by this person or their family. They entered this information themselves, so it may be incomplete
          or out of date. It is not medical advice: follow the instructions of emergency services.
        </p>
        <p className="mt-2 text-xs text-slate-500">Family Health Companion prototype. Synthetic data only.</p>
      </main>
    </div>
  )
}

function CardLines({ card }: { card: PublicCard }) {
  const groups = CARD_GROUPS.map((group) => ({
    ...group,
    lines: card.items.filter((item) => item.type === group.type).map((item) => item.text),
  })).filter((group) => group.lines.length > 0)

  if (groups.length === 0) {
    return <p className="text-slate-700">This card has no information on it yet.</p>
  }

  return (
    <div className="space-y-5">
      {groups.map((group) =>
        group.type === 'PERSON' ? (
          <p key={group.type} className="text-xl font-semibold break-words text-slate-900">
            <span className="sr-only">Name: </span>
            {group.lines[0]}
          </p>
        ) : (
          <section key={group.type}>
            <h2 className="text-sm font-semibold tracking-wide text-slate-600 uppercase">{group.label}</h2>
            <ul className="mt-2 space-y-1.5">
              {group.lines.map((line, index) => (
                <li key={index} className="break-words text-slate-900">
                  {line}
                </li>
              ))}
            </ul>
          </section>
        ),
      )}
    </div>
  )
}
