import { FileCheck2, HeartPulse, ShieldCheck, Users } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { login, register, type Account } from '../../api/auth'
import { ApiError, type FieldError } from '../../api/client'
import { Button } from '../../components/Button'
import { ErrorNote } from '../../components/Feedback'
import { inputClass } from '../../components/ui'
import { Link } from '../../router'

type Mode = 'login' | 'register'

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 0 || error.status >= 500) {
      return 'The service is unavailable. Please try again later.'
    }
    return error.message
  }
  return 'Something went wrong. Please try again.'
}

const POINTS = [
  { icon: Users, text: "One place for your family's health records, visits, and medicines." },
  { icon: ShieldCheck, text: 'You decide who in the family can view, edit, or manage each profile.' },
  { icon: FileCheck2, text: 'AI reads documents into drafts. Nothing is saved until you approve it.' },
]

function BrandPanel() {
  return (
    <div className="relative hidden overflow-hidden bg-sidebar px-10 py-12 text-sidebar-text lg:flex lg:flex-col lg:justify-between xl:px-16">
      <div className="flex items-center gap-3 text-white">
        <span className="grid size-10 place-items-center rounded-xl bg-brand-600">
          <HeartPulse aria-hidden="true" className="size-6" />
        </span>
        <span className="text-lg font-semibold">Family Health Companion</span>
      </div>

      <div className="max-w-md">
        <p className="text-3xl leading-tight font-semibold text-white xl:text-4xl">
          Your family's health, organised and in your hands.
        </p>
        <ul className="mt-8 space-y-5">
          {POINTS.map(({ icon: Icon, text }) => (
            <li key={text} className="flex gap-3">
              <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-white/10 text-brand-200">
                <Icon aria-hidden="true" className="size-4.5" />
              </span>
              <span className="pt-1 text-sm leading-relaxed">{text}</span>
            </li>
          ))}
        </ul>
      </div>

      <p className="text-xs text-sidebar-muted">
        Not a doctor and not for emergencies. It keeps records; it does not give medical advice.
      </p>
    </div>
  )
}

export function AuthPage({ mode, onSignedIn }: { mode: Mode; onSignedIn: (account: Account) => void }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>([])

  const isRegister = mode === 'register'

  // Switching between sign-in and registration starts the form afresh, except the email.
  const [shownMode, setShownMode] = useState(mode)
  if (shownMode !== mode) {
    setShownMode(mode)
    setPassword('')
    setError(null)
    setFieldErrors([])
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors([])
    try {
      if (isRegister) {
        await register({ email, password, displayName })
      }
      onSignedIn(await login({ email, password }))
    } catch (failure) {
      setPassword('')
      if (failure instanceof ApiError && failure.fieldErrors.length > 0) {
        setFieldErrors(failure.fieldErrors)
      } else {
        setError(messageFor(failure))
      }
      setSubmitting(false)
    }
  }

  function fieldError(field: string) {
    const message = fieldErrors.find((item) => item.field === field)?.message
    return message && <p className="mt-1 text-sm text-red-700">{message}</p>
  }

  const labelClass = 'block text-sm font-medium text-slate-800'

  return (
    <div className="grid min-h-screen lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <BrandPanel />

      <div className="flex flex-col px-4 py-8 sm:px-8">
        <div className="flex items-center gap-2.5 font-semibold text-brand-900 lg:hidden">
          <span className="grid size-8 place-items-center rounded-lg bg-brand-600 text-white">
            <HeartPulse aria-hidden="true" className="size-5" />
          </span>
          Family Health Companion
        </div>

        <main className="flex flex-1 items-center justify-center py-10">
          <section className="w-full max-w-sm">
            <h1 className="text-2xl font-semibold text-slate-900">
              {isRegister ? 'Create an account' : 'Sign in'}
            </h1>
            <p className="mt-1 text-sm text-slate-600">
              {isRegister
                ? 'Set up an account, then create a family or join one with a code.'
                : 'Welcome back. Sign in to see your family’s profiles.'}
            </p>

            <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
              {isRegister && (
                <div>
                  <label htmlFor="displayName" className={labelClass}>
                    Your name
                  </label>
                  <input
                    id="displayName"
                    className={inputClass}
                    autoComplete="name"
                    required
                    maxLength={100}
                    value={displayName}
                    onChange={(event) => setDisplayName(event.target.value)}
                  />
                  {fieldError('displayName')}
                </div>
              )}

              <div>
                <label htmlFor="email" className={labelClass}>
                  Email
                </label>
                <input
                  id="email"
                  className={inputClass}
                  type="email"
                  autoComplete="email"
                  required
                  maxLength={254}
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
                {fieldError('email')}
              </div>

              <div>
                <label htmlFor="password" className={labelClass}>
                  Password
                </label>
                <input
                  id="password"
                  className={inputClass}
                  type="password"
                  autoComplete={isRegister ? 'new-password' : 'current-password'}
                  required
                  minLength={isRegister ? 12 : undefined}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
                {isRegister && <p className="mt-1 text-sm text-slate-600">At least 12 characters.</p>}
                {fieldError('password')}
              </div>

              <ErrorNote message={error} />

              <Button type="submit" variant="primary" size="md" busy={submitting} className="w-full">
                {submitting ? 'Please wait…' : isRegister ? 'Create account' : 'Sign in'}
              </Button>
            </form>

            <p className="mt-6 text-center text-sm text-slate-600">
              {isRegister ? 'Already have an account? ' : 'New here? '}
              <Link
                to={isRegister ? '/sign-in' : '/register'}
                className="font-medium text-brand-700 underline-offset-2 hover:underline"
              >
                {isRegister ? 'Sign in' : 'Create an account'}
              </Link>
            </p>
          </section>
        </main>

        <p className="text-center text-xs text-slate-500">
          Prototype. Use synthetic data only and do not enter real health information.
        </p>
      </div>
    </div>
  )
}
