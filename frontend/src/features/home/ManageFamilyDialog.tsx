import { useId, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import {
  deleteFamily,
  familyPhotoUrl,
  removeFamilyPhoto,
  renameFamily,
  setFamilyPhoto,
  type FamilySummary,
} from '../../api/families'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { ErrorNote } from '../../components/Feedback'
import { inputClass } from '../../components/formStyles'
import { PhotoPicker } from '../../components/PhotoPicker'
import { useToast } from '../../components/Toast'

type Props = {
  open: boolean
  summary: FamilySummary
  /** Accounts in the family. Deleting needs this account to be the only one (DECISIONS.md, D-031). */
  memberCount: number
  profileCount: number
  onClose: () => void
  /** Called after any change, so Home reloads its lists. */
  onChanged: () => void
}

/**
 * A manager's settings for one family: its name, its photo, and deleting it. Mount it only while
 * open, so each opening starts from the latest summary.
 */
export function ManageFamilyDialog({ open, summary, memberCount, profileCount, onClose, onChanged }: Props) {
  const [current, setCurrent] = useState(summary)
  const [name, setName] = useState(summary.name)
  const [confirmName, setConfirmName] = useState('')
  const [busy, setBusy] = useState<'rename' | 'delete' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const toast = useToast()
  const nameId = useId()
  const confirmId = useId()

  function changed(saved: FamilySummary) {
    setCurrent(saved)
    onChanged()
  }

  async function rename(event: FormEvent) {
    event.preventDefault()
    setBusy('rename')
    setError(null)
    try {
      changed(await renameFamily(current.id, name, current.version))
      toast('Family renamed')
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(null)
    }
  }

  async function remove() {
    setBusy('delete')
    setError(null)
    try {
      await deleteFamily(current.id)
      toast(`${current.name} deleted`)
      onClose()
      onChanged()
    } catch (failure) {
      setError(errorMessage(failure))
      setBusy(null)
    }
  }

  const soleMember = memberCount <= 1
  const nameChanged = name.trim().replace(/\s+/g, ' ') !== current.name

  return (
    <Dialog open={open} title={`Manage ${current.name}`} onClose={onClose}>
      <div className="space-y-6">
        <form onSubmit={rename} className="space-y-2">
          <label htmlFor={nameId} className="block text-sm font-semibold text-slate-900">
            Family name
          </label>
          <input
            id={nameId}
            className={inputClass}
            value={name}
            required
            maxLength={100}
            onChange={(event) => setName(event.target.value)}
          />
          <p className="text-sm text-slate-600">No two of your families can have the same name.</p>
          <Button type="submit" variant="primary" busy={busy === 'rename'} disabled={!name.trim() || !nameChanged}>
            Save name
          </Button>
        </form>

        <section className="space-y-2 border-t border-slate-200 pt-4">
          <h3 className="text-sm font-semibold text-slate-900">Family photo</h3>
          <PhotoPicker
            name={current.name}
            photoUrl={familyPhotoUrl(current)}
            onUpload={async (file) => changed(await setFamilyPhoto(current.id, file))}
            onRemove={async () => changed(await removeFamilyPhoto(current.id))}
          />
        </section>

        <section className="space-y-2 border-t border-slate-200 pt-4">
          <h3 className="text-sm font-semibold text-red-800">Delete family</h3>
          {soleMember ? (
            <>
              <p className="text-sm text-slate-700">
                This permanently deletes the family and its {profileCount}{' '}
                {profileCount === 1 ? 'profile' : 'profiles'}, with every record, document, photo, summary, and
                emergency card in them. Anyone they were shared with loses access. This can't be undone.
              </p>
              <label htmlFor={confirmId} className="block text-sm font-medium text-slate-900">
                Type the family name, {current.name}, to confirm
              </label>
              <input
                id={confirmId}
                className={inputClass}
                value={confirmName}
                autoComplete="off"
                onChange={(event) => setConfirmName(event.target.value)}
              />
              <Button
                variant="danger"
                busy={busy === 'delete'}
                disabled={confirmName.trim() !== current.name}
                onClick={() => void remove()}
              >
                Delete family
              </Button>
            </>
          ) : (
            <p className="text-sm text-slate-700">
              A family can be deleted only when you are its only member. It has {memberCount} members, and deleting it
              would destroy their profiles too.
            </p>
          )}
        </section>

        <ErrorNote message={error} />
      </div>
    </Dialog>
  )
}
