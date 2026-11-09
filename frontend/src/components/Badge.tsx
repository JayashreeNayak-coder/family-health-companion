import type { LucideIcon } from 'lucide-react'
import { FileText, Sparkles, UserRound } from 'lucide-react'
import type { ReactNode } from 'react'
import type { SourceType } from '../api/records'

export type BadgeTone = 'neutral' | 'brand' | 'success' | 'review' | 'danger' | 'info'

const tones: Record<BadgeTone, string> = {
  neutral: 'bg-slate-100 text-slate-700',
  brand: 'bg-brand-50 text-brand-800',
  success: 'bg-emerald-50 text-emerald-800',
  review: 'bg-amber-50 text-amber-800',
  danger: 'bg-red-50 text-red-800',
  info: 'bg-sky-50 text-sky-800',
}

/** A short state or category label. Always text, so it never relies on colour alone. */
export function Badge({
  tone = 'neutral',
  icon: Icon,
  children,
}: {
  tone?: BadgeTone
  icon?: LucideIcon
  children: ReactNode
}) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap ${tones[tone]}`}
    >
      {Icon && <Icon aria-hidden="true" className="size-3.5" />}
      {children}
    </span>
  )
}

/**
 * Where a record came from, the same everywhere (docs/06 section 8.3). "From document" means
 * taken from an uploaded file. It doesn't mean the document was verified.
 */
export function SourceBadge({
  sourceType,
  sourceDocumentDeletedAt,
}: {
  sourceType: SourceType | undefined
  sourceDocumentDeletedAt?: string | null
}) {
  if (!sourceType) {
    return null
  }
  const documented = sourceType === 'DOCUMENTED'
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap ${
        documented
          ? 'bg-source-doc-bg text-source-doc-fg'
          : 'bg-source-user-bg text-source-user-fg'
      }`}
    >
      {documented ? (
        <FileText aria-hidden="true" className="size-3.5" />
      ) : (
        <UserRound aria-hidden="true" className="size-3.5" />
      )}
      {documented ? 'From document' : 'User reported'}
      {sourceDocumentDeletedAt ? ' · source document deleted' : ''}
    </span>
  )
}

/** Marks content the AI produced. It is never presented as a confirmed record. */
export function AiBadge({ children = 'AI generated' }: { children?: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-source-ai-bg px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap text-source-ai-fg">
      <Sparkles aria-hidden="true" className="size-3.5" />
      {children}
    </span>
  )
}
