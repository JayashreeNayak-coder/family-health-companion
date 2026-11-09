import { BellOff, Check, ChevronLeft, ChevronRight, Clock, Info, Trash2, X } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import {
  listDue,
  listSchedules,
  recordAdherence,
  replaceSchedules,
  type AdherenceStatus,
  type DueDose,
  type Schedule,
} from '../../api/medications'
import { Badge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import { EmptyState, ErrorNote, Loading } from '../../components/Feedback'
import { TogglePill } from '../../components/TogglePill'
import { compactSelectClass } from '../../components/formStyles'
import { RecordSection } from '../records/RecordSection'
import { MEDICATIONS } from '../records/resources'

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function localToday() {
  return new Date().toLocaleDateString('en-CA')
}

/** Moves a YYYY-MM-DD date by whole days, in local time. */
export function shiftDate(date: string, days: number): string {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year, month - 1, day + days).toLocaleDateString('en-CA')
}

function describeDays(days: number[] | null) {
  return days ? days.map((day) => DAYS[day - 1]).join(', ') : 'Every day'
}

type Props = {
  profileId: string
  canEdit: boolean
}

export function MedicationsPanel({ profileId, canEdit }: Props) {
  const [revision, setRevision] = useState(0)

  return (
    <div className="space-y-6">
      <p className="flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
        <Info aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
        <span>
          This records what you enter. It doesn't recommend medicines or doses. Talk to a qualified
          healthcare professional before changing any medication.
        </span>
      </p>
      <DuePanel profileId={profileId} canEdit={canEdit} revision={revision} />
      <RecordSection
        profileId={profileId}
        canEdit={canEdit}
        resource={MEDICATIONS}
        renderItemExtras={(item) => (
          <ScheduleEditor
            profileId={profileId}
            medicationId={item.id}
            canEdit={canEdit}
            onChanged={() => setRevision((current) => current + 1)}
          />
        )}
      />
    </div>
  )
}

