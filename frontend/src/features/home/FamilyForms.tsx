import { Check, Copy } from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { errorMessage } from '../../api/client'
import {
  acceptInvitation,
  createFamily,
  createInvitation,
  previewInvitation,
  type FamilyRole,
  type InvitationCreated,
  type InvitationPreview,
  type KeepAccess,
} from '../../api/families'
import { createProfile, type Profile, type ProfileKind } from '../../api/profiles'
import { Button } from '../../components/Button'
import { ErrorNote } from '../../components/Feedback'
import { inputClass } from '../../components/formStyles'

const labelClass = 'block text-sm font-medium text-slate-800'

function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div>
      <label className={labelClass}>
        {label}
        {children}
      </label>
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </div>
  )
}

/** Runs a form action with a busy state and a readable error. */
function useAction() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function run(action: () => Promise<void>) {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  return { busy, error, run }
}

export function CreateFamilyForm({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState('')
  const { busy, error, run } = useAction()

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    void run(async () => {
      await createFamily(name)
      setName('')
      onCreated()
    })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <Field label="Family name" hint="For example, “The Demo family”. Members see this name.">
        <input
          className={inputClass}
          value={name}
          required
          maxLength={100}
          onChange={(event) => setName(event.target.value)}
        />
      </Field>
      <ErrorNote message={error} />
      <Button type="submit" variant="primary" size="md" busy={busy}>
        Create family
      </Button>
    </form>
  )
}

/**
 * Accepting asks the adult first when the invitation links a profile, because they decide how
 * much access others keep (DECISIONS.md, D-013).
 */
export function JoinFamilyForm({ onJoined }: { onJoined: () => void }) {
  const [code, setCode] = useState('')
  const [preview, setPreview] = useState<InvitationPreview | null>(null)
  const [keepAccess, setKeepAccess] = useState<KeepAccess>('NONE')
  const { busy, error, run } = useAction()

  function handleCheck(event: FormEvent) {
    event.preventDefault()
    void run(async () => setPreview(await previewInvitation(code.trim())))
  }

  function handleAccept() {
    void run(async () => {
      await acceptInvitation(code.trim(), preview?.profileName ? keepAccess : undefined)
      setCode('')
      setPreview(null)
      onJoined()
    })
  }

  if (preview) {
    return (
      <div className="space-y-4">
        <p className="text-sm text-slate-700">
          You are invited to join <strong>{preview.familyName}</strong> as a{' '}
          {preview.role === 'MANAGER' ? 'manager' : 'member'}.
        </p>
        {preview.profileName && (
          <Field
            label={`The profile “${preview.profileName}” will become yours. How much access should others keep?`}
          >
            <select
              className={inputClass}
              value={keepAccess}
              onChange={(event) => setKeepAccess(event.target.value as KeepAccess)}
            >
              <option value="NONE">No access</option>
              <option value="VIEW">View only</option>
              <option value="EDIT">View and edit</option>
            </select>
          </Field>
        )}
        <ErrorNote message={error} />
        <div className="flex flex-wrap gap-2">
          <Button variant="primary" size="md" busy={busy} onClick={handleAccept}>
            Accept invitation
          </Button>
          <Button size="md" onClick={() => setPreview(null)}>
            Cancel
          </Button>
        </div>
      </div>
    )
  }

  return (
    <form onSubmit={handleCheck} className="space-y-4">
      <Field label="Invitation code" hint="The code a family manager gave you.">
        <input
          className={inputClass}
          value={code}
          required
          autoComplete="off"
          onChange={(event) => setCode(event.target.value)}
        />
      </Field>
      <ErrorNote message={error} />
      <Button type="submit" variant="primary" size="md" busy={busy}>
        Check invitation
      </Button>
    </form>
  )
}

