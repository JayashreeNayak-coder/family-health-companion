import { apiGet, apiPost, apiPut } from './client'

export type AdherenceStatus = 'TAKEN' | 'SKIPPED'

export type ScheduleInput = {
  timeOfDay: string
  /** null means daily. 1 is Monday and 7 is Sunday. */
  daysOfWeek: number[] | null
  timeZone: string
  reminderEnabled: boolean
}

export type Schedule = ScheduleInput & { id: string }

export type DueDose = {
  medicationId: string
  medicationName: string
  dosageAsRecorded: string | null
  scheduleId: string
  scheduledFor: string
  timeOfDay: string
  timeZone: string
  reminderEnabled: boolean
  status: AdherenceStatus | null
}

function medication(profileId: string, medicationId: string) {
  return `/profiles/${profileId}/medications/${medicationId}`
}

export function listSchedules(profileId: string, medicationId: string): Promise<Schedule[]> {
  return apiGet(`${medication(profileId, medicationId)}/schedules`)
}

export function replaceSchedules(
  profileId: string,
  medicationId: string,
  schedules: ScheduleInput[],
): Promise<Schedule[]> {
  return apiPut(`${medication(profileId, medicationId)}/schedules`, { schedules })
}

export function listDue(profileId: string, date: string): Promise<DueDose[]> {
  return apiGet(`/profiles/${profileId}/medications/due?date=${date}`)
}

export function recordAdherence(
  profileId: string,
  dose: Pick<DueDose, 'medicationId' | 'scheduleId' | 'scheduledFor'>,
  status: AdherenceStatus,
): Promise<unknown> {
  return apiPost(`${medication(profileId, dose.medicationId)}/adherence`, {
    scheduleId: dose.scheduleId,
    scheduledFor: dose.scheduledFor,
    status,
  })
}
