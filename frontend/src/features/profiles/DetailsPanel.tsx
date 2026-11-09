import { useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import { deleteProfile, updateProfile, type Profile } from '../../api/profiles'
import { buttonClass } from '../../components/Button'
import { Card } from '../../components/Card'
import { ErrorNote, Missing } from '../../components/Feedback'
import { inputClass } from '../../components/formStyles'
import { useConfirm } from '../../components/Dialog'

type Props = {
  profile: Profile
  onSaved: (profile: Profile) => void
  onDeleted: () => void
}

export function DetailsPanel({ profile, onSaved, onDeleted }: Props) {
  const [fullName, setFullName] = useState(profile.fullName)
  const [dateOfBirth, setDateOfBirth] = useState(profile.dateOfBirth ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirm, confirmDialog] = useConfirm()
  const canEdit = profile.accessLevel !== 'VIEW'

  async function handleSave(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      onSaved(
        await updateProfile(profile.id, {
          fullName,
          dateOfBirth: dateOfBirth || null,
          version: profile.version,
        }),
      )
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete() {
    const confirmed = await confirm({
      title: `Delete ${profile.fullName}?`,
      message: `This permanently deletes ${profile.fullName} and all of their records and documents. It can't be undone.`,
      confirmLabel: 'Delete profile',
    })
    if (!confirmed) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await deleteProfile(profile.id)
      onDeleted()
    } catch (failure) {
      setError(errorMessage(failure))
      setBusy(false)
    }
  }

  if (!canEdit) {
    return (
      <Card>
        <p>Date of birth: {profile.dateOfBirth ?? <Missing />}</p>
        <p className="mt-2 text-sm text-slate-600">You have view-only access to this profile.</p>
      </Card>
    )
  }

  return (
    <Card>
      {confirmDialog}
      <form onSubmit={handleSave} className="space-y-3">
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
        <ErrorNote message={error} />
        <div className="flex items-center justify-between">
          <button type="submit" disabled={busy} className={buttonClass('primary', 'md')}>
            Save details
          </button>
          {profile.accessLevel === 'MANAGE' && (
            <button type="button" disabled={busy} onClick={handleDelete} className={buttonClass('danger', 'sm')}>
              Delete profile
            </button>
          )}
        </div>
      </form>
    </Card>
  )
}
