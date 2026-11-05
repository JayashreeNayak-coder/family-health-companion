import { useState, type FormEvent } from 'react'
import { login, register, type Account } from '../../api/auth'
import { ApiError, type FieldError } from '../../api/client'

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

const inputClass = 'mt-1 w-full rounded border border-slate-300 px-3 py-2'

export function AuthPage({ onSignedIn }: { onSignedIn: (account: Account) => void }) {
  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>([])

  const isRegister = mode === 'register'

  function switchMode() {
    setMode(isRegister ? 'login' : 'register')
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
    } finally {
      setSubmitting(false)
    }
  }

  function fieldError(field: string) {
    const message = fieldErrors.find((item) => item.field === field)?.message
    return message && <p className="mt-1 text-sm text-red-700">{message}</p>
  }

  return (
    <section className="mx-auto w-full max-w-sm rounded-lg bg-white p-6 shadow">
      <h2 className="text-xl font-semibold">{isRegister ? 'Create an account' : 'Sign in'}</h2>

      <form className="mt-4 space-y-4" onSubmit={handleSubmit}>
        {isRegister && (
          <div>
            <label htmlFor="displayName" className="block text-sm font-medium">
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
          <label htmlFor="email" className="block text-sm font-medium">
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
          <label htmlFor="password" className="block text-sm font-medium">
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

        {error && (
          <p role="alert" className="rounded bg-red-50 p-2 text-sm text-red-800">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded bg-teal-700 px-4 py-2 font-medium text-white hover:bg-teal-800 disabled:opacity-60"
        >
          {submitting ? 'Please wait…' : isRegister ? 'Create account' : 'Sign in'}
        </button>
      </form>

      <button type="button" onClick={switchMode} className="mt-4 text-sm text-teal-800 underline">
        {isRegister ? 'Already have an account? Sign in' : 'New here? Create an account'}
      </button>
    </section>
  )
}
