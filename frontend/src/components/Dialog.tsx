import { X } from 'lucide-react'
import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { Button } from './Button'

type DialogProps = {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  /** Buttons at the bottom, such as Cancel and Save. */
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
}

const widths = { sm: 'max-w-md', md: 'max-w-lg', lg: 'max-w-3xl' }

/**
 * A modal window. Escape and the close button close it, focus moves into it when it opens and
 * back when it closes, and Tab stays inside it.
 */
export function Dialog({ open, title, onClose, children, footer, size = 'md' }: DialogProps) {
  const titleId = useId()
  const panel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    const previous = document.activeElement as HTMLElement | null
    const first = panel.current?.querySelector<HTMLElement>(
      'input, select, textarea, button:not([data-dialog-close]), [href]',
    )
    ;(first ?? panel.current)?.focus()
    return () => previous?.focus?.()
  }, [open])

  if (!open) {
    return null
  }

  function handleKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'Escape') {
      event.stopPropagation()
      onClose()
      return
    }
    if (event.key !== 'Tab' || !panel.current) {
      return
    }
    const focusable = Array.from(
      panel.current.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled])',
      ),
    )
    if (focusable.length === 0) {
      return
    }
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center p-0 sm:items-center sm:p-4">
      <div aria-hidden="true" className="absolute inset-0 bg-slate-900/40" onClick={onClose} />
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onKeyDown={handleKeyDown}
        className={`relative flex max-h-[92vh] w-full ${widths[size]} flex-col rounded-t-2xl bg-white shadow-xl sm:rounded-2xl`}
      >
        <div className="flex items-start justify-between gap-4 border-b border-slate-200 px-5 py-4">
          <h2 id={titleId} className="text-lg font-semibold text-slate-900">
            {title}
          </h2>
          <button
            type="button"
            data-dialog-close
            onClick={onClose}
            aria-label="Close"
            className="-mr-1 rounded-lg p-1 text-slate-500 hover:bg-slate-100 hover:text-slate-800"
          >
            <X aria-hidden="true" className="size-5" />
          </button>
        </div>
        <div className="overflow-y-auto px-5 py-4">{children}</div>
        {footer && (
          <div className="flex flex-wrap justify-end gap-2 border-t border-slate-200 px-5 py-3">
            {footer}
          </div>
        )}
      </div>
    </div>
  )
}

export type ConfirmOptions = {
  title: string
  message: ReactNode
  confirmLabel: string
  /** Danger for anything that deletes or discards. */
  tone?: 'danger' | 'primary'
}

type Pending = ConfirmOptions & { resolve: (confirmed: boolean) => void }

/**
 * Asks the user to confirm an action, in the page's own dialog rather than the browser's.
 * Render the returned element, then `if (!(await confirm({...}))) return`.
 */
export function useConfirm(): [(options: ConfirmOptions) => Promise<boolean>, ReactNode] {
  const [pending, setPending] = useState<Pending | null>(null)

  const confirm = useCallback(
    (options: ConfirmOptions) =>
      new Promise<boolean>((resolve) => setPending({ ...options, resolve })),
    [],
  )

  function finish(confirmed: boolean) {
    pending?.resolve(confirmed)
    setPending(null)
  }

  const element = (
    <Dialog
      open={pending !== null}
      title={pending?.title ?? ''}
      size="sm"
      onClose={() => finish(false)}
      footer={
        <>
          <Button onClick={() => finish(false)}>Cancel</Button>
          <Button variant={pending?.tone === 'primary' ? 'primary' : 'danger'} onClick={() => finish(true)}>
            {pending?.confirmLabel}
          </Button>
        </>
      }
    >
      <div className="text-sm text-slate-700">{pending?.message}</div>
    </Dialog>
  )

  return [confirm, element]
}
