import type { ReactNode } from 'react'

export const inputClass = 'mt-1 w-full rounded border border-slate-300 px-3 py-2'

export const primaryButtonClass =
  'rounded bg-teal-700 px-4 py-2 font-medium text-white hover:bg-teal-800 disabled:opacity-60'

export const secondaryButtonClass =
  'rounded border border-slate-300 px-3 py-1.5 text-sm font-medium hover:bg-slate-50 disabled:opacity-60'

export const dangerButtonClass =
  'rounded border border-red-300 px-3 py-1.5 text-sm font-medium text-red-800 hover:bg-red-50 disabled:opacity-60'

export function ErrorNote({ message }: { message: string | null }) {
  if (!message) {
    return null
  }
  return (
    <p role="alert" className="mt-3 rounded bg-red-50 p-2 text-sm text-red-800">
      {message}
    </p>
  )
}

export function Card({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <section className="rounded-lg bg-white p-5 shadow">
      {title && <h3 className="mb-3 text-lg font-semibold">{title}</h3>}
      {children}
    </section>
  )
}

/** Shows a missing value as missing, never as blank or zero. */
export function Missing({ children = 'Not recorded' }: { children?: ReactNode }) {
  return <span className="text-slate-500 italic">{children}</span>
}
