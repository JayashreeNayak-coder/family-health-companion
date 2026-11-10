import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import { listRecords, type RecordItem } from '../../api/records'
import { ErrorNote, Loading } from '../../components/Feedback'

type Loaded = { allergies: RecordItem[]; restrictions: RecordItem[]; conditions: RecordItem[] }

type Props = {
  profileId: string
  name: string
  conditionIds: string[]
  onConditionIdsChange: (ids: string[]) => void
  confirmed: boolean
  onConfirmedChange: (confirmed: boolean) => void
}

function FactList({ title, items }: { title: string; items: string[] }) {
  return (
    <div>
      <h4 className="text-sm font-semibold text-slate-900">{title}</h4>
      {items.length === 0 ? (
        <p className="mt-1 text-sm text-slate-500 italic">None recorded</p>
      ) : (
        <ul className="mt-1 list-disc space-y-0.5 pl-5 text-sm text-slate-700">
          {items.map((item, index) => (
            <li key={index}>{item}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * Shows exactly what health-aware mode will send for this person: allergies, restrictions, and
 * preferences as recorded, plus only the conditions ticked here. The user confirms each time.
 */
export function PersonFacts({
  profileId,
  name,
  conditionIds,
  onConditionIdsChange,
  confirmed,
  onConfirmedChange,
}: Props) {
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false
    Promise.all([
      listRecords(profileId, 'allergies'),
      listRecords(profileId, 'dietary-restrictions'),
      listRecords(profileId, 'conditions'),
    ]).then(
      ([allergies, restrictions, conditions]) => {
        if (!ignore) {
          setLoaded({ allergies: allergies.items, restrictions: restrictions.items, conditions: conditions.items })
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
  }, [profileId])

  if (error) {
    return <ErrorNote message={error} />
  }
  if (!loaded) {
    return <Loading lines={2} />
  }

  const byKind = (kind: string) =>
    loaded.restrictions.filter((item) => item.kind === kind).map((item) => String(item.description))

  function toggle(id: string) {
    onConditionIdsChange(conditionIds.includes(id) ? conditionIds.filter((item) => item !== id) : [...conditionIds, id])
  }

  return (
    <div className="space-y-4 rounded-lg border border-slate-200 bg-slate-50 p-4">
      <p className="text-sm text-slate-700">
        These are taken from {name}&rsquo;s records. Check them, and correct the records first if something is wrong.
      </p>
      <div className="grid gap-4 sm:grid-cols-3">
        <FactList title="Allergies" items={loaded.allergies.map((item) => String(item.allergen))} />
        <FactList title="Dietary restrictions" items={byKind('RESTRICTION')} />
        <FactList title="Food preferences" items={byKind('PREFERENCE')} />
      </div>
      <fieldset>
        <legend className="text-sm font-semibold text-slate-900">Health conditions</legend>
        <p className="mt-1 text-xs text-slate-500">
          None are sent unless you tick them. Tick one only if it matters for this meal.
        </p>
        {loaded.conditions.length === 0 ? (
          <p className="mt-2 text-sm text-slate-500 italic">None recorded</p>
        ) : (
          <ul className="mt-2 space-y-1">
            {loaded.conditions.map((condition) => (
              <li key={condition.id}>
                <label className="flex items-center gap-2 text-sm text-slate-800">
                  <input
                    type="checkbox"
                    className="size-4 accent-brand-600"
                    checked={conditionIds.includes(condition.id)}
                    onChange={() => toggle(condition.id)}
                  />
                  {String(condition.name)}
                </label>
              </li>
            ))}
          </ul>
        )}
      </fieldset>
      <label className="flex items-start gap-2 rounded-lg bg-violet-50 px-3 py-2 text-sm text-violet-900">
        <input
          type="checkbox"
          className="mt-0.5 size-4 shrink-0 accent-brand-600"
          checked={confirmed}
          onChange={(event) => onConfirmedChange(event.target.checked)}
        />
        <span>
          I understand that {name}&rsquo;s allergies, restrictions, preferences, and any conditions I tick are sent
          to an outside AI service. Use synthetic records only.
        </span>
      </label>
    </div>
  )
}
