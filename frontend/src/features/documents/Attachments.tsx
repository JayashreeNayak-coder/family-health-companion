import { useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import {
  attachDocument,
  detachDocument,
  listDocuments,
  type Attachment,
  type HealthDocument,
  type RecordType,
} from '../../api/documents'
import {
  ErrorNote,
  inputClass,
  primaryButtonClass,
  secondaryButtonClass,
} from '../../components/ui'
import { DocumentView } from './DocumentView'
import { UploadForm } from './UploadForm'
import { useConfirm } from '../../components/Dialog'

type Props = {
  profileId: string
  recordType: RecordType
  recordId: string
  recordTitle: string
  attachments: Attachment[]
  canEdit: boolean
  onChanged: () => void
}

/** Files attached to one record as evidence, such as the prescription from a visit. */
export function Attachments({
  profileId,
  recordType,
  recordId,
  recordTitle,
  attachments,
  canEdit,
  onChanged,
}: Props) {
  const [mode, setMode] = useState<'closed' | 'upload' | 'existing'>('closed')
  const [error, setError] = useState<string | null>(null)
  const [confirm, confirmDialog] = useConfirm()

  async function attach(document: HealthDocument) {
    await attachDocument(profileId, recordType, recordId, document.id)
    setMode('closed')
    onChanged()
  }

  async function detach(attachment: Attachment) {
    const confirmed = await confirm({
      title: 'Remove this attachment?',
      message: `${attachment.document.originalFilename} will be removed from this record. The document itself is kept in Documents.`,
      confirmLabel: 'Remove',
    })
    if (!confirmed) {
      return
    }
    setError(null)
    try {
      await detachDocument(profileId, attachment.id)
      onChanged()
    } catch (failure) {
      setError(errorMessage(failure))
    }
  }

  if (attachments.length === 0 && !canEdit) {
    return null
  }

  return (
    <div className="mt-2 text-sm">
      {confirmDialog}
      {attachments.length > 0 && (
        <ul aria-label={`Attachments for ${recordTitle}`} className="space-y-1">
          {attachments.map((attachment) => (
            <li key={attachment.id} className="flex flex-wrap items-center gap-2">
              <span aria-hidden="true">📎</span>
              <span>{attachment.document.description ?? attachment.document.originalFilename}</span>
              <DocumentView profileId={profileId} document={attachment.document} />
              {canEdit && (
                <button
                  type="button"
                  className={secondaryButtonClass}
                  aria-label={`Remove ${attachment.document.originalFilename} from ${recordTitle}`}
                  onClick={() => void detach(attachment)}
                >
                  Remove
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      <ErrorNote message={error} />
      {canEdit && mode === 'closed' && (
        <div className="mt-2 flex flex-wrap gap-2">
          <button
            type="button"
            className={secondaryButtonClass}
            aria-label={`Attach a new file to ${recordTitle}`}
            onClick={() => setMode('upload')}
          >
            Attach a photo or file
          </button>
          <button
            type="button"
            className={secondaryButtonClass}
            aria-label={`Attach an uploaded document to ${recordTitle}`}
            onClick={() => setMode('existing')}
          >
            Attach an uploaded document
          </button>
        </div>
      )}
      {canEdit && mode === 'upload' && (
        <UploadForm
          profileId={profileId}
          defaultCategory={recordType === 'LAB_RESULT' ? 'LAB_REPORT' : 'PHOTO'}
          submitLabel="Upload and attach"
          onUploaded={attach}
          onCancel={() => setMode('closed')}
        />
      )}
      {canEdit && mode === 'existing' && (
        <ExistingDocumentPicker
          profileId={profileId}
          exclude={attachments.map((attachment) => attachment.document.id)}
          onPick={attach}
          onCancel={() => setMode('closed')}
        />
      )}
    </div>
  )
}

function ExistingDocumentPicker({
  profileId,
  exclude,
  onPick,
  onCancel,
}: {
  profileId: string
  exclude: string[]
  onPick: (document: HealthDocument) => Promise<void>
  onCancel: () => void
}) {
  const [documents, setDocuments] = useState<HealthDocument[] | null>(null)
  const [selected, setSelected] = useState('')
  const [error, setError] = useState<string | null>(null)
  const excluded = exclude.join(',')

  useEffect(() => {
    let ignore = false
    listDocuments(profileId).then(
      (page) => {
        if (!ignore) {
          setDocuments(page.items.filter((document) => !excluded.split(',').includes(document.id)))
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
  }, [profileId, excluded])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const document = documents?.find((candidate) => candidate.id === selected)
    if (!document) {
      return
    }
    setError(null)
    try {
      await onPick(document)
    } catch (failure) {
      setError(errorMessage(failure))
    }
  }

  return (
    <form onSubmit={handleSubmit} className="mt-2 space-y-2 rounded border border-slate-200 p-3">
      {!documents && !error && <p role="status">Loading…</p>}
      {documents && documents.length === 0 && (
        <p className="text-slate-600">No other documents uploaded for this profile.</p>
      )}
      {documents && documents.length > 0 && (
        <label className="block font-medium">
          Document
          <select className={inputClass} value={selected} onChange={(event) => setSelected(event.target.value)}>
            <option value="">Choose a document</option>
            {documents.map((document) => (
              <option key={document.id} value={document.id}>
                {document.description ?? document.originalFilename}
              </option>
            ))}
          </select>
        </label>
      )}
      <ErrorNote message={error} />
      <div className="flex flex-wrap gap-2">
        <button type="submit" disabled={!selected} className={primaryButtonClass}>
          Attach
        </button>
        <button type="button" onClick={onCancel} className={secondaryButtonClass}>
          Cancel
        </button>
      </div>
    </form>
  )
}