export function AddProfileForm({
  familyId,
  isManager,
  onCreated,
}: {
  familyId: string
  isManager: boolean
  onCreated: (profile: Profile) => void
}) {
  const [fullName, setFullName] = useState('')
  const [dateOfBirth, setDateOfBirth] = useState('')
  const [kind, setKind] = useState<ProfileKind>('SELF')
  const { busy, error, run } = useAction()

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    void run(async () => {
      onCreated(await createProfile(familyId, { fullName, dateOfBirth: dateOfBirth || null, kind }))
    })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <Field label="Full name">
        <input
          className={inputClass}
          value={fullName}
          required
          maxLength={200}
          onChange={(event) => setFullName(event.target.value)}
        />
      </Field>
      <Field label="Date of birth (optional)">
        <input
          type="date"
          className={inputClass}
          value={dateOfBirth}
          onChange={(event) => setDateOfBirth(event.target.value)}
        />
      </Field>
      <Field
        label="Who is this for?"
        hint={isManager ? undefined : 'Only family managers can add profiles for other people.'}
      >
        <select
          className={inputClass}
          value={kind}
          onChange={(event) => setKind(event.target.value as ProfileKind)}
        >
          <option value="SELF">Me</option>
          {isManager && <option value="DEPENDENT">A dependent</option>}
          {isManager && <option value="ADULT">Another adult</option>}
        </select>
      </Field>
      <ErrorNote message={error} />
      <Button type="submit" variant="primary" size="md" busy={busy}>
        Add profile
      </Button>
    </form>
  )
}

function InvitationCode({ created }: { created: InvitationCreated }) {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(created.token)
      setCopied(true)
    } catch {
      // Without clipboard access the code is still on screen to copy by hand.
    }
  }

  return (
    <div role="status" className="space-y-3 rounded-xl border border-brand-200 bg-brand-50 p-4 text-sm">
      <p className="text-brand-900">
        Send this code to <strong>{created.email}</strong>. It is shown only once and expires in 7
        days. It works only for that email.
      </p>
      <div className="flex flex-wrap items-center gap-2">
        <code className="min-w-0 flex-1 rounded-lg border border-brand-200 bg-white px-3 py-2 font-mono text-sm break-all text-slate-900">
          {created.token}
        </code>
        <Button icon={copied ? Check : Copy} onClick={copy}>
          {copied ? 'Copied' : 'Copy'}
        </Button>
      </div>
    </div>
  )
}

export function InviteForm({ familyId, offerable }: { familyId: string; offerable: Profile[] }) {
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<FamilyRole>('MEMBER')
  const [profileId, setProfileId] = useState('')
  const [created, setCreated] = useState<InvitationCreated | null>(null)
  const { busy, error, run } = useAction()

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setCreated(null)
    void run(async () => {
      setCreated(await createInvitation(familyId, { email, role, profileId: profileId || undefined }))
      setEmail('')
      setProfileId('')
    })
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <Field label="Their email">
        <input
          type="email"
          className={inputClass}
          value={email}
          required
          onChange={(event) => setEmail(event.target.value)}
        />
      </Field>
      <Field
        label="Family role"
        hint="Managers can add people and invite others. Access to each profile is set separately."
      >
        <select
          className={inputClass}
          value={role}
          onChange={(event) => setRole(event.target.value as FamilyRole)}
        >
          <option value="MEMBER">Member</option>
          <option value="MANAGER">Manager</option>
        </select>
      </Field>
      {offerable.length > 0 && (
        <Field
          label="Give them their profile (optional)"
          hint="For an adult whose profile you set up. It becomes theirs when they join."
        >
          <select
            className={inputClass}
            value={profileId}
            onChange={(event) => setProfileId(event.target.value)}
          >
            <option value="">None</option>
            {offerable.map((profile) => (
              <option key={profile.id} value={profile.id}>
                {profile.fullName}
              </option>
            ))}
          </select>
        </Field>
      )}
      <ErrorNote message={error} />
      <Button type="submit" variant="primary" size="md" busy={busy}>
        Create invitation
      </Button>
      {created && <InvitationCode created={created} />}
    </form>
  )
}
