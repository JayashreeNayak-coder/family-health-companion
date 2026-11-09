import { HousePlus, KeyRound } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import { listFamilies, type FamilySummary } from '../../api/families'
import { listProfiles, type Profile } from '../../api/profiles'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import { Dialog } from '../../components/Dialog'
import { ErrorNote, Loading } from '../../components/Feedback'
import { useNavProfiles } from '../shell/NavProfiles'
import { CreateFamilyForm, JoinFamilyForm } from './FamilyForms'
import { FamilySection } from './FamilySection'
import { ProfileCard } from './ProfileCard'

type Data = { families: FamilySummary[]; profiles: Profile[] }

/** The family overview on Home: each family's people and members. */
export function Dashboard() {
  const [data, setData] = useState<Data | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [revision, setRevision] = useState(0)
  const [dialog, setDialog] = useState<'create' | 'join' | null>(null)
  const { reload: reloadNav } = useNavProfiles()

  const refresh = useCallback(() => {
    setDialog(null)
    setRevision((current) => current + 1)
    reloadNav()
  }, [reloadNav])

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

  if (error && !data) {
    return <ErrorNote message={error} />
  }
  if (!data) {
    return <Loading lines={4} />
  }

  const familyIds = new Set(data.families.map((family) => family.id))
  // Profiles shared with this account from a family it isn't a member of.
  const shared = data.profiles.filter((profile) => !familyIds.has(profile.familyId))

  if (data.families.length === 0 && shared.length === 0) {
    return (
      <div className="space-y-4">
        <ErrorNote message={error} />
        <div className="rounded-xl border border-brand-100 bg-brand-50 px-5 py-4 text-sm text-brand-900">
          <p className="font-medium">No profiles yet.</p>
          <p className="mt-1">
            Start a family to keep records for yourself and the people you look after, or join one
            with a code from a family manager.
          </p>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          <Card title="Create a family" description="You become its manager.">
            <CreateFamilyForm onCreated={refresh} />
          </Card>
          <Card title="Join a family" description="Someone in the family invited you.">
            <JoinFamilyForm onJoined={refresh} />
          </Card>
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-10">
      <ErrorNote message={error} />

      {data.families.map((family) => (
        <FamilySection
          key={family.id}
          summary={family}
          profiles={data.profiles.filter((profile) => profile.familyId === family.id)}
          onChanged={refresh}
        />
      ))}

      {shared.length > 0 && (
        <section aria-labelledby="shared-profiles" className="space-y-4">
          <h2 id="shared-profiles" className="text-lg font-semibold text-slate-900">
            Shared with you
          </h2>
          <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {shared.map((profile) => (
              <li key={profile.id}>
                <ProfileCard profile={profile} />
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="flex flex-wrap items-center gap-2 border-t border-slate-200 pt-6">
        <span className="mr-2 text-sm text-slate-600">Another family?</span>
        <Button icon={HousePlus} onClick={() => setDialog('create')}>
          Create a family
        </Button>
        <Button icon={KeyRound} onClick={() => setDialog('join')}>
          Join with a code
        </Button>
      </div>

      <Dialog open={dialog === 'create'} title="Create a family" onClose={() => setDialog(null)}>
        <CreateFamilyForm onCreated={refresh} />
      </Dialog>
      <Dialog open={dialog === 'join'} title="Join a family" onClose={() => setDialog(null)}>
        <JoinFamilyForm onJoined={refresh} />
      </Dialog>
    </div>
  )
}
