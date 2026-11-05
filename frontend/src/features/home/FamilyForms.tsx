import { useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import {
  acceptInvitation,
  createFamily,
  previewInvitation,
  type InvitationPreview,
  type KeepAccess,
} from '../../api/families'
import { ErrorNote, inputClass, primaryButtonClass, secondaryButtonClass } from '../../components/ui'

export function CreateFamilyForm({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await createFamily(name)
      setName('')
      onCreated()
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <label className="block text-sm font-medium">
        Family name
        <input
          className={inputClass}
          value={name}
          required
          maxLength={100}
          onChange={(event) => setName(event.target.value)}
        />
      </label>
      <ErrorNote message={error} />
      <button type="submit" disabled={busy} className={`mt-3 ${primaryButtonClass}`}>
        Create family
      </button>
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
      <div>
        <p className="text-sm">
          You are invited to join <strong>{preview.familyName}</strong>.
        </p>
        {preview.profileName && (
          <label className="mt-3 block text-sm font-medium">
            The profile “{preview.profileName}” will become yours. How much access should others
            keep?
            <select
              className={inputClass}
              value={keepAccess}
              onChange={(event) => setKeepAccess(event.target.value as KeepAccess)}
            >
              <option value="NONE">No access</option>
              <option value="VIEW">View only</option>
              <option value="EDIT">View and edit</option>
            </select>
          </label>
        )}
        <ErrorNote message={error} />
        <div className="mt-3 flex gap-2">
          <button type="button" disabled={busy} onClick={handleAccept} className={primaryButtonClass}>
            Accept invitation
          </button>
          <button
            type="button"
            onClick={() => setPreview(null)}
            className={secondaryButtonClass}
          >
            Cancel
          </button>
        </div>
      </div>
    )
  }

  return (
    <form onSubmit={handleCheck}>
      <label className="block text-sm font-medium">
        Invitation code
        <input
          className={inputClass}
          value={code}
          required
          autoComplete="off"
          onChange={(event) => setCode(event.target.value)}
        />
      </label>
      <ErrorNote message={error} />
      <button type="submit" disabled={busy} className={`mt-3 ${primaryButtonClass}`}>
        Check invitation
      </button>
    </form>
  )
}
