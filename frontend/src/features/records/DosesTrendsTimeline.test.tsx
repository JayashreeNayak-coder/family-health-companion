import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { TimelineEntry } from '../../api/timeline'
import { page, stubApi } from '../../test/stubApi'
import { MedicationsPanel, shiftDate } from '../medication/MedicationsPanel'
import { LabTrendsPanel } from './LabTrendsPanel'
import { byMonth, monthLabel, TimelinePanel } from './TimelinePanel'

function entry(id: string, date: string | null): TimelineEntry {
  return {
    type: 'VISIT',
    id,
    date,
    time: null,
    title: `Visit ${id}`,
    detail: null,
    sourceType: 'USER_REPORTED',
    sourceDocumentId: null,
    sourceDocumentDeletedAt: null,
  }
}

describe('Date and month helpers', () => {
  it('shiftsDatesAcrossMonthAndYearEnds', () => {
    expect(shiftDate('2026-01-15', 1)).toBe('2026-01-16')
    expect(shiftDate('2026-03-01', -1)).toBe('2026-02-28')
    expect(shiftDate('2026-12-31', 1)).toBe('2027-01-01')
  })

  it('groupsTimelineEntriesByMonthKeepingTheirOrder', () => {
    expect(monthLabel('2026-03-14')).toBe('March 2026')
    const groups = byMonth([entry('a', '2026-03-14'), entry('b', '2026-03-02'), entry('c', '2026-01-20')])
    expect(groups.map((group) => [group.month, group.entries.map((item) => item.id)])).toEqual([
      ['March 2026', ['a', 'b']],
      ['January 2026', ['c']],
    ])
  })
})

describe('Doses, trends, and timeline', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('stepsTheDueListByDayAndReturnsToToday', async () => {
    vi.useFakeTimers({ now: new Date(2026, 0, 15, 12), toFake: ['Date'] })
    const stub = stubApi({
      'GET /profiles/p1/medications/due?date=2026-01-15': { status: 200, body: [] },
      'GET /profiles/p1/medications/due?date=2026-01-16': { status: 200, body: [] },
      'GET /profiles/p1/medications?size=100': page([]),
    })
    render(<MedicationsPanel profileId="p1" canEdit />)
    await screen.findByText('No doses due')

    fireEvent.click(screen.getByRole('button', { name: 'Next day' }))
    expect(screen.getByLabelText('Date')).toHaveValue('2026-01-16')
    await vi.waitFor(() =>
      expect(stub.mock.calls.some(([url]) => String(url).endsWith('date=2026-01-16'))).toBe(true),
    )

    fireEvent.click(screen.getByRole('button', { name: 'Today' }))
    expect(screen.getByLabelText('Date')).toHaveValue('2026-01-15')
    expect(screen.queryByRole('button', { name: 'Today' })).not.toBeInTheDocument()
  })

  it('labelsEachEntryByItsOwnTypeAndNeverAsAnother', async () => {
    stubApi({
      'GET /profiles/p1/timeline': {
        status: 200,
        body: {
          entries: [
            { ...entry('l', '2026-03-10'), type: 'LAB_RESULT', title: 'Synthetic HbA1c' },
            { ...entry('x', '2026-03-09'), type: 'SOMETHING_NEW', title: 'Future record' },
          ],
          dateUnknown: [],
        },
      },
    })
    render(<TimelinePanel profileId="p1" />)

    const march = await screen.findByRole('region', { name: 'March 2026' })
    expect(within(march).getByText('Lab result')).toBeInTheDocument()
    expect(within(march).getByText('Record')).toBeInTheDocument()
    expect(within(march).queryByText('Condition')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Lab results')).toBeInTheDocument()
  })

  it('groupsTheTimelineByMonthAndClearsFilters', async () => {
    stubApi({
      'GET /profiles/p1/timeline': {
        status: 200,
        body: { entries: [entry('a', '2026-03-14'), entry('c', '2026-01-20')], dateUnknown: [] },
      },
      'GET /profiles/p1/timeline?types=VISIT': {
        status: 200,
        body: { entries: [entry('a', '2026-03-14')], dateUnknown: [] },
      },
    })
    render(<TimelinePanel profileId="p1" />)

    const march = await screen.findByRole('region', { name: 'March 2026' })
    expect(within(march).getByText('Visit a')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'January 2026' })).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Visits'))
    expect(await screen.findByRole('button', { name: 'Clear filters' })).toBeInTheDocument()
    await vi.waitFor(() => expect(screen.queryByRole('region', { name: 'January 2026' })).not.toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(screen.getByLabelText('Visits')).not.toBeChecked()
    expect(await screen.findByRole('region', { name: 'January 2026' })).toBeInTheDocument()
  })

  it('showsTheLatestValueAndAHoverReadoutWithoutJudgingIt', async () => {
    stubApi({
      'GET /profiles/p1/lab-results/trend-series': {
        status: 200,
        body: [{ testKey: 'hba1c', testName: 'HbA1c', unit: '%', count: 2 }],
      },
      'GET /profiles/p1/lab-results/trends?testKey=hba1c&unit=%25': {
        status: 200,
        body: {
          testKey: 'hba1c',
          unit: '%',
          points: [
            { id: 'a', resultOn: '2026-01-10', value: 5.8, sourceType: 'USER_REPORTED' },
            { id: 'b', resultOn: '2026-03-01', value: 6.1, sourceType: 'DOCUMENTED' },
          ],
          excludedCount: 0,
        },
      },
    })
    // jsdom has no layout, so give the chart the size it has on screen: 640 units wide.
    vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
      left: 0,
      top: 0,
      width: 640,
      height: 220,
    } as DOMRect)
    render(<LabTrendsPanel profileId="p1" />)

    const chart = await screen.findByRole('img', { name: /HbA1c/ })
    expect(screen.getByText('Latest')).toHaveTextContent('Latest 6.1 %')

    const target = chart.querySelector('rect')!
    fireEvent.pointerMove(target, { clientX: 70 })
    expect(screen.getByText('2026-01-10', { selector: 'span' })).toBeInTheDocument()

    fireEvent.pointerLeave(target)
    expect(screen.queryByText('2026-01-10', { selector: 'span' })).not.toBeInTheDocument()
    expect(screen.queryByText(/high|low|normal/i)).not.toBeInTheDocument()
  })
})
