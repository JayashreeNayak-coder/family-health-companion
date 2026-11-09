import type { LucideIcon } from 'lucide-react'
import { Loader2 } from 'lucide-react'
import type { ButtonHTMLAttributes, ReactNode } from 'react'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
export type ButtonSize = 'sm' | 'md'

const base =
  'inline-flex items-center justify-center gap-2 rounded-lg font-medium whitespace-nowrap transition-colors disabled:cursor-not-allowed disabled:opacity-60'

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-brand-700 text-white shadow-xs hover:bg-brand-800',
  secondary: 'border border-slate-300 bg-white text-slate-800 shadow-xs hover:bg-slate-50',
  ghost: 'text-slate-700 hover:bg-slate-100',
  danger: 'border border-red-200 bg-white text-red-700 shadow-xs hover:bg-red-50',
}

const sizes: Record<ButtonSize, string> = {
  sm: 'min-h-9 px-3 text-sm',
  md: 'min-h-10 px-4 text-sm',
}

/** The classes for a button look, for elements that can't be a Button, such as links. */
export function buttonClass(variant: ButtonVariant = 'secondary', size: ButtonSize = 'sm'): string {
  return `${base} ${variants[variant]} ${sizes[size]}`
}

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  size?: ButtonSize
  icon?: LucideIcon
  /** Shows a spinner and disables the button while an action runs. */
  busy?: boolean
  children?: ReactNode
}

export function Button({
  variant = 'secondary',
  size = 'sm',
  icon: Icon,
  busy = false,
  disabled,
  className = '',
  type = 'button',
  children,
  ...rest
}: Props) {
  return (
    <button
      type={type}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      className={`${buttonClass(variant, size)} ${className}`}
      {...rest}
    >
      {busy ? (
        <Loader2 aria-hidden="true" className="size-4 animate-spin" />
      ) : (
        Icon && <Icon aria-hidden="true" className="size-4" />
      )}
      {children}
    </button>
  )
}
