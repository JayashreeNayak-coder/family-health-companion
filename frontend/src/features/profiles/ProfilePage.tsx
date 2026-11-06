import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import { getProfile, type Profile } from '../../api/profiles'
import { ErrorNote, secondaryButtonClass } from '../../components/ui'
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

type Tab = { id: string; label: string }

const BASE_TABS: Tab[] = [
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
  onBack: () => void
}

export function ProfilePage({ profileId, onBack }: Props) {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState('details')

  const load = useCallback(() => {
    return getProfile(profileId).then(setProfile, (failure) => setError(errorMessage(failure)))
  }, [profileId])

  useEffect(() => {
    void load()
  }, [load])

  const tabs = BASE_TABS.concat(
    profile?.accessLevel === 'MANAGE' ? [{ id: 'access', label: 'Access' }] : [],
  )

  return (
    <div className="space-y-4">
      <button type="button" onClick={onBack} className={secondaryButtonClass}>
        ← All profiles
      </button>
      <ErrorNote message={error} />
      {!profile && !error && <p role="status">Loading…</p>}
      {profile && (
        <>
          <h2 className="text-2xl font-semibold">{profile.fullName}</h2>
          <nav aria-label="Profile sections" className="flex flex-wrap gap-2">
            {tabs.map((item) => (
              <button
                key={item.id}
                type="button"
                aria-current={tab === item.id ? 'page' : undefined}
                onClick={() => setTab(item.id)}
                className={
                  tab === item.id
                    ? 'rounded bg-teal-700 px-3 py-1.5 text-sm font-medium text-white'
                    : secondaryButtonClass
                }
              >
                {item.label}
              </button>
            ))}
          </nav>
          {tab === 'details' && (
            <DetailsPanel profile={profile} onSaved={setProfile} onDeleted={onBack} />
          )}
          {SECTIONS[tab]?.map((resource) => (
            <RecordSection
              key={resource.path}
              profileId={profile.id}
              canEdit={profile.accessLevel !== 'VIEW'}
              resource={resource}
            />
          ))}
          {tab === 'medications' && (
            <MedicationsPanel profileId={profile.id} canEdit={profile.accessLevel !== 'VIEW'} />
          )}
          {tab === 'labs' && <LabTrendsPanel profileId={profile.id} />}
          {tab === 'documents' && (
            <DocumentsPanel
              profileId={profile.id}
              canEdit={profile.accessLevel !== 'VIEW'}
              canManage={profile.accessLevel === 'MANAGE'}
            />
          )}
          {tab === 'timeline' && <TimelinePanel profileId={profile.id} />}
          {tab === 'access' && <AccessPanel profile={profile} />}
        </>
      )}
    </div>
  )
}
