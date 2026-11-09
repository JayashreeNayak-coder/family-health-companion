import { render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { page, stubApi } from '../../test/stubApi'
import { Dashboard } from './Dashboard'
import { ageText } from './ProfileCard'

const today = new Date(2026, 10, 9) // 9 November 2026

describe('ageText', () => {
  it('countsWholeYearsAndMonths', () => {
    expect(ageText('2016-04-12', today)).toBe('10 years')
    expect(ageText('2016-11-10', today)).toBe('9 years')
    expect(ageText('2025-11-09', today)).toBe('1 year')
    expect(ageText('2026-10-09', today)).toBe('1 month')
    expect(ageText('2026-06-20', today)).toBe('4 months')
    expect(ageText('2026-11-01', today)).toBe('0 months')
  })

  it('saysNothingForAMissingFutureOrMalformedDate', () => {
    expect(ageText(null, today)).toBeNull()
    expect(ageText('2027-01-01', today)).toBeNull()
    expect(ageText('not-a-date', today)).toBeNull()
  })
})

describe('Home overview', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  const family = { id: 'f1', name: 'Synthetic Family', role: 'MEMBER' }
  const own = {
    id: 'p1',
    familyId: 'f1',
    fullName: 'Sam Synthetic',
    dateOfBirth: null,
    dependent: false,
    linkedToAccount: true,
    accessLevel: 'MANAGE',
    version: 0,
  }
  const elsewhere = { ...own, id: 'p2', familyId: 'f9', fullName: 'Grandma Synthetic', linkedToAccount: false, accessLevel: 'VIEW' }

  it('showsPeopleMembersAndProfilesSharedFromAnotherFamily', async () => {
    stubApi({
      'GET /families?size=100': page([family]),
      'GET /profiles?size=100': page([own, elsewhere]),
      'GET /families/f1': {
        status: 200,
        body: {
          ...family,
          members: [
            { accountId: 'a1', displayName: 'Sam Synthetic', email: 'sam@example.test', role: 'MEMBER' },
            { accountId: 'a2', displayName: 'Kim Synthetic', email: 'kim@example.test', role: 'MANAGER' },
          ],
        },
      },
    })

    render(<Dashboard />)

    const familySection = await screen.findByRole('region', { name: 'Synthetic Family' })
    const card = within(familySection).getByRole('link', { name: 'Sam Synthetic' })
    expect(card).toHaveTextContent('Age not recorded')
    expect(card).toHaveTextContent('Has own account')
    expect(await within(familySection).findByText('Family members · 2')).toBeInTheDocument()
    expect(within(familySection).getByText('kim@example.test')).toBeInTheDocument()

    const shared = screen.getByRole('region', { name: 'Shared with you' })
    expect(within(shared).getByRole('link', { name: 'Grandma Synthetic' })).toHaveTextContent('View only')
  })
})
