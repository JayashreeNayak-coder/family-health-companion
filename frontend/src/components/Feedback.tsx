import type { LucideIcon } from 'lucide-react'
import { CircleAlert } from 'lucide-react'
import type { ReactNode } from 'react'

export function ErrorNote({ message }: { message: string | null }) {
  if (!message) {
    return null
  }
  return (
    <p
      role="alert"
      className="mt-3 flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800"
    >
      <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <span>{message}</span>
    </p>
  )
}

/** Shows a missing value as missing, never as blank or zero. */
export function Missing({ children = 'Not recorded' }: { children?: ReactNode }) {
  return <span className="text-slate-500 italic">{children}</span>
}

/** Grey placeholder lines while content loads. Screen readers hear "Loading…". */
export function Loading({ lines = 3 }: { lines?: number }) {
  return (
    <div role="status" className="space-y-3">
      <span className="sr-only">Loading…</span>
      {Array.from({ length: lines }, (_, index) => (
        <div
          key={index}
          aria-hidden="true"
          className="h-4 animate-pulse rounded bg-slate-200"
          style={{ width: `${90 - index * 15}%` }}
        />
      ))}
    </div>
  )
}

/** What an empty list means, and what to do next. */
export function EmptyState({
  icon: Icon,
  title,
  children,
  action,
}: {
  icon?: LucideIcon
  title: string
  children?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center rounded-lg border border-dashed border-slate-300 px-4 py-8 text-center">
      {Icon && (
        <span className="mb-3 grid size-10 place-items-center rounded-full bg-slate-100 text-slate-500">
          <Icon aria-hidden="true" className="size-5" />
        </span>
      )}
      <p className="font-medium text-slate-800">{title}</p>
      {children && <div className="mt-1 max-w-md text-sm text-slate-600">{children}</div>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}
