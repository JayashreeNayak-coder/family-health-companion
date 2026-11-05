import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { page, problem, requestBody, stubApi } from '../../test/stubApi'
import { MedicationsPanel } from './MedicationsPanel'

const medication = {
  id: 'm1',
  name: 'Synthetic medicine',
  dosageAsRecorded: '1 tablet',
  instructionsAsRecorded: null,
  prescribedBy: null,
  startOn: null,
  endOn: null,
  status: 'ACTIVE',
  sourceType: 'USER_REPORTED',
  version: 0,
}

const schedule = {
  id: 's1',
  timeOfDay: '08:00',
  daysOfWeek: null,
  timeZone: 'UTC',
  reminderEnabled: true,
}

const dose = {
  medicationId: 'm1',
  medicationName: 'Synthetic medicine',
  dosageAsRecorded: '1 tablet',
  scheduleId: 's1',
  scheduledFor: '2026-01-15T08:00:00Z',
  timeOfDay: '08:00',
  timeZone: 'UTC',
  reminderEnabled: true,
  status: null,
}

const DUE = 'GET /profiles/p1/medications/due?date=2026-01-15'
const LIST = 'GET /profiles/p1/medications?size=100'
const SCHEDULES = 'GET /profiles/p1/medications/m1/schedules'

function ok(body: unknown) {
  return { status: 200, body }
}

describe('MedicationsPanel', () => {
  beforeEach(() => {
    vi.useFakeTimers({ now: new Date(2026, 0, 15, 12), toFake: ['Date'] })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('statesThatItRecommendsNothingAndShowsTheEmptyStates', async () => {
    stubApi({ [DUE]: ok([]), [LIST]: page([]) })

    render(<MedicationsPanel profileId="p1" canEdit />)

    expect(screen.getByText(/doesn't recommend medicines or doses/)).toBeInTheDocument()
    expect(await screen.findByText(/No doses due/)).toBeInTheDocument()
  })

  it('recordsADoseAndShowsItsNewStatus', async () => {
    const stub = stubApi({
      [DUE]: [ok([dose]), ok([{ ...dose, status: 'TAKEN' }])],
      [LIST]: page([medication]),
      [SCHEDULES]: ok([schedule]),
      'POST /profiles/p1/medications/m1/adherence': { status: 201, body: {} },
    })
    render(<MedicationsPanel profileId="p1" canEdit />)

    fireEvent.click(
      await screen.findByRole('button', { name: 'Mark Synthetic medicine at 08:00 as taken' }),
    )

    expect(await screen.findByText('Taken')).toBeInTheDocument()
    expect(requestBody(stub, 'POST', '/profiles/p1/medications/m1/adherence')).toEqual({
      scheduleId: 's1',
      scheduledFor: '2026-01-15T08:00:00Z',
      status: 'TAKEN',
    })
  })

  it('reportsADoseAlreadyRecorded', async () => {
    stubApi({
      [DUE]: ok([dose]),
      [LIST]: page([]),
      'POST /profiles/p1/medications/m1/adherence': problem(
        409,
        'ALREADY_RECORDED',
        'This dose has already been recorded',
      ),
    })
    render(<MedicationsPanel profileId="p1" canEdit />)

    fireEvent.click(
      await screen.findByRole('button', { name: 'Mark Synthetic medicine at 08:00 as skipped' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('already been recorded')
  })

  it('hidesDoseAndScheduleControlsWithoutEditAccess', async () => {
    stubApi({
      [DUE]: ok([dose]),
      [LIST]: page([medication]),
      [SCHEDULES]: ok([schedule]),
    })
    render(<MedicationsPanel profileId="p1" canEdit={false} />)

    expect(await screen.findByText(/Every day/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /as taken/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add time' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Remove schedule/ })).not.toBeInTheDocument()
  })

  it('addsAScheduleTimeKeepingTheExistingOnesAndRefreshesTheDueList', async () => {
    const stub = stubApi({
      [DUE]: [ok([]), ok([dose])],
      [LIST]: page([medication]),
      [SCHEDULES]: ok([schedule]),
      'PUT /profiles/p1/medications/m1/schedules': ok([schedule, { ...schedule, id: 's2', timeOfDay: '20:00' }]),
    })
    render(<MedicationsPanel profileId="p1" canEdit />)
    await screen.findByText(/Every day/)

    fireEvent.change(screen.getByLabelText('Time'), { target: { value: '20:00' } })
    fireEvent.click(screen.getByLabelText('Mon'))
    fireEvent.click(screen.getByRole('button', { name: 'Add time' }))

    expect(await screen.findByText(/20:00/)).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /as taken/ })).toBeInTheDocument()
    const { schedules } = requestBody(stub, 'PUT', '/profiles/p1/medications/m1/schedules')
    expect(schedules).toHaveLength(2)
    expect(schedules[0]).toEqual({
      timeOfDay: '08:00',
      daysOfWeek: null,
      timeZone: 'UTC',
      reminderEnabled: true,
    })
    expect(schedules[1]).toMatchObject({ timeOfDay: '20:00', daysOfWeek: [1], reminderEnabled: true })
  })
})
