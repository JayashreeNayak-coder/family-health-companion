import type { ReactNode } from 'react'

type Props = {
  title?: string
  description?: ReactNode
  /** Controls shown at the right of the header, such as an "Add" button. */
  actions?: ReactNode
  children: ReactNode
  className?: string
}

/** A bordered surface for one group of content. Depth comes from the border, not a shadow. */
export function Card({ title, description, actions, children, className = '' }: Props) {
  return (
    <section className={`rounded-xl border border-slate-200 bg-white p-5 sm:p-6 ${className}`}>
      {(title || actions) && (
        <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            {title && <h3 className="text-base font-semibold text-slate-900">{title}</h3>}
            {description && <p className="mt-1 text-sm text-slate-600">{description}</p>}
          </div>
          {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  )
}
