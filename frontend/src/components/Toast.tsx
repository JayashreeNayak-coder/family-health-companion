import { CircleCheck } from 'lucide-react'
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'

type Toast = { id: number; message: string }

const ToastContext = createContext<(message: string) => void>(() => {})

/** Short confirmations such as "Saved". Without a provider, as in component tests, it does nothing. */
export function useToast(): (message: string) => void {
  return useContext(ToastContext)
}

let nextId = 0

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const remove = useCallback((id: number) => {
    setToasts((current) => current.filter((item) => item.id !== id))
  }, [])

  const show = useCallback((message: string) => {
    nextId += 1
    const toast = { id: nextId, message }
    setToasts((current) => [...current.slice(-2), toast])
  }, [])

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-4 z-[60] flex flex-col items-center gap-2 px-4 sm:inset-x-auto sm:right-4 sm:items-end"
      >
        {toasts.map((toast) => (
          <ToastItem key={toast.id} toast={toast} onDone={remove} />
        ))}
      </div>
    </ToastContext.Provider>
  )
}

function ToastItem({ toast, onDone }: { toast: Toast; onDone: (id: number) => void }) {
  useEffect(() => {
    const timer = window.setTimeout(() => onDone(toast.id), 3500)
    return () => window.clearTimeout(timer)
  }, [onDone, toast.id])

  return (
    <div
      role="status"
      className="pointer-events-auto flex items-center gap-2 rounded-lg bg-slate-900 px-4 py-2.5 text-sm text-white shadow-lg"
    >
      <CircleCheck aria-hidden="true" className="size-4 text-emerald-400" />
      {toast.message}
    </div>
  )
}
