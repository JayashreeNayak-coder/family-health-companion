import { Download, ExternalLink, Eye, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import { documentContent, type HealthDocument } from '../../api/documents'
import { Button, buttonClass } from '../../components/Button'
import { ErrorNote } from '../../components/Feedback'

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
  const openLabel = isImage ? 'View' : 'Open'

  if (!url) {
    return (
      <>
        <Button icon={Eye} busy={busy} aria-label={`${openLabel} ${document.originalFilename}`} onClick={() => void load()}>
          {busy ? 'Opening…' : openLabel}
        </Button>
        <ErrorNote message={error} />
      </>
    )
  }

  return (
    <div className="w-full space-y-2">
      {isImage && (
        <img
          src={url}
          alt={document.description ?? document.originalFilename}
          className="max-h-96 max-w-full rounded-lg border border-slate-200 bg-white"
        />
      )}
      <div className="flex flex-wrap gap-2">
        {!isImage && (
          <a className={buttonClass('secondary', 'sm')} href={url} target="_blank" rel="noreferrer">
            <ExternalLink aria-hidden="true" className="size-4" />
            Open PDF in a new tab
          </a>
        )}
        <a className={buttonClass('secondary', 'sm')} href={url} download={document.originalFilename}>
          <Download aria-hidden="true" className="size-4" />
          Download
        </a>
        <Button variant="ghost" icon={X} onClick={() => setUrl(null)}>
          Close
        </Button>
      </div>
    </div>
  )
}
