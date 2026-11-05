import { useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import {
  createInvitation,
  getFamily,
  type Family,
  type FamilyRole,
  type FamilySummary,
  type InvitationCreated,
} from '../../api/families'
import { createProfile, type Profile, type ProfileKind } from '../../api/profiles'
import { Card, ErrorNote, inputClass, primaryButtonClass } from '../../components/ui'

type Props = {
  summary: FamilySummary
  profiles: Profile[]
  onChanged: () => void
}

export function FamilyCard({ summary, profiles, onChanged }: Props) {
  const [family, setFamily] = useState<Family | null>(null)
  const [error, setError] = useState<string | null>(null)
  const isManager = summary.role === 'MANAGER'

  useEffect(() => {
    let ignore = false
    getFamily(summary.id).then(
      (loaded) => {
        if (!ignore) {
          setFamily(loaded)
        }
      },
      (failure) => {
        if (!ignore) {
          setError(errorMessage(failure))
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [summary.id, profiles])

  const offerable = profiles.filter(
    (profile) =>
      profile.familyId === summary.id && !profile.linkedToAccount && profile.accessLevel === 'MANAGE',
  )

  return (
    <Card title={summary.name}>
      <p className="text-sm text-slate-600">You are a {summary.role.toLowerCase()} of this family.</p>
      <ErrorNote message={error} />
      {family && (
        <ul className="mt-2 text-sm">
          {family.members.map((member) => (
            <li key={member.accountId}>
              {member.displayName} ({member.email}) · {member.role.toLowerCase()}
            </li>
          ))}
        </ul>
      )}
      <div className="mt-4 grid gap-4 md:grid-cols-2">
        <AddProfileForm familyId={summary.id} isManager={isManager} onCreated={onChanged} />
        {isManager && <InviteForm familyId={summary.id} offerable={offerable} />}
      </div>
    </Card>
  )
}

function AddProfileForm({
  familyId,
  isManager,
  onCreated,
}: {
  familyId: string
  isManager: boolean
  onCreated: () => void
}) {
  const [fullName, setFullName] = useState('')
  const [dateOfBirth, setDateOfBirth] = useState('')
  const [kind, setKind] = useState<ProfileKind>('SELF')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await createProfile(familyId, { fullName, dateOfBirth: dateOfBirth || null, kind })
      setFullName('')
      setDateOfBirth('')
      onCreated()
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-2">
      <h4 className="font-medium">Add a profile</h4>
      <label className="block text-sm font-medium">
        Full name
        <input
          className={inputClass}
          value={fullName}
          required
          maxLength={200}
          onChange={(event) => setFullName(event.target.value)}
        />
      </label>
      <label className="block text-sm font-medium">
        Date of birth (optional)
        <input
          type="date"
          className={inputClass}
          value={dateOfBirth}
          onChange={(event) => setDateOfBirth(event.target.value)}
        />
      </label>
      <label className="block text-sm font-medium">
        Who is this for?
        <select
          className={inputClass}
          value={kind}
          onChange={(event) => setKind(event.target.value as ProfileKind)}
        >
          <option value="SELF">Me</option>
          {isManager && <option value="DEPENDENT">A dependent</option>}
          {isManager && <option value="ADULT">Another adult</option>}
        </select>
      </label>
      <ErrorNote message={error} />
      <button type="submit" disabled={busy} className={primaryButtonClass}>
        Add profile
      </button>
    </form>
  )
}

function InviteForm({ familyId, offerable }: { familyId: string; offerable: Profile[] }) {
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<FamilyRole>('MEMBER')
  const [profileId, setProfileId] = useState('')
  const [created, setCreated] = useState<InvitationCreated | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setCreated(null)
    try {
      setCreated(await createInvitation(familyId, { email, role, profileId: profileId || undefined }))
      setEmail('')
      setProfileId('')
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-2">
      <h4 className="font-medium">Invite someone</h4>
      <label className="block text-sm font-medium">
        Their email
        <input
          type="email"
          className={inputClass}
          value={email}
          required
          onChange={(event) => setEmail(event.target.value)}
        />
      </label>
      <label className="block text-sm font-medium">
        Family role
        <select
          className={inputClass}
          value={role}
          onChange={(event) => setRole(event.target.value as FamilyRole)}
        >
          <option value="MEMBER">Member</option>
          <option value="MANAGER">Manager</option>
        </select>
      </label>
      {offerable.length > 0 && (
        <label className="block text-sm font-medium">
          Give them their profile (optional)
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
        </label>
      )}
      <ErrorNote message={error} />
      <button type="submit" disabled={busy} className={primaryButtonClass}>
        Create invitation
      </button>
      {created && (
        <div role="status" className="rounded bg-teal-50 p-2 text-sm">
          <p>
            Send this code to {created.email}. It is shown only once and expires in 7 days. It works
            only for that email.
          </p>
          <code className="mt-1 block break-all">{created.token}</code>
        </div>
      )}
    </form>
  )
}
