import type { LucideIcon } from 'lucide-react'
import { CalendarDays, FlaskConical, HeartPulse, Phone, Pill, TriangleAlert, Utensils } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { errorMessage } from '../../api/client'
import { listRecords, type RecordItem } from '../../api/records'
import { Badge, SourceBadge } from '../../components/Badge'
import { ErrorNote, Loading } from '../../components/Feedback'
import { Link, profilePath, type ProfileSection } from '../../router'

type ListState = { items: RecordItem[] | null; error: string | null }

/** Loads one record list. Each panel loads on its own, so one failure doesn't hide the rest. */
function useRecords(profileId: string, path: string): ListState {
  const [state, setState] = useState<ListState>({ items: null, error: null })

  useEffect(() => {
    let ignore = false
    listRecords(profileId, path).then(
      (page) => {
        if (!ignore) {
          setState({ items: page.items, error: null })
        }
      },
      (failure) => {
        if (!ignore) {
          setState({ items: null, error: errorMessage(failure) })
        }
      },
    )
    return () => {
      ignore = true
    }
  }, [profileId, path])

  return state
}

function text(value: unknown): string | null {
  return value === null || value === undefined || value === '' ? null : String(value)
}

/** Joins the parts that were recorded, skipping missing ones. */
function joined(...parts: unknown[]): string | null {
  const present = parts.map(text).filter((part): part is string => part !== null)
  return present.length > 0 ? present.join(' · ') : null
}

type PanelProps<T> = {
  profileId: string
  title: string
  icon: LucideIcon
  section: ProfileSection
  linkLabel: string
  state: ListState
  /** Which items to show, from the loaded list. */
  select?: (items: RecordItem[]) => T[]
  empty: string
  render: (item: T) => ReactNode
  tone?: 'default' | 'alert'
  className?: string
}

function Panel<T extends RecordItem>({
  profileId,
  title,
  icon: Icon,
  section,
  linkLabel,
  state,
  select = (items) => items as T[],
  empty,
  render,
  tone = 'default',
  className = '',
}: PanelProps<T>) {
  const shown = state.items ? select(state.items) : null
  const headingId = `overview-${section}-${title.replaceAll(' ', '-').toLowerCase()}`

  return (
    <section
      aria-labelledby={headingId}
      className={`flex flex-col rounded-xl border bg-white ${
        tone === 'alert' ? 'border-amber-200' : 'border-slate-200'
      } ${className}`}
    >
      <div className="flex items-center gap-2 border-b border-slate-100 px-5 py-3.5">
        <span
          className={`grid size-7 place-items-center rounded-lg ${
            tone === 'alert' ? 'bg-amber-50 text-amber-700' : 'bg-brand-50 text-brand-700'
          }`}
        >
          <Icon aria-hidden="true" className="size-4" />
        </span>
        <h2 id={headingId} className="font-semibold text-slate-900">
          {title}
        </h2>
        {shown && shown.length > 0 && <span className="text-sm text-slate-500">{shown.length}</span>}
      </div>
      <div className="flex-1 px-5 py-4">
        <ErrorNote message={state.error} />
        {!state.items && !state.error && <Loading lines={2} />}
        {shown && shown.length === 0 && <p className="text-sm text-slate-500">{empty}</p>}
        {shown && shown.length > 0 && <ul className="space-y-3">{shown.map((item) => <li key={item.id}>{render(item)}</li>)}</ul>}
      </div>
      <div className="border-t border-slate-100 px-5 py-2.5">
        <Link to={profilePath(profileId, section)} className="text-sm font-medium text-brand-700 hover:underline">
          {linkLabel}
        </Link>
      </div>
    </section>
  )
}

function Line({ title, detail, item, badge }: { title: string; detail?: string | null; item: RecordItem; badge?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
      <div className="min-w-0">
        <p className="font-medium break-words text-slate-900">{title}</p>
        {detail && <p className="text-sm break-words text-slate-600">{detail}</p>}
      </div>
      <div className="flex flex-wrap gap-1.5">
        {badge}
        {item.sourceType && (
          <SourceBadge sourceType={item.sourceType} sourceDocumentDeletedAt={item.sourceDocumentDeletedAt} />
        )}
      </div>
    </div>
  )
}

const STATUS_LABEL: Record<string, string> = { ACTIVE: 'Active', UNKNOWN: 'Status unknown' }

/** The value and unit exactly as recorded. A missing unit stays missing, never guessed. */
export function labValue(item: RecordItem): string {
  const value = text(item.valueNumeric) ?? text(item.valueText)
  if (value === null) {
    return 'Value not recorded'
  }
  const unit = text(item.unit)
  return unit ? `${value} ${unit}` : value
}

