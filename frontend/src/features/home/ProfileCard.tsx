import { ChevronRight } from 'lucide-react'
import type { AccessLevel, Profile } from '../../api/profiles'
import { Badge, type BadgeTone } from '../../components/Badge'
import { Link, profilePath } from '../../router'
import { initials } from '../shell/initials'

/** Age in whole years, or months under one year, from a YYYY-MM-DD date. Null if unknown. */
export function ageText(dateOfBirth: string | null, today: Date = new Date()): string | null {
  if (!dateOfBirth) {
    return null
  }
  const [year, month, day] = dateOfBirth.split('-').map(Number)
  if (!year || !month || !day) {
    return null
  }
  let months = (today.getFullYear() - year) * 12 + (today.getMonth() + 1 - month)
  if (today.getDate() < day) {
    months -= 1
  }
  if (months < 0) {
    return null
  }
  if (months < 12) {
    return months === 1 ? '1 month' : `${months} months`
  }
  const years = Math.floor(months / 12)
  return years === 1 ? '1 year' : `${years} years`
}

export const ACCESS_BADGE: Record<AccessLevel, { label: string; tone: BadgeTone }> = {
  MANAGE: { label: 'You manage', tone: 'brand' },
  EDIT: { label: 'You can edit', tone: 'info' },
  VIEW: { label: 'View only', tone: 'neutral' },
}

/** One person on Home. The whole card opens their profile. */
export function ProfileCard({ profile }: { profile: Profile }) {
  const age = ageText(profile.dateOfBirth)
  const access = ACCESS_BADGE[profile.accessLevel]

  return (
    <Link
      to={profilePath(profile.id)}
      aria-label={profile.fullName}
      className="group flex items-center gap-4 rounded-xl border border-slate-200 bg-white p-4 transition hover:border-brand-200 hover:shadow-sm"
    >
      <span
        aria-hidden="true"
        className="grid size-12 shrink-0 place-items-center rounded-full bg-brand-100 text-base font-semibold text-brand-800"
      >
        {initials(profile.fullName)}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate font-semibold text-slate-900 group-hover:text-brand-800">
          {profile.fullName}
        </span>
        <span className="mt-0.5 block text-sm text-slate-600">{age ?? 'Age not recorded'}</span>
        <span className="mt-2 flex flex-wrap gap-1.5">
          {profile.dependent && <Badge tone="review">Dependent</Badge>}
          {profile.linkedToAccount && <Badge>Has own account</Badge>}
          <Badge tone={access.tone}>{access.label}</Badge>
        </span>
      </span>
      <ChevronRight aria-hidden="true" className="size-5 shrink-0 text-slate-400 group-hover:text-brand-600" />
    </Link>
  )
}
