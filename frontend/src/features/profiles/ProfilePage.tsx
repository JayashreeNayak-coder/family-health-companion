import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import { getProfile, type Profile } from '../../api/profiles'
import { ChevronRight, Pencil } from 'lucide-react'
import { Badge } from '../../components/Badge'
import { buttonClass } from '../../components/Button'
import { useDocumentTitle } from '../../components/useDocumentTitle'
import { ErrorNote, Loading } from '../../components/Feedback'
import { Link, navigate, profilePath, type ProfileSection } from '../../router'
import { ACCESS_BADGE, ageText } from '../home/ProfileCard'
import { initials } from '../shell/initials'
import { useNavProfiles } from '../shell/NavProfiles'
import { DocumentsPanel } from '../documents/DocumentsPanel'
import { MedicationsPanel } from '../medication/MedicationsPanel'
import { LabTrendsPanel } from '../records/LabTrendsPanel'
import { RecordSection } from '../records/RecordSection'
import {
  ALLERGIES,
  CONDITIONS,
  CONTACTS,
  DIETARY,
  FOOD_LOG,
  LAB_RESULTS,
  SYMPTOMS,
  VISITS,
  type ResourceConfig,
} from '../records/resources'
import { TimelinePanel } from '../records/TimelinePanel'
import { AccessPanel } from './AccessPanel'
import { DetailsPanel } from './DetailsPanel'
import { OverviewPanel } from './OverviewPanel'

type Tab = { id: ProfileSection; label: string }

const BASE_TABS: Tab[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'details', label: 'Details' },
  { id: 'history', label: 'Health history' },
  { id: 'visits', label: 'Visits and symptoms' },
  { id: 'medications', label: 'Medications' },
  { id: 'labs', label: 'Lab results' },
  { id: 'food', label: 'Food log' },
  { id: 'documents', label: 'Documents' },
  { id: 'timeline', label: 'Timeline' },
]

const SECTIONS: Record<string, ResourceConfig[]> = {
  history: [CONDITIONS, ALLERGIES, DIETARY, CONTACTS],
  visits: [VISITS, SYMPTOMS],
  labs: [LAB_RESULTS],
  food: [FOOD_LOG],
}

type Props = {
  profileId: string
  /** The open tab, from the page address. */
  section: ProfileSection
}

export function ProfilePage({ profileId, section: tab }: Props) {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [error, setError] = useState<string | null>(null)
  const { reload: reloadNav } = useNavProfiles()

  const load = useCallback(() => {
    return getProfile(profileId).then(setProfile, (failure) => setError(errorMessage(failure)))
  }, [profileId])

  useEffect(() => {
    void load()
  }, [load])

  const tabs = BASE_TABS.concat(
    profile?.accessLevel === 'MANAGE' ? [{ id: 'access', label: 'Access' }] : [],
  )

  // The Access tab exists only for managers. Anyone else at its address sees the overview.
  const shown: ProfileSection = profile && !tabs.some((item) => item.id === tab) ? 'overview' : tab

  useDocumentTitle(profile?.fullName ?? 'Profile', tabs.find((item) => item.id === shown)?.label)

  function handleSaved(saved: Profile) {
    setProfile(saved)
    reloadNav()
  }

  function handleDeleted() {
    reloadNav()
    navigate('/', { replace: true })
  }

  return (
    <div className="space-y-6">
      <nav aria-label="Breadcrumb">
        <ol className="flex min-w-0 items-center gap-1 text-sm text-slate-600">
          <li>
            <Link to="/" className="rounded hover:text-brand-700 hover:underline">
              Home
            </Link>
          </li>
          <li className="flex min-w-0 items-center gap-1">
            <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-slate-400" />
            <span className="truncate font-medium text-slate-800" aria-current="page">
              {profile?.fullName ?? 'Profile'}
            </span>
          </li>
        </ol>
      </nav>
      <ErrorNote message={error} />
      {!profile && !error && <Loading />}
      {profile && (
        <>
          <ProfileHeader profile={profile} onDetails={shown === 'details'} />
          <nav aria-label="Profile sections" className="-mx-4 overflow-x-auto px-4 sm:mx-0 sm:px-0">
            <ul className="flex min-w-max border-b border-slate-200">
              {tabs.map((item) => {
                const active = shown === item.id
                return (
                  <li key={item.id}>
                    <Link
                      to={profilePath(profile.id, item.id)}
                      aria-current={active ? 'page' : undefined}
                      className={`-mb-px block border-b-2 px-2.5 py-2.5 text-sm font-medium whitespace-nowrap transition-colors ${
                        active
                          ? 'border-brand-600 text-brand-800'
                          : 'border-transparent text-slate-600 hover:border-slate-300 hover:text-slate-900'
                      }`}
                    >
                      {item.label}
                    </Link>
                  </li>
                )
              })}
            </ul>
          </nav>
          {shown === 'overview' && <OverviewPanel profileId={profile.id} />}
          {shown === 'details' && (
            <DetailsPanel profile={profile} onSaved={handleSaved} onDeleted={handleDeleted} />
          )}
          {SECTIONS[shown]?.map((resource) => (
            <RecordSection
              key={resource.path}
              profileId={profile.id}
              canEdit={profile.accessLevel !== 'VIEW'}
              resource={resource}
            />
          ))}
          {shown === 'medications' && (
            <MedicationsPanel profileId={profile.id} canEdit={profile.accessLevel !== 'VIEW'} />
          )}
          {shown === 'labs' && <LabTrendsPanel profileId={profile.id} />}
          {shown === 'documents' && (
            <DocumentsPanel
              profileId={profile.id}
              canEdit={profile.accessLevel !== 'VIEW'}
              canManage={profile.accessLevel === 'MANAGE'}
            />
          )}
          {shown === 'timeline' && <TimelinePanel profileId={profile.id} />}
          {shown === 'access' && <AccessPanel profile={profile} />}
        </>
      )}
    </div>
  )
}

function ProfileHeader({ profile, onDetails }: { profile: Profile; onDetails: boolean }) {
  const age = ageText(profile.dateOfBirth)
  const access = ACCESS_BADGE[profile.accessLevel]

  return (
    <header className="flex flex-wrap items-center gap-4">
      <span
        aria-hidden="true"
        className="grid size-14 shrink-0 place-items-center rounded-full bg-brand-100 text-lg font-semibold text-brand-800 sm:size-16 sm:text-xl"
      >
        {initials(profile.fullName)}
      </span>
      <div className="min-w-48 flex-1">
        <h1 className="text-2xl font-semibold break-words text-slate-900">{profile.fullName}</h1>
        <p className="mt-0.5 text-sm text-slate-600">
          {profile.dateOfBirth ? (
            <>
              {age && <span className="whitespace-nowrap">{age} · </span>}
              <span className="whitespace-nowrap">Born {profile.dateOfBirth}</span>
            </>
          ) : (
            'Date of birth not recorded'
          )}
        </p>
        <div className="mt-2 flex flex-wrap gap-1.5">
          {profile.dependent && <Badge tone="review">Dependent</Badge>}
          {profile.linkedToAccount && <Badge>Has own account</Badge>}
          <Badge tone={access.tone}>{access.label}</Badge>
        </div>
      </div>
      {profile.accessLevel !== 'VIEW' && !onDetails && (
        <Link to={profilePath(profile.id, 'details')} className={buttonClass('secondary', 'sm')}>
          <Pencil aria-hidden="true" className="size-4" />
          Edit details
        </Link>
      )}
    </header>
  )
}
