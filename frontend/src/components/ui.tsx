/**
 * The original small UI kit, kept as a thin layer over the design system during the redesign
 * (UI-1). Pages still importing these get the new look; later phases move them to the components
 * directly, and this file goes once nothing uses it.
 */
import { buttonClass } from './Button'

export { Card } from './Card'
export { EmptyState, ErrorNote, Loading, Missing } from './Feedback'

export const inputClass =
  'mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-base text-slate-900 shadow-xs placeholder:text-slate-400 focus:border-brand-600 focus:ring-2 focus:ring-brand-600/20 focus:outline-none disabled:bg-slate-100 sm:text-sm'

/** For a compact select inside a row, such as an access level. */
export const compactSelectClass =
  'rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm text-slate-900 shadow-xs focus:border-brand-600 focus:ring-2 focus:ring-brand-600/20 focus:outline-none'

export const primaryButtonClass = buttonClass('primary', 'md')

export const secondaryButtonClass = buttonClass('secondary', 'sm')

export const dangerButtonClass = buttonClass('danger', 'sm')
