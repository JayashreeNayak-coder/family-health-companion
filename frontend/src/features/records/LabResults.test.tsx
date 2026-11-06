import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { page, requestBody, stubApi } from '../../test/stubApi'
import { LabTrendsPanel } from './LabTrendsPanel'
import { RecordSection } from './RecordSection'
import { LAB_RESULTS } from './resources'

describe('Lab results', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('sendsTheValueAsANumberAndLeavesAMissingUnitMissing', async () => {
    const stub = stubApi({
      'GET /profiles/p1/lab-results?size=100': page([]),
      'POST /profiles/p1/lab-results': { status: 201, body: {} },
    })
    render(<RecordSection profileId="p1" canEdit resource={LAB_RESULTS} />)
    fireEvent.click(await screen.findByRole('button', { name: 'Add lab result' }))

    expect(screen.getByText(/never guessed/)).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Test'), { target: { value: 'Synthetic HbA1c' } })
    fireEvent.change(screen.getByLabelText(/^Value \(number\)/), { target: { value: '5.4' } })
    fireEvent.click(screen.getByRole('button', { name: 'Save' }))

    await screen.findByRole('button', { name: 'Add lab result' })
    const body = requestBody(stub, 'POST', '/profiles/p1/lab-results')
    expect(body.valueNumeric).toBe(5.4)
    expect(body.unit).toBeNull()
    expect(body.resultOn).toBeNull()
    expect(body).not.toHaveProperty('sourceDocumentId')
  })

  it('showsATrendAsAChartAndATableWithoutInterpretation', async () => {
    stubApi({
      'GET /profiles/p1/lab-results/trend-series': {
        status: 200,
        body: [
          { testKey: 'hba1c', testName: 'HbA1c', unit: '%', count: 2 },
          { testKey: 'urine ph', testName: 'Urine pH', unit: null, count: 1 },
        ],
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
          excludedCount: 1,
        },
      },
      'GET /profiles/p1/lab-results/trends?testKey=urine+ph': {
        status: 200,
        body: { testKey: 'urine ph', unit: null, points: [], excludedCount: 0 },
      },
    })

    render(<LabTrendsPanel profileId="p1" />)

    expect(await screen.findByRole('img', { name: /HbA1c from 2026-01-10 to 2026-03-01/ })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '6.1' })).toBeInTheDocument()
    expect(screen.getByText(/1 other result for this test isn’t shown/)).toBeInTheDocument()
    expect(document.body).not.toHaveTextContent(/normal|abnormal|high|low/i)

    fireEvent.change(screen.getByLabelText('Test'), { target: { value: 'urine ph\u0000' } })
    expect(await screen.findByText('(no unit recorded)')).toBeInTheDocument()
  })

  it('explainsWhatATrendNeedsWhenThereIsNone', async () => {
    stubApi({ 'GET /profiles/p1/lab-results/trend-series': { status: 200, body: [] } })

    render(<LabTrendsPanel profileId="p1" />)

    expect(await screen.findByText(/needs lab results with a number and a date/)).toBeInTheDocument()
  })
})
