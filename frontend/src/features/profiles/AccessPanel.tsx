import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import { getFamily, type FamilyMember } from '../../api/families'
import { deleteGrant, listGrants, putGrant, type AccessLevel, type Grant, type Profile } from '../../api/profiles'
import { Card, compactSelectClass, dangerButtonClass, ErrorNote, inputClass } from '../../components/ui'

const LEVELS: { value: AccessLevel; label: string }[] = [
  { value: 'VIEW', label: 'View' },
  { value: 'EDIT', label: 'Edit' },
  { value: 'MANAGE', label: 'Manage' },
]

export function AccessPanel({ profile }: { profile: Profile }) {
  const [grants, setGrants] = useState<Grant[] | null>(null)
  const [members, setMembers] = useState<FamilyMember[]>([])
  const [error, setError] = useState<string | null>(null)

  const [revision, setRevision] = useState(0)

  useEffect(() => {
    let ignore = false
    Promise.all([listGrants(profile.id), getFamily(profile.familyId)]).then(
      ([loadedGrants, family]) => {
        if (!ignore) {
          setGrants(loadedGrants)
          setMembers(family.members)
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
  }, [profile.id, profile.familyId, revision])

  async function run(action: () => Promise<unknown>) {
    setError(null)
    try {
      await action()
      setRevision((current) => current + 1)
    } catch (failure) {
      setError(errorMessage(failure))
    }
  }

  if (!grants) {
    return error ? <ErrorNote message={error} /> : <p role="status">Loading…</p>
  }

  const others = members.filter((member) => !grants.some((g) => g.accountId === member.accountId))

  return (
    <Card title="Who can see this profile">
      <p className="text-sm text-slate-600">
        Being in the family gives no access. Only the people listed here can see these records.
      </p>
      <ErrorNote message={error} />
      <ul className="mt-3 divide-y divide-slate-200">
        {grants.map((grant) => (
          <li key={grant.accountId} className="flex flex-wrap items-center justify-between gap-2 py-2">
            <span>
              {grant.displayName} <span className="text-sm text-slate-600">({grant.email})</span>
            </span>
            {grant.linkedAccount ? (
              <span className="text-sm text-slate-600">Owner · manage</span>
            ) : (
              <span className="flex items-center gap-2">
                <select
                  aria-label={`Access for ${grant.displayName}`}
                  className={compactSelectClass}
                  value={grant.level}
                  onChange={(event) =>
                    void run(() =>
                      putGrant(profile.id, grant.accountId, event.target.value as AccessLevel),
                    )
                  }
                >
                  {LEVELS.map((level) => (
                    <option key={level.value} value={level.value}>
                      {level.label}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className={dangerButtonClass}
                  onClick={() => void run(() => deleteGrant(profile.id, grant.accountId))}
                >
                  Remove
                </button>
              </span>
            )}
          </li>
        ))}
      </ul>
      {others.length > 0 && (
        <label className="mt-4 block text-sm font-medium">
          Give view access to
          <select
            className={inputClass}
            value=""
            onChange={(event) => void run(() => putGrant(profile.id, event.target.value, 'VIEW'))}
          >
            <option value="">Choose a family member</option>
            {others.map((member) => (
              <option key={member.accountId} value={member.accountId}>
                {member.displayName}
              </option>
            ))}
          </select>
        </label>
      )}
    </Card>
  )
}
