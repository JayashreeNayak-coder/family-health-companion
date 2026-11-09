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
import { FolderOpen, Paperclip, Upload, X } from 'lucide-react'
import { Button } from '../../components/Button'
import { Dialog, useConfirm } from '../../components/Dialog'
import { ErrorNote, Loading } from '../../components/Feedback'
import { inputClass } from '../../components/formStyles'
import { DocumentView } from './DocumentView'
import { UploadForm } from './UploadForm'

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
    <div className="mt-3 text-sm">
      {confirmDialog}
      {attachments.length > 0 && (
        <ul aria-label={`Attachments for ${recordTitle}`} className="mb-2 space-y-2">
          {attachments.map((attachment) => (
            <li
              key={attachment.id}
              className="flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 bg-slate-50/60 px-3 py-2"
            >
              <Paperclip aria-hidden="true" className="size-4 shrink-0 text-slate-500" />
              <span className="min-w-0 flex-1 break-words text-slate-800">
                {attachment.document.description ?? attachment.document.originalFilename}
              </span>
              <DocumentView profileId={profileId} document={attachment.document} />
              {canEdit && (
                <button
                  type="button"
                  aria-label={`Remove ${attachment.document.originalFilename} from ${recordTitle}`}
                  title="Remove attachment"
                  onClick={() => void detach(attachment)}
                  className="grid size-8 place-items-center rounded-lg text-slate-500 hover:bg-red-50 hover:text-red-700"
                >
                  <X aria-hidden="true" className="size-4" />
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      <ErrorNote message={error} />
      {canEdit && (
        <div className="flex flex-wrap gap-1">
          <Button
            variant="ghost"
            icon={Upload}
            aria-label={`Attach a file to ${recordTitle}`}
            onClick={() => setMode('upload')}
            className="text-brand-700"
          >
            Attach a file
          </Button>
          <Button
            variant="ghost"
            icon={FolderOpen}
            aria-label={`Attach from Documents to ${recordTitle}`}
            onClick={() => setMode('existing')}
            className="text-brand-700"
          >
            Attach from Documents
          </Button>
        </div>
      )}
      <Dialog
        open={canEdit && mode === 'upload'}
        title={`Attach a file to ${recordTitle}`}
        onClose={() => setMode('closed')}
      >
        <UploadForm
          profileId={profileId}
          defaultCategory={recordType === 'LAB_RESULT' ? 'LAB_REPORT' : 'PHOTO'}
          submitLabel="Upload and attach"
          onUploaded={attach}
          onCancel={() => setMode('closed')}
        />
      </Dialog>
      <Dialog
        open={canEdit && mode === 'existing'}
        title={`Attach a document to ${recordTitle}`}
        onClose={() => setMode('closed')}
      >
        <ExistingDocumentPicker
          profileId={profileId}
          exclude={attachments.map((attachment) => attachment.document.id)}
          onPick={attach}
          onCancel={() => setMode('closed')}
        />
      </Dialog>
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
    <form onSubmit={handleSubmit} className="space-y-4 text-sm">
      {!documents && !error && <Loading lines={2} />}
      {documents && documents.length === 0 && (
        <p className="text-slate-600">No other documents uploaded for this profile.</p>
      )}
      {documents && documents.length > 0 && (
        <label className="block font-medium text-slate-800">
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
      <div className="flex flex-wrap justify-end gap-2">
        <Button size="md" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" variant="primary" size="md" disabled={!selected}>
          Attach
        </Button>
      </div>
    </form>
  )
}
