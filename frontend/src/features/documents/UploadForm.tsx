import { FileUp } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import {
  ACCEPTED_FILES,
  CATEGORY_LABELS,
  formatSize,
  MAX_UPLOAD_BYTES,
  uploadDocument,
  type DocumentCategory,
  type HealthDocument,
} from '../../api/documents'
import { Button } from '../../components/Button'
import { ErrorNote } from '../../components/Feedback'
import { inputClass } from '../../components/formStyles'

type Props = {
  profileId: string
  defaultCategory?: DocumentCategory
  submitLabel?: string
  onUploaded: (document: HealthDocument) => void | Promise<void>
  onCancel?: () => void
}

const ALLOWED_TYPES = ['application/pdf', 'image/jpeg', 'image/png']

const labelClass = 'block text-sm font-medium text-slate-800'

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
    <form onSubmit={handleSubmit} className="space-y-4">
      <label className="flex cursor-pointer flex-col items-center gap-2 rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center transition-colors hover:border-brand-600 hover:bg-brand-50/50 has-[:focus-visible]:border-brand-600">
        <FileUp aria-hidden="true" className="size-7 text-brand-700" />
        <span className="text-sm font-medium text-slate-800">
          File (PDF, JPEG, or PNG, up to 10 MB)
        </span>
        <span className="text-xs text-slate-500">
          {file ? `${file.name} · ${formatSize(file.size)}` : 'Choose a file, or take a photo on a phone'}
        </span>
        <input
          type="file"
          accept={ACCEPTED_FILES}
          className="sr-only"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
        />
      </label>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className={labelClass}>
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
        <label className={labelClass}>
          Date on the document (optional)
          <input
            type="date"
            className={inputClass}
            value={documentOn}
            onChange={(event) => setDocumentOn(event.target.value)}
          />
        </label>
      </div>
      <label className={labelClass}>
        Description (optional)
        <input
          className={inputClass}
          maxLength={200}
          placeholder="For example, “Prescription from the March visit”"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
      </label>
      <ErrorNote message={error} />
      <div className="flex flex-wrap justify-end gap-2 pt-1">
        {onCancel && (
          <Button size="md" onClick={onCancel}>
            Cancel
          </Button>
        )}
        <Button type="submit" variant="primary" size="md" busy={busy}>
          {busy ? 'Uploading…' : submitLabel}
        </Button>
      </div>
    </form>
  )
}
