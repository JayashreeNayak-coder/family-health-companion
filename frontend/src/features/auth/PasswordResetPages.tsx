import { MailCheck } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { confirmPasswordReset, requestPasswordReset } from '../../api/auth'
import { ApiError, errorMessage } from '../../api/client'
import { Button } from '../../components/Button'
import { ErrorNote } from '../../components/Feedback'
import { inputClass } from '../../components/formStyles'
import { PasswordInput } from '../../components/PasswordInput'
import { useDocumentTitle } from '../../components/useDocumentTitle'
import { Link, navigate } from '../../router'
import { AuthFrame } from './AuthPage'

const labelClass = 'block text-sm font-medium text-slate-800'
const linkClass = 'font-medium text-brand-700 underline-offset-2 hover:underline'

/** Asks for a reset link. The answer is the same whether or not the email has an account. */
export function ForgotPasswordPage() {
  useDocumentTitle('Forgot password')
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await requestPasswordReset(email.trim())
      setSent(true)
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthFrame>
      <h1 className="text-2xl font-semibold text-slate-900">Forgot your password?</h1>
      {sent ? (
        <div role="status" className="mt-6 rounded-lg border border-brand-200 bg-brand-50 p-4 text-sm text-brand-900">
          <MailCheck aria-hidden="true" className="mb-2 size-6" />
          <p>
            If an account uses <strong className="font-semibold break-all">{email.trim()}</strong>, we&rsquo;ve sent it a
            link to choose a new password. The link works once, for 30 minutes.
          </p>
          <p className="mt-2">Check your spam folder if it doesn&rsquo;t arrive in a few minutes.</p>
        </div>
      ) : (
        <>
          <p className="mt-1 text-sm text-slate-600">
            Enter your account&rsquo;s email and we&rsquo;ll send you a link to choose a new one. This also works for an
            account made with Google, to add a password.
          </p>
          <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
            <div>
              <label htmlFor="reset-email" className={labelClass}>
                Email
              </label>
              <input
                id="reset-email"
                className={inputClass}
                type="email"
                autoComplete="email"
                required
                maxLength={254}
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
            </div>
            <ErrorNote message={error} />
            <Button type="submit" variant="primary" size="md" busy={busy} className="w-full">
              Send reset link
            </Button>
          </form>
        </>
      )}
      <p className="mt-6 text-center text-sm text-slate-600">
        <Link to="/sign-in" className={linkClass}>
          Back to sign in
        </Link>
      </p>
    </AuthFrame>
  )
}

/** The token arrives after "#" in the emailed link, so it never reaches a server log. */
function tokenFromAddress(): string {
  return new URLSearchParams(window.location.hash.slice(1)).get('token') ?? ''
}

/** Chooses a new password from an emailed link, then sends the user to sign in. */
export function ResetPasswordPage({ onDone }: { onDone: (message: string) => void }) {
  useDocumentTitle('Choose a new password')
  const [token] = useState(tokenFromAddress)
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldError, setFieldError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldError(null)
    if (password.length < 12) {
      setFieldError('Use at least 12 characters.')
      return
    }
    if (password !== repeat) {
      setFieldError("The two passwords don't match.")
      return
    }
    setBusy(true)
    try {
      await confirmPasswordReset(token, password)
      // Take the token out of the address before leaving the page.
      window.history.replaceState(null, '', window.location.pathname)
      onDone('Your password has been changed. Sign in with the new one.')
      navigate('/sign-in', { replace: true })
    } catch (failure) {
      if (failure instanceof ApiError && failure.fieldErrors.length > 0) {
        setFieldError(failure.fieldErrors[0].message)
      } else {
        setError(errorMessage(failure))
      }
      setBusy(false)
    }
  }

  return (
    <AuthFrame>
      <h1 className="text-2xl font-semibold text-slate-900">Choose a new password</h1>
      {!token ? (
        <>
          <ErrorNote message="This link is missing its code. Open the link from the email again, or ask for a new one." />
          <p className="mt-6 text-center text-sm">
            <Link to="/forgot-password" className={linkClass}>
              Ask for a new link
            </Link>
          </p>
        </>
      ) : (
        <>
          <p className="mt-1 text-sm text-slate-600">
            After this, you&rsquo;ll be signed out on every device and can sign in with the new password.
          </p>
          <form className="mt-8 space-y-5" onSubmit={handleSubmit} noValidate>
            <div>
              <label htmlFor="new-password" className={labelClass}>
                New password
              </label>
              <PasswordInput
                id="new-password"
                autoComplete="new-password"
                required
                minLength={12}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
              <p className="mt-1 text-sm text-slate-600">At least 12 characters.</p>
            </div>
            <div>
              <label htmlFor="repeat-password" className={labelClass}>
                New password again
              </label>
              <PasswordInput
                id="repeat-password"
                autoComplete="new-password"
                required
                value={repeat}
                onChange={(event) => setRepeat(event.target.value)}
              />
              {fieldError && <p className="mt-1 text-sm text-red-700">{fieldError}</p>}
            </div>
            <ErrorNote message={error} />
            <Button type="submit" variant="primary" size="md" busy={busy} className="w-full">
              Change password
            </Button>
          </form>
          <p className="mt-6 text-center text-sm">
            <Link to="/forgot-password" className={linkClass}>
              Ask for a new link
            </Link>
          </p>
        </>
      )}
    </AuthFrame>
  )
}