/** Keeps the newest result for each test and unit. The list arrives newest first. */
export function latestPerTest(items: RecordItem[], limit = 5): RecordItem[] {
  const seen = new Set<string>()
  const latest: RecordItem[] = []
  for (const item of items) {
    const key = `${String(item.testName).trim().toLowerCase()}|${text(item.unit) ?? ''}`
    if (!seen.has(key)) {
      seen.add(key)
      latest.push(item)
    }
  }
  return latest.slice(0, limit)
}

/**
 * A read-only summary of what has been recorded for one person. It only shows records as
 * entered: it doesn't judge values or suggest anything (docs/06 section 8).
 */
export function OverviewPanel({ profileId }: { profileId: string }) {
  const allergies = useRecords(profileId, 'allergies')
  const conditions = useRecords(profileId, 'conditions')
  const medications = useRecords(profileId, 'medications')
  const dietary = useRecords(profileId, 'dietary-restrictions')
  const contacts = useRecords(profileId, 'emergency-contacts')
  const visits = useRecords(profileId, 'visits')
  const labs = useRecords(profileId, 'lab-results')

  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-600">
        A summary of what has been recorded. It isn't medical advice, and something missing here may
        simply not have been entered.
      </p>
      <div className="grid gap-4 lg:grid-cols-2">
        <Panel
          profileId={profileId}
          title="Allergies"
          icon={TriangleAlert}
          tone="alert"
          section="history"
          linkLabel="Health history"
          state={allergies}
          empty="No allergies recorded."
          render={(item) => (
            <Line item={item} title={String(item.allergen)} detail={joined(item.reactionAsRecorded, item.severityAsRecorded)} />
          )}
        />
        <Panel
          profileId={profileId}
          title="Current conditions"
          icon={HeartPulse}
          section="history"
          linkLabel="Health history"
          state={conditions}
          select={(items) => items.filter((item) => item.status !== 'RESOLVED')}
          empty="No current conditions recorded."
          render={(item) => (
            <Line
              item={item}
              title={String(item.name)}
              detail={text(item.onsetOn) && `Since ${String(item.onsetOn)}`}
              badge={<Badge tone={item.status === 'ACTIVE' ? 'info' : 'neutral'}>{STATUS_LABEL[String(item.status)] ?? 'Status unknown'}</Badge>}
            />
          )}
        />
        <Panel
          profileId={profileId}
          title="Active medications"
          icon={Pill}
          section="medications"
          linkLabel="Medications and doses"
          state={medications}
          select={(items) => items.filter((item) => item.status === 'ACTIVE')}
          empty="No active medications recorded."
          render={(item) => <Line item={item} title={String(item.name)} detail={joined(item.dosageAsRecorded, item.instructionsAsRecorded)} />}
        />
        <Panel
          profileId={profileId}
          title="Diet"
          icon={Utensils}
          section="history"
          linkLabel="Health history"
          state={dietary}
          empty="No dietary restrictions or preferences recorded."
          render={(item) => (
            <Line
              item={item}
              title={String(item.description)}
              badge={<Badge tone={item.kind === 'RESTRICTION' ? 'review' : 'neutral'}>{item.kind === 'RESTRICTION' ? 'Restriction' : 'Preference'}</Badge>}
            />
          )}
        />
        <Panel
          profileId={profileId}
          title="Recent visits"
          icon={CalendarDays}
          section="visits"
          linkLabel="Visits and symptoms"
          state={visits}
          select={(items) => items.slice(0, 3)}
          empty="No visits recorded."
          render={(item) => (
            <Line
              item={item}
              title={text(item.reason) ?? 'Doctor visit'}
              detail={joined(item.occurredOn ?? 'Date not recorded', item.doctorName, item.facility)}
            />
          )}
        />
        <Panel
          profileId={profileId}
          title="Latest lab results"
          icon={FlaskConical}
          section="labs"
          linkLabel="Lab results and trends"
          state={labs}
          select={(items) => latestPerTest(items)}
          empty="No lab results recorded."
          render={(item) => (
            <Line
              item={item}
              title={String(item.testName)}
              detail={joined(
                labValue(item),
                item.resultOn ?? 'Date not recorded',
                text(item.referenceRangeAsPrinted) && `Range as printed: ${String(item.referenceRangeAsPrinted)}`,
              )}
            />
          )}
        />
        <Panel
          profileId={profileId}
          title="Emergency contacts"
          icon={Phone}
          section="history"
          linkLabel="Health history"
          state={contacts}
          empty="No emergency contacts recorded."
          className="lg:col-span-2"
          render={(item) => (
            <div className="flex flex-wrap items-baseline justify-between gap-x-3">
              <p className="font-medium text-slate-900">
                {String(item.name)}
                {text(item.relationship) && <span className="font-normal text-slate-600"> · {String(item.relationship)}</span>}
              </p>
              <a href={`tel:${String(item.phone).replace(/[^\d+]/g, '')}`} className="text-sm font-medium text-brand-700 hover:underline">
                {String(item.phone)}
              </a>
            </div>
          )}
        />
      </div>
    </div>
  )
}
