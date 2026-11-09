import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import {
  CATEGORY_LABELS,
  deleteDocument,
  formatSize,
  listDocuments,
  type HealthDocument,
} from '../../api/documents'
import { Card, dangerButtonClass, ErrorNote, Missing, secondaryButtonClass } from '../../components/ui'
import { DocumentView } from './DocumentView'
import { DraftReview } from './DraftReview'
import { ExtractionControls } from './ExtractionControls'
import { UploadForm } from './UploadForm'
import { useConfirm } from '../../components/Dialog'
import { useToast } from '../../components/Toast'

type Props = { profileId: string; canEdit: boolean; canManage: boolean }

export function DocumentsPanel({ profileId, canEdit, canManage }: Props) {
  const [documents, setDocuments] = useState<HealthDocument[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [uploading, setUploading] = useState(false)
  const [reviewing, setReviewing] = useState<string | null>(null)
  const [confirm, confirmDialog] = useConfirm()
  const toast = useToast()
  const inProgress = documents?.some((document) =>
    ['QUEUED', 'PROCESSING'].includes(document.extractionStatus),
  )

  // While the AI is reading a document, check again every few seconds.
  useEffect(() => {
    if (!inProgress) {
      return
    }
    const timer = window.setTimeout(() => setRevision((current) => current + 1), 3000)
    return () => window.clearTimeout(timer)
  }, [inProgress, documents])

  useEffect(() => {
    let ignore = false
    listDocuments(profileId).then(
      (page) => {
        if (!ignore) {
          setDocuments(page.items)
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
  }, [profileId, revision])

  function reload() {
    setUploading(false)
    setRevision((current) => current + 1)
  }

  async function handleDelete(document: HealthDocument) {
    const confirmed = await confirm({
      title: 'Delete this document?',
      message: `${document.originalFilename} will be permanently deleted. Records that came from it keep their data, and show that the source was deleted.`,
      confirmLabel: 'Delete document',
    })
    if (!confirmed) {
      return
    }
    setError(null)
    try {
      await deleteDocument(profileId, document.id)
      toast('Document deleted')
      reload()
    } catch (failure) {
      setError(errorMessage(failure))
    }
  }

  return (
    <Card title="Documents">
      {confirmDialog}
      <p className="mb-3 text-sm text-slate-600">
        Prescriptions, lab reports, scans, bills, and photos, kept as evidence. Files are private to
        the people with access to this profile. Scans and X-rays are stored, never interpreted.
        &ldquo;Read with AI&rdquo; sends the file to an outside AI service to draft records for you to
        check: use synthetic documents only.
      </p>
      <ErrorNote message={error} />
      {!documents && !error && <p role="status">Loading…</p>}
      {documents && documents.length === 0 && (
        <p className="text-sm text-slate-600">No documents uploaded.</p>
      )}
      {documents && documents.length > 0 && (
        <ul className="divide-y divide-slate-200">
          {documents.map((document) => (
            <li key={document.id} className="py-3">
              <div className="flex flex-wrap items-center gap-2">
                <strong>{document.description ?? document.originalFilename}</strong>
                <span className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-700">
                  {CATEGORY_LABELS[document.category]}
                </span>
              </div>
              <p className="text-sm text-slate-600">
                {document.originalFilename} · {formatSize(document.sizeBytes)} · Dated{' '}
                {document.documentOn ?? <Missing />}
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                <DocumentView profileId={profileId} document={document} />
                {canEdit && (
                  <ExtractionControls
                    profileId={profileId}
                    document={document}
                    onRequested={() => setRevision((current) => current + 1)}
                    onReview={() => setReviewing(document.id)}
                  />
                )}
                {canManage && (
                  <button
                    type="button"
                    className={dangerButtonClass}
                    aria-label={`Delete ${document.originalFilename}`}
                    onClick={() => void handleDelete(document)}
                  >
                    Delete
                  </button>
                )}
              </div>
              {reviewing === document.id && (
                <DraftReview
                  profileId={profileId}
                  document={document}
                  onClose={() => {
                    setReviewing(null)
                    setRevision((current) => current + 1)
                  }}
                />
              )}
            </li>
          ))}
        </ul>
      )}
      {canEdit && !uploading && (
        <button
          type="button"
          className={`mt-3 ${secondaryButtonClass}`}
          onClick={() => setUploading(true)}
        >
          Upload a document
        </button>
      )}
      {canEdit && uploading && (
        <UploadForm
          profileId={profileId}
          onUploaded={() => {
            toast('Document uploaded')
            reload()
          }} onCancel={() => setUploading(false)} />
      )}
    </Card>
  )
}
