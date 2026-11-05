import type { SourceType } from '../../api/records'

type Props = {
  sourceType: SourceType | undefined
  sourceDocumentDeletedAt?: string | null
}

/** Always shows where a value came from. "From document" doesn't mean the document was verified. */
export function SourceLabel({ sourceType, sourceDocumentDeletedAt }: Props) {
  if (!sourceType) {
    return null
  }
  const label = sourceType === 'DOCUMENTED' ? 'From document' : 'User reported'
  return (
    <span className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-700">
      {label}
      {sourceDocumentDeletedAt ? ' · source document deleted' : ''}
    </span>
  )
}
