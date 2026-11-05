import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import { listFamilies, type FamilySummary } from '../../api/families'
import { listProfiles, type Profile } from '../../api/profiles'
import { Card, ErrorNote } from '../../components/ui'
import { ProfilePage } from '../profiles/ProfilePage'
import { CreateFamilyForm, JoinFamilyForm } from './FamilyForms'
import { FamilyCard } from './FamilyCard'

type Data = { families: FamilySummary[]; profiles: Profile[] }

export function Dashboard() {
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)

  const refresh = useCallback(() => setRevision((current) => current + 1), [])

  useEffect(() => {
    let ignore = false
    Promise.all([listFamilies(), listProfiles()]).then(
      ([families, profiles]) => {
        if (!ignore) {
          setData({ families: families.items, profiles: profiles.items })
          setError(null)
        }
      },
      (failure) => {
        if (!ignore) {
          setError(errorMessage(failure))
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [revision])

  if (selectedId) {
    return (
      <ProfilePage
        profileId={selectedId}
        onBack={() => {
          setSelectedId(null)
          refresh()
        }}
      />
    )
  }
  if (error && !data) {
    return <ErrorNote message={error} />
  }
  if (!data) {
    return <p role="status">Loading…</p>
  }

  return (
    <div className="space-y-4">
      <ErrorNote message={error} />
      <Card title="Health profiles">
        {data.profiles.length === 0 ? (
          <p className="text-sm text-slate-600">
            No profiles yet. Only profiles you have been given access to appear here.
          </p>
        ) : (
          <ul className="divide-y divide-slate-200">
            {data.profiles.map((profile) => (
              <li key={profile.id} className="flex items-center justify-between py-2">
                <button
                  type="button"
                  onClick={() => setSelectedId(profile.id)}
                  className="font-medium text-teal-800 underline"
                >
                  {profile.fullName}
                </button>
                <span className="text-sm text-slate-600">
                  {profile.dependent ? 'Dependent · ' : ''}
                  {profile.accessLevel.toLowerCase()} access
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {data.families.map((family) => (
        <FamilyCard key={family.id} summary={family} profiles={data.profiles} onChanged={refresh} />
      ))}

      <div className="grid gap-4 md:grid-cols-2">
        <Card title="Create a family">
          <CreateFamilyForm onCreated={refresh} />
        </Card>
        <Card title="Join a family">
          <JoinFamilyForm onJoined={refresh} />
        </Card>
      </div>
    </div>
  )
}
