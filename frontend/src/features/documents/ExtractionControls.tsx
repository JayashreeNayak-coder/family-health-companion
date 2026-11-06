import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import type { HealthDocument } from '../../api/documents'
import { failureMessage, getExtractionJob, requestExtraction } from '../../api/extraction'
import { ErrorNote, secondaryButtonClass } from '../../components/ui'

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
      <span role="status" className="self-center text-sm text-slate-600">
        AI is reading this document…
      </span>
    )
  }
  if (status === 'READY_FOR_REVIEW') {
    return (
      <button type="button" className={secondaryButtonClass} onClick={onReview}>
        Review AI draft
      </button>
    )
  }
  return (
    <>
      {status === 'FAILED' && <FailureNote profileId={profileId} documentId={document.id} />}
      <button
        type="button"
        className={secondaryButtonClass}
        disabled={busy}
        aria-label={`Read ${document.originalFilename} with AI`}
        onClick={() => void start()}
      >
        {status === 'NOT_REQUESTED' ? 'Read with AI' : 'Read with AI again'}
      </button>
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
    <span className="self-center text-sm text-slate-700">
      {failureMessage(code)} The document is kept, and you can enter its details by hand.
    </span>
  )
}
