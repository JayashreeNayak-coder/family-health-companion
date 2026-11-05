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
import {
  Card,
  dangerButtonClass,
  ErrorNote,
  inputClass,
  secondaryButtonClass,
} from '../../components/ui'
import { RecordSection } from '../records/RecordSection'
import { MEDICATIONS } from '../records/resources'

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function localToday() {
  return new Date().toLocaleDateString('en-CA')
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
    <>
      <p className="rounded bg-amber-50 p-3 text-sm text-amber-900">
        This records what you enter. It doesn't recommend medicines or doses. Talk to a qualified
        healthcare professional before changing any medication.
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
    </>
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

  return (
    <Card title="Doses due">
      <label className="block text-sm font-medium">
        Date
        <input
          type="date"
          className={inputClass}
          value={date}
          onChange={(event) => setDate(event.target.value)}
        />
      </label>
      <ErrorNote message={error} />
      {!doses && !error && <p role="status">Loading…</p>}
      {doses && doses.length === 0 && (
        <p className="mt-3 text-sm text-slate-600">
          No doses due. Only active medications with a schedule appear here.
        </p>
      )}
      {doses && doses.length > 0 && (
        <ul className="mt-3 divide-y divide-slate-200">
          {doses.map((dose) => (
            <li
              key={`${dose.scheduleId}-${dose.scheduledFor}`}
              className="flex flex-wrap items-center justify-between gap-2 py-2"
            >
              <span>
                <strong>{dose.timeOfDay}</strong> {dose.medicationName}
                {dose.dosageAsRecorded && ` · ${dose.dosageAsRecorded}`}
                <span className="block text-xs text-slate-600">
                  {dose.timeZone}
                  {dose.reminderEnabled ? '' : ' · reminder off'}
                </span>
              </span>
              {dose.status && (
                <span className="text-sm font-medium">
                  {dose.status === 'TAKEN' ? 'Taken' : 'Skipped'}
                </span>
              )}
              {!dose.status && canEdit && (
                <span className="flex gap-2">
                  <button
                    type="button"
                    className={secondaryButtonClass}
                    aria-label={`Mark ${dose.medicationName} at ${dose.timeOfDay} as taken`}
                    onClick={() => void record(dose, 'TAKEN')}
                  >
                    Taken
                  </button>
                  <button
                    type="button"
                    className={secondaryButtonClass}
                    aria-label={`Mark ${dose.medicationName} at ${dose.timeOfDay} as skipped`}
                    onClick={() => void record(dose, 'SKIPPED')}
                  >
                    Skipped
                  </button>
                </span>
              )}
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
    <div className="mt-2 rounded bg-slate-50 p-2 text-sm">
      <strong>Schedule</strong>
      <ErrorNote message={error} />
      {schedules && schedules.length === 0 && <p className="text-slate-600">No schedule set.</p>}
      {schedules && schedules.length > 0 && (
        <ul>
          {schedules.map((schedule) => (
            <li key={schedule.id} className="flex flex-wrap items-center gap-2 py-1">
              <span>
                {schedule.timeOfDay} · {describeDays(schedule.daysOfWeek)} · {schedule.timeZone}
              </span>
              {canEdit && (
                <>
                  <label className="flex items-center gap-1">
                    <input
                      type="checkbox"
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
                    className={dangerButtonClass}
                    aria-label={`Remove schedule at ${schedule.timeOfDay}`}
                    onClick={() => void save(schedules.filter((item) => item.id !== schedule.id))}
                  >
                    Remove
                  </button>
                </>
              )}
            </li>
          ))}
        </ul>
      )}
      {canEdit && schedules && (
        <form onSubmit={handleAdd} className="mt-2 flex flex-wrap items-end gap-2">
          <label className="font-medium">
            Time
            <input
              type="time"
              required
              className="ml-1 rounded border border-slate-300 px-2 py-1"
              value={time}
              onChange={(event) => setTime(event.target.value)}
            />
          </label>
          <fieldset className="flex flex-wrap gap-1">
            <legend className="sr-only">Days (none selected means every day)</legend>
            {DAYS.map((label, index) => (
              <label key={label} className="flex items-center gap-0.5">
                <input
                  type="checkbox"
                  checked={days.includes(index + 1)}
                  onChange={() => toggleDay(index + 1)}
                />
                {label}
              </label>
            ))}
          </fieldset>
          <button type="submit" className={secondaryButtonClass}>
            Add time
          </button>
        </form>
      )}
    </div>
  )
}
