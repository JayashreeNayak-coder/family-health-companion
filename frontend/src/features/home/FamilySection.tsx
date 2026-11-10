import { Mail, Settings, UserPlus, Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import { familyPhotoUrl, getFamily, type Family, type FamilySummary } from '../../api/families'
import type { Profile } from '../../api/profiles'
import { Avatar } from '../../components/Avatar'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { EmptyState, ErrorNote } from '../../components/Feedback'
import { useToast } from '../../components/Toast'
import { initials } from '../shell/initials'
import { AddProfileForm, InviteForm } from './FamilyForms'
import { ManageFamilyDialog } from './ManageFamilyDialog'
import { ProfileCard } from './ProfileCard'

type Props = {
  summary: FamilySummary
  /** The profiles in this family that this account can open. */
  profiles: Profile[]
  onChanged: () => void
}

function roleLabel(role: FamilySummary['role']) {
  return role === 'MANAGER' ? 'Manager' : 'Member'
}

/** One family on Home: its people, its members, and adding or inviting someone. */
export function FamilySection({ summary, profiles, onChanged }: Props) {
  const [family, setFamily] = useState<Family | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [dialog, setDialog] = useState<'profile' | 'invite' | 'manage' | null>(null)
  const toast = useToast()
  const isManager = summary.role === 'MANAGER'

  useEffect(() => {
    let ignore = false
    getFamily(summary.id).then(
      (loaded) => {
        if (!ignore) {
          setFamily(loaded)
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
  }, [summary.id, profiles])

  const offerable = profiles.filter((profile) => !profile.linkedToAccount && profile.accessLevel === 'MANAGE')
  const headingId = `family-${summary.id}`

  return (
    <section aria-labelledby={headingId} className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <Avatar
            name={summary.name}
            photoUrl={familyPhotoUrl(summary)}
            className="size-9 bg-slate-200 text-sm font-semibold text-slate-700"
          />
          <h2 id={headingId} className="text-lg font-semibold text-slate-900">
            {summary.name}
          </h2>
          <Badge tone={isManager ? 'brand' : 'neutral'}>You are a {roleLabel(summary.role).toLowerCase()}</Badge>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button icon={UserPlus} onClick={() => setDialog('profile')}>
            Add a person
          </Button>
          {isManager && (
            <Button icon={Mail} onClick={() => setDialog('invite')}>
              Invite someone
            </Button>
          )}
          {isManager && (
            <Button icon={Settings} onClick={() => setDialog('manage')}>
              Manage family
            </Button>
          )}
        </div>
      </div>

      {profiles.length === 0 ? (
        <EmptyState
          icon={Users}
          title="No profiles in this family that you can see"
        >
          Use “Add a person” for yourself or someone you look after. Only profiles you have been
          given access to appear here.
        </EmptyState>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {profiles.map((profile) => (
            <li key={profile.id}>
              <ProfileCard profile={profile} />
            </li>
          ))}
        </ul>
      )}

      <ErrorNote message={error} />
      {family && (
        <div className="rounded-xl border border-slate-200 bg-white">
          <h3 className="border-b border-slate-200 px-4 py-3 text-sm font-semibold text-slate-700">
            Family members · {family.members.length}
          </h3>
          <ul className="divide-y divide-slate-100">
            {family.members.map((member) => (
              <li key={member.accountId} className="flex items-center gap-3 px-4 py-3">
                <span
                  aria-hidden="true"
                  className="grid size-8 shrink-0 place-items-center rounded-full bg-slate-100 text-xs font-semibold text-slate-600"
                >
                  {initials(member.displayName)}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-slate-900">{member.displayName}</span>
                  <span className="block truncate text-xs text-slate-500">{member.email}</span>
                </span>
                <Badge tone={member.role === 'MANAGER' ? 'brand' : 'neutral'}>{roleLabel(member.role)}</Badge>
              </li>
            ))}
          </ul>
        </div>
      )}

      <Dialog open={dialog === 'profile'} title={`Add a person to ${summary.name}`} onClose={() => setDialog(null)}>
        <AddProfileForm
          familyId={summary.id}
          isManager={isManager}
          onCreated={(profile) => {
            setDialog(null)
            toast(`${profile.fullName} added`)
            onChanged()
          }}
        />
      </Dialog>
      <Dialog open={dialog === 'invite'} title={`Invite someone to ${summary.name}`} onClose={() => setDialog(null)}>
        <InviteForm familyId={summary.id} offerable={offerable} />
      </Dialog>
      {isManager && dialog === 'manage' && (
        <ManageFamilyDialog
          open
          summary={summary}
          memberCount={family?.members.length ?? 1}
          profileCount={profiles.length}
          onClose={() => setDialog(null)}
          onChanged={onChanged}
        />
      )}
    </section>
  )
}
