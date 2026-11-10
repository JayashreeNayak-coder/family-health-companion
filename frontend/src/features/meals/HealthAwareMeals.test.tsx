import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../App'
import { page, requestBody, stubApi } from '../../test/stubApi'

const account = { id: 'a1', email: 'sam@example.test', displayName: 'Sam Synthetic' }

function profile(id: string, fullName: string) {
  return { id, familyId: 'f1', fullName, dateOfBirth: null, dependent: true, linkedToAccount: false, accessLevel: 'EDIT', version: 0 }
}

const answer = {
  contentType: 'AI_GENERATED',
  mode: 'HEALTH_AWARE',
  promptVersion: 'recipes-v1',
  recipes: [
    { title: 'Vegetable rice', minutes: 20, servings: 2, ingredients: ['rice'], steps: ['Cook.'], avoidMatches: [] },
  ],
  limitations: ['Based only on what is recorded for this person.'],
  disclaimer: 'These are AI-generated recipe ideas, not medical or nutrition advice. They are not confirmed safe for this person.',
  considered: { allergies: ['Sesame'], restrictions: ['Vegetarian'], preferences: [], conditions: ['Synthetic condition A'] },
}

describe('Health-aware meal ideas', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('showsWhatWillBeSentAndSendsOnlyTickedConditionsAfterConfirmation', async () => {
    const stub = stubApi({
      'GET /auth/me': { status: 200, body: account },
      'GET /profiles?size=100': page([profile('p1', 'Child Synthetic'), profile('p2', 'Other Synthetic')]),
      'GET /profiles/p1/allergies?size=100': page([{ id: 'al1', version: 0, allergen: 'Sesame' }]),
      'GET /profiles/p1/dietary-restrictions?size=100': page([
        { id: 'd1', version: 0, kind: 'RESTRICTION', description: 'Vegetarian' },
      ]),
      'GET /profiles/p1/conditions?size=100': page([
        { id: 'c1', version: 0, name: 'Synthetic condition A' },
        { id: 'c2', version: 0, name: 'Synthetic condition B' },
      ]),
      'POST /profiles/p1/meal-assistant/health-aware': { status: 200, body: answer },
    })
    window.history.replaceState(null, '', '/meals')
    render(<App />)

    fireEvent.change(await screen.findByLabelText('Ingredients', { selector: 'textarea' }), {
      target: { value: 'rice' },
    })
    fireEvent.click(screen.getByLabelText('A family member'))
    fireEvent.change(screen.getByLabelText('Person'), { target: { value: 'p1' } })

    expect(await screen.findByText('Sesame')).toBeInTheDocument()
    expect(screen.getByText('Vegetarian')).toBeInTheDocument()
    const conditionA = screen.getByLabelText('Synthetic condition A')
    expect(conditionA).not.toBeChecked()
    fireEvent.click(conditionA)

    fireEvent.click(screen.getByRole('button', { name: 'Suggest recipes' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Confirm that this person')
    fireEvent.click(screen.getByLabelText(/I understand that Child Synthetic/))
    fireEvent.click(screen.getByRole('button', { name: 'Suggest recipes' }))

    expect(await screen.findByRole('heading', { name: 'Vegetable rice' })).toBeInTheDocument()
    expect(requestBody(stub, 'POST', '/profiles/p1/meal-assistant/health-aware')).toEqual({
      ingredients: ['rice'],
      avoid: [],
      maxMinutes: null,
      preferences: null,
      conditionIds: ['c1'],
    })
    expect(screen.getByText('Records used for these ideas')).toBeInTheDocument()
    expect(screen.getByText(/not confirmed safe for this person/)).toBeInTheDocument()

    // The save dialog offers the person the ideas were for.
    fireEvent.click(screen.getByRole('button', { name: 'Save to food log: Vegetable rice' }))
    const dialog = await screen.findByRole('dialog', { name: 'Save to food log' })
    expect(within(dialog).getByLabelText('Person')).toHaveValue('p1')
  })

  it('clearsTheConfirmationWhenAnotherPersonIsChosen', async () => {
    stubApi({
      'GET /auth/me': { status: 200, body: account },
      'GET /profiles?size=100': page([profile('p1', 'Child Synthetic'), profile('p2', 'Other Synthetic')]),
    })
    window.history.replaceState(null, '', '/meals')
    render(<App />)

    fireEvent.click(await screen.findByLabelText('A family member'))
    fireEvent.change(screen.getByLabelText('Person'), { target: { value: 'p1' } })
    fireEvent.click(await screen.findByLabelText(/I understand that Child Synthetic/))
    fireEvent.change(screen.getByLabelText('Person'), { target: { value: 'p2' } })

    expect(await screen.findByLabelText(/I understand that Other Synthetic/)).not.toBeChecked()
  })
})
