import { FileText, FolderOpen, Image, Lock, Sparkles, Trash2, Upload } from 'lucide-react'
import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import {
  CATEGORY_LABELS,
  deleteDocument,
  formatSize,
  listDocuments,
  type HealthDocument,
} from '../../api/documents'
import { AiBadge, Badge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import { Dialog, useConfirm } from '../../components/Dialog'
import { EmptyState, ErrorNote, Loading, Missing } from '../../components/Feedback'
import { useToast } from '../../components/Toast'
import { DocumentView } from './DocumentView'
import { DraftReview } from './DraftReview'
import { ExtractionControls } from './ExtractionControls'
import { UploadForm } from './UploadForm'

type Props = { profileId: string; canEdit: boolean; canManage: boolean }

/** Where the AI is with a document, as a short label. Nothing for a document it never read. */
function AiStatus({ status }: { status: string }) {
  if (status === 'READY_FOR_REVIEW') {
    return <AiBadge>Draft ready to review</AiBadge>
  }
  if (status === 'REVIEWED') {
    return <Badge tone="success">AI draft reviewed</Badge>
  }
  if (status === 'FAILED') {
    return <Badge>AI couldn't read it</Badge>
  }
  return null
}

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

  const uploadButton = canEdit && (
    <Button variant="primary" icon={Upload} onClick={() => setUploading(true)}>
      Upload a document
    </Button>
  )

  return (
    <Card
      title="Documents"
      description="Prescriptions, lab reports, scans, bills, and photos, kept as evidence."
      actions={uploadButton}
    >
      {confirmDialog}
      <div className="mb-5 grid gap-2 text-sm sm:grid-cols-2">
        <p className="flex items-start gap-2 rounded-lg bg-slate-50 px-3 py-2 text-slate-700">
          <Lock aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-slate-500" />
          <span>
            Files are private to the people with access to this profile. Scans and X-rays are
            stored, never interpreted.
          </span>
        </p>
        <p className="flex items-start gap-2 rounded-lg bg-violet-50 px-3 py-2 text-violet-900">
          <Sparkles aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          <span>
            &ldquo;Read with AI&rdquo; sends the file to an outside AI service to draft records for
            you to check: use synthetic documents only.
          </span>
        </p>
      </div>
      <ErrorNote message={error} />
      {!documents && !error && <Loading />}
      {documents && documents.length === 0 && (
        <EmptyState icon={FolderOpen} title="No documents uploaded.">
          Upload a prescription or report to keep it with this profile.
        </EmptyState>
      )}
      {documents && documents.length > 0 && (
        <ul className="divide-y divide-slate-100">
          {documents.map((document) => {
            const isImage = document.contentType.startsWith('image/')
            const Icon = isImage ? Image : FileText
            return (
              <li key={document.id} className="py-4 first:pt-0 last:pb-0">
                <div className="flex items-start gap-3">
                  <span
                    aria-hidden="true"
                    className="grid size-10 shrink-0 place-items-center rounded-lg bg-brand-50 text-brand-700"
                  >
                    <Icon className="size-5" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <h4 className="font-semibold break-words text-slate-900">
                        {document.description ?? document.originalFilename}
                      </h4>
                      <Badge>{CATEGORY_LABELS[document.category]}</Badge>
                      <AiStatus status={document.extractionStatus} />
                    </div>
                    <p className="mt-0.5 text-sm break-all text-slate-500">
                      {document.originalFilename} · {formatSize(document.sizeBytes)} · Dated{' '}
                      {document.documentOn ?? <Missing />}
                    </p>
                  </div>
                  {canManage && (
                    <button
                      type="button"
                      aria-label={`Delete ${document.originalFilename}`}
                      title="Delete"
                      onClick={() => void handleDelete(document)}
                      className="grid size-9 shrink-0 place-items-center rounded-lg text-slate-500 hover:bg-red-50 hover:text-red-700"
                    >
                      <Trash2 aria-hidden="true" className="size-4" />
                    </button>
                  )}
                </div>
                <div className="mt-3 flex flex-wrap items-start gap-2 sm:pl-13">
                  <DocumentView profileId={profileId} document={document} />
                  {canEdit && (
                    <ExtractionControls
                      profileId={profileId}
                      document={document}
                      onRequested={() => setRevision((current) => current + 1)}
                      onReview={() => setReviewing(document.id)}
                    />
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
            )
          })}
        </ul>
      )}
      <Dialog open={canEdit && uploading} title="Upload a document" onClose={() => setUploading(false)}>
        <UploadForm
          profileId={profileId}
          onUploaded={() => {
            toast('Document uploaded')
            reload()
          }}
          onCancel={() => setUploading(false)}
        />
      </Dialog>
    </Card>
  )
}
