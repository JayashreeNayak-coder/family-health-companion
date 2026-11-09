import type { LucideIcon } from 'lucide-react'
import { CalendarDays, FileText, FlaskConical, HeartPulse, Pill, Thermometer } from 'lucide-react'
import { useEffect, useState } from 'react'
import { errorMessage } from '../../api/client'
import { getTimeline, type Timeline, type TimelineEntry, type TimelineType } from '../../api/timeline'
import { SourceBadge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import { EmptyState, ErrorNote, Loading } from '../../components/Feedback'
import { inputClass } from '../../components/formStyles'
import { TogglePill } from '../../components/TogglePill'

const TYPES: { value: TimelineType; label: string; singular: string; icon: LucideIcon }[] = [
  { value: 'CONDITION', label: 'Conditions', singular: 'Condition', icon: HeartPulse },
  { value: 'VISIT', label: 'Visits', singular: 'Visit', icon: CalendarDays },
  { value: 'SYMPTOM', label: 'Symptoms', singular: 'Symptom', icon: Thermometer },
  { value: 'MEDICATION', label: 'Medications', singular: 'Medication', icon: Pill },
  { value: 'LAB_RESULT', label: 'Lab results', singular: 'Lab result', icon: FlaskConical },
]

/** A type this screen doesn't know yet is shown neutrally, never as another type. */
const UNKNOWN_TYPE = { label: 'Records', singular: 'Record', icon: FileText }

function typeOf(type: TimelineType) {
  return TYPES.find((candidate) => candidate.value === type) ?? UNKNOWN_TYPE
}

/** "March 2026" from 2026-03-14, without time zone shifts. */
export function monthLabel(date: string): string {
  const [year, month] = date.split('-').map(Number)
  return new Date(year, month - 1, 1).toLocaleDateString('en-GB', { month: 'long', year: 'numeric' })
}

/** Splits dated entries, already newest first, into runs that share a month. */
export function byMonth(entries: TimelineEntry[]): { month: string; entries: TimelineEntry[] }[] {
  const groups: { month: string; entries: TimelineEntry[] }[] = []
  for (const entry of entries) {
    const month = entry.date ? monthLabel(entry.date) : 'Date unknown'
    const last = groups[groups.length - 1]
    if (last && last.month === month) {
      last.entries.push(entry)
    } else {
      groups.push({ month, entries: [entry] })
    }
  }
  return groups
}

function Entry({ entry }: { entry: TimelineEntry }) {
  const type = typeOf(entry.type)
  const Icon = type.icon
  return (
    <li className="relative pb-5 pl-11 last:pb-0">
      <span
        aria-hidden="true"
        className="absolute top-0 left-0 grid size-8 place-items-center rounded-full bg-white text-brand-700 ring-1 ring-slate-200"
      >
        <Icon className="size-4" />
      </span>
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1 pt-1">
        <span className="text-xs font-semibold tracking-wide text-slate-500 uppercase">{type.singular}</span>
        {entry.date && (
          <span className="text-xs text-slate-500 tabular-nums">
            {entry.date}
            {entry.time ? ` ${entry.time}` : ''}
          </span>
        )}
      </div>
      <div className="mt-0.5 flex flex-wrap items-center gap-2">
        <p className="font-medium break-words text-slate-900">{entry.title}</p>
        <SourceBadge sourceType={entry.sourceType} sourceDocumentDeletedAt={entry.sourceDocumentDeletedAt} />
      </div>
      {entry.detail && <p className="mt-0.5 text-sm break-words text-slate-600">{entry.detail}</p>}
    </li>
  )
}

function EntryList({ entries }: { entries: TimelineEntry[] }) {
  return (
    <ol className="relative before:absolute before:top-2 before:bottom-2 before:left-4 before:w-px before:bg-slate-200">
      {entries.map((entry) => (
        <Entry key={`${entry.type}-${entry.id}`} entry={entry} />
      ))}
    </ol>
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

  const filtered = from !== '' || to !== '' || types.length > 0

  return (
    <Card title="Timeline" description="Conditions, visits, symptoms, medications, and lab results, newest first.">
      <div className="space-y-3 rounded-xl bg-slate-50 p-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm font-medium text-slate-800">
            From
            <input type="date" className={inputClass} value={from} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="block text-sm font-medium text-slate-800">
            To
            <input type="date" className={inputClass} value={to} onChange={(e) => setTo(e.target.value)} />
          </label>
        </div>
        <fieldset className="flex flex-wrap items-center gap-2">
          <legend className="mb-2 text-sm font-medium text-slate-800">
            Show only <span className="font-normal text-slate-500">(none selected shows all)</span>
          </legend>
          {TYPES.map((type) => (
            <TogglePill
              key={type.value}
              label={type.label}
              checked={types.includes(type.value)}
              onChange={() => toggle(type.value)}
            />
          ))}
          {filtered && (
            <Button
              variant="ghost"
              onClick={() => {
                setFrom('')
                setTo('')
                setTypes([])
              }}
            >
              Clear filters
            </Button>
          )}
        </fieldset>
      </div>

      <div className="mt-5">
        <ErrorNote message={error} />
        {!timeline && !error && <Loading />}
        {timeline && timeline.entries.length === 0 && timeline.dateUnknown.length === 0 && (
          <EmptyState icon={CalendarDays} title="No records to show.">
            {filtered ? 'Nothing matches these filters.' : 'Records with a date appear here as they are added.'}
          </EmptyState>
        )}
        {timeline && timeline.entries.length > 0 && (
          <div className="space-y-6">
            {byMonth(timeline.entries).map((group) => (
              <section key={group.month} aria-label={group.month}>
                <h4 className="mb-3 text-sm font-semibold text-slate-700">{group.month}</h4>
                <EntryList entries={group.entries} />
              </section>
            ))}
          </div>
        )}
        {timeline && timeline.dateUnknown.length > 0 && (
          <section className="mt-6 border-t border-slate-200 pt-5">
            <h4 className="mb-3 text-sm font-semibold text-slate-700">Date unknown</h4>
            <EntryList entries={timeline.dateUnknown} />
          </section>
        )}
      </div>
    </Card>
  )
}