function DuePanel({
  profileId,
  canEdit,
  revision,
}: {
  profileId: string
  canEdit: boolean
  revision: number
}) {
  const [date, setDate] = useState(localToday)
  const [doses, setDoses] = useState<DueDose[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [recorded, setRecorded] = useState(0)
  const today = localToday()

  useEffect(() => {
    let ignore = false
    listDue(profileId, date).then(
      (loaded) => {
        if (!ignore) {
          setDoses(loaded)
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
  }, [profileId, date, revision, recorded])

  async function record(dose: DueDose, status: AdherenceStatus) {
    setError(null)
    try {
      await recordAdherence(profileId, dose, status)
      setRecorded((current) => current + 1)
    } catch (failure) {
      setError(errorMessage(failure))
    }
  }

  const done = doses?.filter((dose) => dose.status !== null).length ?? 0

  const dateControls = (
    <div className="flex flex-wrap items-center gap-1">
      <button
        type="button"
        aria-label="Previous day"
        onClick={() => setDate((current) => shiftDate(current, -1))}
        className="grid size-9 place-items-center rounded-lg text-slate-600 hover:bg-slate-100"
      >
        <ChevronLeft aria-hidden="true" className="size-4" />
      </button>
      <label>
        <span className="sr-only">Date</span>
        <input
          type="date"
          className={compactSelectClass}
          value={date}
          required
          onChange={(event) => event.target.value && setDate(event.target.value)}
        />
      </label>
      <button
        type="button"
        aria-label="Next day"
        onClick={() => setDate((current) => shiftDate(current, 1))}
        className="grid size-9 place-items-center rounded-lg text-slate-600 hover:bg-slate-100"
      >
        <ChevronRight aria-hidden="true" className="size-4" />
      </button>
      {date !== today && (
        <Button variant="ghost" onClick={() => setDate(today)}>
          Today
        </Button>
      )}
    </div>
  )

  return (
    <Card
      title="Doses due"
      description={
        doses && doses.length > 0
          ? `${done} of ${doses.length} recorded${date === today ? ' today' : ''}`
          : 'Active medications with a schedule appear here.'
      }
      actions={dateControls}
    >
      <ErrorNote message={error} />
      {!doses && !error && <Loading lines={2} />}
      {doses && doses.length === 0 && (
        <EmptyState icon={Clock} title="No doses due">
          Only active medications with a schedule appear here.
        </EmptyState>
      )}
      {doses && doses.length > 0 && (
        <ul className="divide-y divide-slate-100">
          {doses.map((dose) => (
            <li
              key={`${dose.scheduleId}-${dose.scheduledFor}`}
              className="flex flex-wrap items-center gap-x-4 gap-y-2 py-3 first:pt-0 last:pb-0"
            >
              <span className="w-14 shrink-0 rounded-lg bg-slate-100 py-1 text-center text-sm font-semibold text-slate-800 tabular-nums">
                {dose.timeOfDay}
              </span>
              <span className="min-w-40 flex-1">
                <span className="block font-medium text-slate-900">{dose.medicationName}</span>
                <span className="flex flex-wrap items-center gap-x-2 text-xs text-slate-500">
                  {dose.dosageAsRecorded && <span>{dose.dosageAsRecorded}</span>}
                  <span>{dose.timeZone}</span>
                  {!dose.reminderEnabled && (
                    <span className="inline-flex items-center gap-1">
                      <BellOff aria-hidden="true" className="size-3" />
                      reminder off
                    </span>
                  )}
                </span>
              </span>
              {dose.status === 'TAKEN' && (
                <Badge tone="success" icon={Check}>
                  Taken
                </Badge>
              )}
              {dose.status === 'SKIPPED' && (
                <Badge icon={X}>Skipped</Badge>
              )}
              {!dose.status && canEdit && (
                <span className="flex flex-wrap gap-2">
                  <Button
                    icon={Check}
                    aria-label={`Mark ${dose.medicationName} at ${dose.timeOfDay} as taken`}
                    onClick={() => void record(dose, 'TAKEN')}
                  >
                    Taken
                  </Button>
                  <Button
                    icon={X}
                    aria-label={`Mark ${dose.medicationName} at ${dose.timeOfDay} as skipped`}
                    onClick={() => void record(dose, 'SKIPPED')}
                  >
                    Skipped
                  </Button>
                </span>
              )}
              {!dose.status && !canEdit && <Badge>Not recorded</Badge>}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

function ScheduleEditor({
  profileId,
  medicationId,
  canEdit,
  onChanged,
}: {
  profileId: string
  medicationId: string
  canEdit: boolean
  onChanged: () => void
}) {
  const [schedules, setSchedules] = useState<Schedule[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [time, setTime] = useState('')
  const [days, setDays] = useState<number[]>([])

  useEffect(() => {
    let ignore = false
    listSchedules(profileId, medicationId).then(
      (loaded) => {
        if (!ignore) {
          setSchedules(loaded)
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
  }, [profileId, medicationId])

  async function save(next: Schedule[] | Omit<Schedule, 'id'>[]) {
    setError(null)
    try {
      const inputs = next.map(({ timeOfDay, daysOfWeek, timeZone, reminderEnabled }) => ({
        timeOfDay,
        daysOfWeek,
        timeZone,
        reminderEnabled,
      }))
      setSchedules(await replaceSchedules(profileId, medicationId, inputs))
      onChanged()
      return true
    } catch (failure) {
      setError(errorMessage(failure))
      return false
    }
  }

  async function handleAdd(event: FormEvent) {
    event.preventDefault()
    const added = {
      timeOfDay: time,
      daysOfWeek: days.length > 0 ? [...days].sort() : null,
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      reminderEnabled: true,
    }
    if (await save([...(schedules ?? []), added])) {
      setTime('')
      setDays([])
    }
  }

  function toggleDay(day: number) {
    setDays((current) =>
      current.includes(day) ? current.filter((item) => item !== day) : [...current, day],
    )
  }

  return (
    <div className="mt-3 rounded-xl border border-slate-200 bg-slate-50/60 p-3 text-sm">
      <p className="flex items-center gap-1.5 font-medium text-slate-800">
        <Clock aria-hidden="true" className="size-4 text-slate-500" />
        Schedule
      </p>
      <ErrorNote message={error} />
      {schedules && schedules.length === 0 && <p className="mt-1 text-slate-500">No schedule set.</p>}
      {schedules && schedules.length > 0 && (
        <ul className="mt-2 space-y-1.5">
          {schedules.map((schedule) => (
            <li
              key={schedule.id}
              className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg bg-white px-3 py-2 ring-1 ring-slate-200"
            >
              <span className="font-semibold text-slate-900 tabular-nums">{schedule.timeOfDay}</span>
              <span className="min-w-0 flex-1 text-slate-600">
                {describeDays(schedule.daysOfWeek)} · {schedule.timeZone}
              </span>
              {canEdit ? (
                <>
                  <label className="inline-flex items-center gap-1.5 text-slate-700">
                    <input
                      type="checkbox"
                      className="size-4 accent-brand-700"
                      checked={schedule.reminderEnabled}
                      onChange={() =>
                        void save(
                          schedules.map((item) =>
                            item.id === schedule.id
                              ? { ...item, reminderEnabled: !item.reminderEnabled }
                              : item,
                          ),
                        )
                      }
                    />
                    Remind
                  </label>
                  <button
                    type="button"
                    aria-label={`Remove schedule at ${schedule.timeOfDay}`}
                    title="Remove"
                    onClick={() => void save(schedules.filter((item) => item.id !== schedule.id))}
                    className="grid size-8 place-items-center rounded-lg text-slate-500 hover:bg-red-50 hover:text-red-700"
                  >
                    <Trash2 aria-hidden="true" className="size-4" />
                  </button>
                </>
              ) : (
                !schedule.reminderEnabled && <span className="text-xs text-slate-500">reminder off</span>
              )}
            </li>
          ))}
        </ul>
      )}
      {canEdit && schedules && (
        <form onSubmit={handleAdd} className="mt-3 flex flex-wrap items-center gap-2">
          <label className="inline-flex items-center gap-2 font-medium text-slate-700">
            Time
            <input
              type="time"
              required
              className={compactSelectClass}
              value={time}
              onChange={(event) => setTime(event.target.value)}
            />
          </label>
          <fieldset className="flex flex-wrap gap-1">
            <legend className="sr-only">Days (none selected means every day)</legend>
            {DAYS.map((label, index) => (
              <TogglePill
                key={label}
                label={label}
                checked={days.includes(index + 1)}
                onChange={() => toggleDay(index + 1)}
              />
            ))}
          </fieldset>
          <Button type="submit">Add time</Button>
          <span className="w-full text-xs text-slate-500">No days selected means every day.</span>
        </form>
      )}
    </div>
  )
}
