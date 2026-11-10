import { ImagePlus, Trash2 } from 'lucide-react'
import { useId, useRef, useState } from 'react'
import { errorMessage } from '../api/client'
import { Avatar } from './Avatar'
import { buttonClass, Button } from './Button'
import { ErrorNote } from './Feedback'

const MAX_BYTES = 5 * 1024 * 1024

type Props = {
  /** Whose photo, for the initials and the control names, such as "Ravi Demo" or "Demo Family". */
  name: string
  photoUrl: string | null
  onUpload: (file: File) => Promise<unknown>
  onRemove: () => Promise<unknown>
}

/**
 * Choose, replace, or remove a photo. The server checks the file again, crops it to a square,
 * shrinks it, and drops hidden details such as location.
 */
export function PhotoPicker({ name, photoUrl, onUpload, onRemove }: Props) {
  const inputId = useId()
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState<'upload' | 'remove' | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function run(action: 'upload' | 'remove', work: () => Promise<unknown>) {
    setBusy(action)
    setError(null)
    try {
      await work()
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(null)
    }
  }

  function chosen(file: File | undefined) {
    if (input.current) {
      input.current.value = ''
    }
    if (!file) {
      return
    }
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      setError('Choose a JPEG or PNG photo.')
      return
    }
    if (file.size > MAX_BYTES) {
      setError('A photo can be at most 5 MB.')
      return
    }
    void run('upload', () => onUpload(file))
  }

  return (
    <div>
      <div className="flex flex-wrap items-center gap-4">
        <Avatar name={name} photoUrl={photoUrl} className="size-20 bg-brand-100 text-2xl font-semibold text-brand-800" />
        <div className="flex flex-wrap gap-2">
          <input
            ref={input}
            id={inputId}
            type="file"
            accept="image/jpeg,image/png"
            className="peer sr-only"
            disabled={busy !== null}
            onChange={(event) => chosen(event.target.files?.[0])}
          />
          <label
            htmlFor={inputId}
            aria-busy={busy === 'upload' || undefined}
            className={`${buttonClass('secondary', 'sm')} cursor-pointer peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-brand-600`}
          >
            <ImagePlus aria-hidden="true" className="size-4" />
            {busy === 'upload' ? 'Uploading…' : photoUrl ? 'Change photo' : 'Add a photo'}
          </label>
          {photoUrl && (
            <Button icon={Trash2} variant="ghost" busy={busy === 'remove'} onClick={() => void run('remove', onRemove)}>
              Remove photo
            </Button>
          )}
        </div>
      </div>
      <p className="mt-2 text-sm text-slate-600">JPEG or PNG, up to 5 MB. It is cropped to a square.</p>
      <ErrorNote message={error} />
    </div>
  )
}
