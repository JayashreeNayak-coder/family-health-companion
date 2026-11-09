import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import { getTimeline, type Timeline, type TimelineEntry, type TimelineType } from '../../api/timeline'
import { Card, ErrorNote, inputClass } from '../../components/ui'
import { SourceBadge } from '../../components/Badge'

const TYPES: { value: TimelineType; label: string }[] = [
  { value: 'CONDITION', label: 'Conditions' },
  { value: 'VISIT', label: 'Visits' },
  { value: 'SYMPTOM', label: 'Symptoms' },
  { value: 'MEDICATION', label: 'Medications' },
]

function typeLabel(type: TimelineType) {
  return TYPES.find((candidate) => candidate.value === type)?.label.replace(/s$/, '') ?? type
}

function Entry({ entry }: { entry: TimelineEntry }) {
  return (
    <li className="py-2">
      <div className="flex flex-wrap items-center gap-2">
        {entry.date && (
          <span className="text-sm text-slate-600">
            {entry.date}
            {entry.time ? ` ${entry.time}` : ''}
          </span>
        )}
        <span className="text-xs font-medium text-teal-800 uppercase">{typeLabel(entry.type)}</span>
        <strong>{entry.title}</strong>
        <SourceBadge
          sourceType={entry.sourceType}
          sourceDocumentDeletedAt={entry.sourceDocumentDeletedAt}
        />
      </div>
      {entry.detail && <p className="text-sm text-slate-700">{entry.detail}</p>}
    </li>
  )
}

export function TimelinePanel({ profileId }: { profileId: string }) {
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [types, setTypes] = useState<TimelineType[]>([])
  const [timeline, setTimeline] = useState<Timeline | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let ignore = false
    getTimeline(profileId, { from, to, types }).then(
      (loaded) => {
        if (!ignore) {
          setTimeline(loaded)
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
  }, [profileId, from, to, types])

  function toggle(type: TimelineType) {
    setTypes((current) =>
      current.includes(type) ? current.filter((item) => item !== type) : [...current, type],
    )
  }

  return (
    <Card title="Timeline">
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm font-medium">
          From
          <input type="date" className={inputClass} value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label className="block text-sm font-medium">
          To
          <input type="date" className={inputClass} value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
      </div>
      <fieldset className="mt-3 flex flex-wrap gap-3 text-sm">
        <legend className="font-medium">Show only (none selected shows all)</legend>
        {TYPES.map((type) => (
          <label key={type.value} className="flex items-center gap-1">
            <input
              type="checkbox"
              checked={types.includes(type.value)}
              onChange={() => toggle(type.value)}
            />
            {type.label}
          </label>
        ))}
      </fieldset>

      <ErrorNote message={error} />
      {!timeline && !error && <p role="status">Loading…</p>}
      {timeline && timeline.entries.length === 0 && timeline.dateUnknown.length === 0 && (
        <p className="mt-3 text-sm text-slate-600">No records to show.</p>
      )}
      {timeline && timeline.entries.length > 0 && (
        <ul className="mt-3 divide-y divide-slate-200">
          {timeline.entries.map((entry) => (
            <Entry key={`${entry.type}-${entry.id}`} entry={entry} />
          ))}
        </ul>
      )}
      {timeline && timeline.dateUnknown.length > 0 && (
        <section className="mt-4">
          <h4 className="font-medium">Date unknown</h4>
          <ul className="divide-y divide-slate-200">
            {timeline.dateUnknown.map((entry) => (
              <Entry key={`${entry.type}-${entry.id}`} entry={entry} />
            ))}
          </ul>
        </section>
      )}
    </Card>
  )
}
