import { useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import {
  ACCEPTED_FILES,
  CATEGORY_LABELS,
  MAX_UPLOAD_BYTES,
  uploadDocument,
  type DocumentCategory,
  type HealthDocument,
} from '../../api/documents'
import { ErrorNote, inputClass, primaryButtonClass, secondaryButtonClass } from '../../components/ui'

type Props = {
  profileId: string
  defaultCategory?: DocumentCategory
  submitLabel?: string
  onUploaded: (document: HealthDocument) => void | Promise<void>
  onCancel?: () => void
}

const ALLOWED_TYPES = ['application/pdf', 'image/jpeg', 'image/png']

/** On a phone, the file picker also offers the camera, so a prescription can be photographed directly. */
export function UploadForm({
  profileId,
  defaultCategory = 'OTHER',
  submitLabel = 'Upload',
  onUploaded,
  onCancel,
}: Props) {
  const [file, setFile] = useState<File | null>(null)
  const [category, setCategory] = useState<DocumentCategory>(defaultCategory)
  const [documentOn, setDocumentOn] = useState('')
  const [description, setDescription] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!file) {
      setError('Choose a file to upload')
      return
    }
    // A quick check for the user's sake. The backend checks the file's real content regardless.
    if (file.type && !ALLOWED_TYPES.includes(file.type)) {
      setError('Only PDF, JPEG, and PNG files are allowed')
      return
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      setError('The file is larger than 10 MB')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const uploaded = await uploadDocument(profileId, file, {
        category,
        documentOn: documentOn || null,
        description: description.trim() || null,
      })
      await onUploaded(uploaded)
    } catch (failure) {
      setError(errorMessage(failure))
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mt-3 space-y-2 rounded border border-slate-200 p-3">
      <label className="block text-sm font-medium">
        File (PDF, JPEG, or PNG, up to 10 MB)
        <input
          type="file"
          accept={ACCEPTED_FILES}
          className={inputClass}
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
        />
      </label>
      <label className="block text-sm font-medium">
        Type
        <select
          className={inputClass}
          value={category}
          onChange={(event) => setCategory(event.target.value as DocumentCategory)}
        >
          {Object.entries(CATEGORY_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <label className="block text-sm font-medium">
        Date on the document (optional)
        <input
          type="date"
          className={inputClass}
          value={documentOn}
          onChange={(event) => setDocumentOn(event.target.value)}
        />
      </label>
      <label className="block text-sm font-medium">
        Description (optional)
        <input
          className={inputClass}
          maxLength={200}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
      </label>
      <ErrorNote message={error} />
      <div className="flex flex-wrap gap-2">
        <button type="submit" disabled={busy} className={primaryButtonClass}>
          {busy ? 'Uploading…' : submitLabel}
        </button>
        {onCancel && (
          <button type="button" onClick={onCancel} className={secondaryButtonClass}>
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}
