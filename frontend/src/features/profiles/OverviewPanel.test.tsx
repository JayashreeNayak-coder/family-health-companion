import { render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { page, problem, stubApi } from '../../test/stubApi'
import { labValue, latestPerTest, OverviewPanel } from './OverviewPanel'

const base = { version: 0, sourceType: 'USER_REPORTED' as const, sourceDocumentId: null, sourceDocumentDeletedAt: null }

function panel(name: string) {
  return screen.getByRole('region', { name })
}

describe('OverviewPanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('summarisesWhatIsRecordedAndLinksToEachSection', async () => {
    stubApi({
      'GET /profiles/p1/allergies?size=100': page([
        { ...base, id: 'a1', allergen: 'Synthetic pollen', reactionAsRecorded: 'Rash', severityAsRecorded: null },
      ]),
      'GET /profiles/p1/conditions?size=100': page([
        { ...base, id: 'c1', name: 'Current synthetic', status: 'ACTIVE', onsetOn: '2024-03-01' },
        { ...base, id: 'c2', name: 'Old synthetic', status: 'RESOLVED', onsetOn: null },
      ]),
      'GET /profiles/p1/medications?size=100': page([
        { ...base, id: 'm1', name: 'Taking synthetic', status: 'ACTIVE', dosageAsRecorded: '5 mg' },
        { ...base, id: 'm2', name: 'Stopped synthetic', status: 'STOPPED' },
      ]),
      'GET /profiles/p1/emergency-contacts?size=100': page([
        { id: 'e1', version: 0, name: 'Meera Synthetic', relationship: 'Aunt', phone: '+91 555 0100' },
      ]),
    })

    render(<OverviewPanel profileId="p1" />)

    const allergies = panel('Allergies')
    expect(await within(allergies).findByText('Synthetic pollen')).toBeInTheDocument()
    expect(within(allergies).getByText('Rash')).toBeInTheDocument()
    expect(within(allergies).getByText('User reported')).toBeInTheDocument()

    const conditions = panel('Current conditions')
    expect(await within(conditions).findByText('Current synthetic')).toBeInTheDocument()
    expect(within(conditions).queryByText('Old synthetic')).not.toBeInTheDocument()

    const medications = panel('Active medications')
    expect(await within(medications).findByText('Taking synthetic')).toBeInTheDocument()
    expect(within(medications).queryByText('Stopped synthetic')).not.toBeInTheDocument()
    expect(within(medications).getByRole('link', { name: 'Medications and doses' })).toHaveAttribute(
      'href',
      '/profiles/p1/medications',
    )

    const contacts = panel('Emergency contacts')
    expect(await within(contacts).findByRole('link', { name: '+91 555 0100' })).toHaveAttribute('href', 'tel:+915550100')

    expect(await within(panel('Recent visits')).findByText('No visits recorded.')).toBeInTheDocument()
    expect(screen.getByText(/isn't medical advice/)).toBeInTheDocument()
  })

  it('keepsShowingTheOtherPanelsWhenOneListFails', async () => {
    stubApi({
      'GET /profiles/p1/allergies?size=100': problem(500, 'INTERNAL_ERROR', 'Something went wrong'),
    })

    render(<OverviewPanel profileId="p1" />)

    expect(await within(panel('Allergies')).findByRole('alert')).toHaveTextContent('Something went wrong')
    expect(await within(panel('Diet')).findByText(/No dietary/)).toBeInTheDocument()
  })

  it('keepsTheNewestResultForEachTestAndUnit', () => {
    const items = [
      { ...base, id: '3', testName: 'HbA1c', unit: '%', resultOn: '2026-03-10' },
      { ...base, id: '2', testName: 'hba1c ', unit: '%', resultOn: '2026-01-10' },
      { ...base, id: '1', testName: 'HbA1c', unit: 'mmol/mol', resultOn: '2025-09-10' },
    ]

    expect(latestPerTest(items).map((item) => item.id)).toEqual(['3', '1'])
  })

  it('showsALabValueAsRecordedWithoutGuessingAUnit', () => {
    expect(labValue({ ...base, id: '1', valueNumeric: 5.7, unit: '%' })).toBe('5.7 %')
    expect(labValue({ ...base, id: '2', valueNumeric: 0, unit: null })).toBe('0')
    expect(labValue({ ...base, id: '3', valueNumeric: null, valueText: 'Negative' })).toBe('Negative')
    expect(labValue({ ...base, id: '4', valueNumeric: null, valueText: null })).toBe('Value not recorded')
  })
})
