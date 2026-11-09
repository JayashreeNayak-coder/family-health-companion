import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import { documentContent, type HealthDocument } from '../../api/documents'
import { ErrorNote, secondaryButtonClass } from '../../components/ui'

type Props = { profileId: string; document: HealthDocument }

/**
 * Loads the file through the API only when asked, then shows an image inline or offers the PDF.
 * The file is never reachable through a public link: this page holds it as a temporary blob.
 */
export function DocumentView({ profileId, document }: Props) {
  const [url, setUrl] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    return () => {
      if (url) {
        URL.revokeObjectURL(url)
      }
    }
  }, [url])

  async function load() {
    setBusy(true)
    setError(null)
    try {
      setUrl(URL.createObjectURL(await documentContent(profileId, document.id)))
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  const isImage = document.contentType.startsWith('image/')

  if (!url) {
    return (
      <>
        <button
          type="button"
          className={secondaryButtonClass}
          disabled={busy}
          aria-label={`Open ${document.originalFilename}`}
          onClick={() => void load()}
        >
          {busy ? 'Opening…' : isImage ? 'View' : 'Open'}
        </button>
        <ErrorNote message={error} />
      </>
    )
  }

  return (
    <div className="mt-2 w-full space-y-2">
      {isImage && (
        <img
          src={url}
          alt={document.description ?? document.originalFilename}
          className="max-h-96 rounded border border-slate-200"
        />
      )}
      <div className="flex flex-wrap gap-2">
        {!isImage && (
          <a className={secondaryButtonClass} href={url} target="_blank" rel="noreferrer">
            Open PDF in a new tab
          </a>
        )}
        <a className={secondaryButtonClass} href={url} download={document.originalFilename}>
          Download
        </a>
        <button type="button" className={secondaryButtonClass} onClick={() => setUrl(null)}>
          Close
        </button>
      </div>
    </div>
  )
}
