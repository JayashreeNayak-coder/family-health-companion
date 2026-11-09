import { Loader2, Sparkles } from 'lucide-react'
import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import type { HealthDocument } from '../../api/documents'
import { failureMessage, getExtractionJob, requestExtraction } from '../../api/extraction'
import { Button } from '../../components/Button'
import { ErrorNote } from '../../components/Feedback'

type Props = {
  profileId: string
  document: HealthDocument
  onRequested: () => void
  onReview: () => void
}

/** Starts AI reading of a document and shows where it's up to. */
export function ExtractionControls({ profileId, document, onRequested, onReview }: Props) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const status = document.extractionStatus
  const readLabel = status === 'NOT_REQUESTED' ? 'Read with AI' : 'Read with AI again'

  async function start() {
    setBusy(true)
    setError(null)
    try {
      await requestExtraction(profileId, document.id)
      onRequested()
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  if (status === 'QUEUED' || status === 'PROCESSING') {
    return (
      <span
        role="status"
        className="inline-flex min-h-9 items-center gap-2 rounded-lg bg-violet-50 px-3 text-sm font-medium text-violet-800"
      >
        <Loader2 aria-hidden="true" className="size-4 animate-spin" />
        AI is reading this document…
      </span>
    )
  }
  if (status === 'READY_FOR_REVIEW') {
    return (
      <Button variant="primary" icon={Sparkles} onClick={onReview}>
        Review AI draft
      </Button>
    )
  }
  return (
    <>
      <Button
        icon={Sparkles}
        busy={busy}
        aria-label={`${readLabel}: ${document.originalFilename}`}
        onClick={() => void start()}
      >
        {readLabel}
      </Button>
      {status === 'FAILED' && <FailureNote profileId={profileId} documentId={document.id} />}
      <ErrorNote message={error} />
    </>
  )
}

function FailureNote({ profileId, documentId }: { profileId: string; documentId: string }) {
  const [code, setCode] = useState<string | null | undefined>(undefined)

  useEffect(() => {
    let ignore = false
    getExtractionJob(profileId, documentId).then(
      (job) => {
        if (!ignore) {
          setCode(job.errorCode)
        }
      },
      () => {
        if (!ignore) {
          setCode(null)
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [profileId, documentId])

  if (code === undefined) {
    return null
  }
  return (
    <p className="w-full rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">
      {failureMessage(code)} The document is kept, and you can enter its details by hand.
    </p>
  )
}
