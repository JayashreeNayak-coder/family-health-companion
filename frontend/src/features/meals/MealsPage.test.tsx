import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../../App'
import { splitList } from '../../api/meals'
import { page, problem, requestBody, stubApi } from '../../test/stubApi'

const account = { id: 'a1', email: 'sam@example.test', displayName: 'Sam Synthetic' }

function profile(id: string, fullName: string, accessLevel: string) {
  return {
    id,
    familyId: 'f1',
    fullName,
    dateOfBirth: null,
    dependent: true,
    linkedToAccount: false,
    accessLevel,
    version: 0,
  }
}

const suggestions = {
  contentType: 'AI_GENERATED',
  mode: 'GENERAL',
  promptVersion: 'recipes-v1',
  recipes: [
    {
      title: 'Synthetic satay',
      minutes: 20,
      servings: 2,
      ingredients: ['2 tbsp peanut butter', 'noodles'],
      steps: ['Boil the noodles.', 'Stir in the sauce.'],
      avoidMatches: ['peanuts'],
    },
    {
      title: 'Plain rice',
      minutes: null,
      servings: null,
      ingredients: ['1 cup rice'],
      steps: ['Cook the rice.'],
      avoidMatches: [],
    },
  ],
  limitations: ["General mode doesn't use any family member's health records."],
  disclaimer: 'These are AI-generated recipe ideas, not medical or nutrition advice.',
}

function openMeals() {
  window.history.replaceState(null, '', '/meals')
  render(<App />)
}

describe('MealsPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('splitsTypedListsOnCommasAndNewLines', () => {
    expect(splitList(' rice, Lentils\nrice ,, spinach ')).toEqual(['rice', 'Lentils', 'spinach'])
  })

  it('sendsOnlyTheTypedFieldsAndLabelsTheSuggestions', async () => {
    const stub = stubApi({
      'GET /auth/me': { status: 200, body: account },
      'POST /meal-assistant/general': { status: 200, body: suggestions },
    })
    openMeals()

    expect(await screen.findByRole('heading', { level: 1, name: 'Meal ideas' })).toBeInTheDocument()
    expect(within(screen.getByRole('navigation', { name: 'Main' })).getByRole('link', { name: 'Meal ideas' }))
      .toHaveAttribute('aria-current', 'page')
    fireEvent.change(screen.getByLabelText('Ingredients'), { target: { value: 'noodles, rice' } })
    fireEvent.change(screen.getByLabelText('Ingredients to avoid'), { target: { value: 'peanuts' } })
    fireEvent.change(screen.getByLabelText('Most minutes to cook'), { target: { value: '30' } })
    fireEvent.click(screen.getByRole('button', { name: 'Suggest recipes' }))

    expect(await screen.findByRole('heading', { name: 'Synthetic satay' })).toBeInTheDocument()
    expect(requestBody(stub, 'POST', '/meal-assistant/general')).toEqual({
      ingredients: ['noodles', 'rice'],
      avoid: ['peanuts'],
      maxMinutes: 30,
      preferences: null,
    })
    expect(screen.getByText('AI generated')).toBeInTheDocument()
    expect(screen.getByText(suggestions.disclaimer)).toBeInTheDocument()
    expect(screen.getByText(/Mentions something you asked to avoid/).closest('p')).toHaveTextContent('peanuts')
    expect(screen.getByText('Time not given')).toBeInTheDocument()
    // The typed ingredients stay after the answer arrives.
    expect(screen.getByLabelText('Ingredients')).toHaveValue('noodles, rice')
  })

  it('asksForAnIngredientBeforeSending', async () => {
    const stub = stubApi({ 'GET /auth/me': { status: 200, body: account } })
    openMeals()

    fireEvent.click(await screen.findByRole('button', { name: 'Suggest recipes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Type at least one ingredient.')
    expect(stub.mock.calls.some(([url]) => String(url).includes('meal-assistant'))).toBe(false)
  })

  it('showsWhenTheAiServiceIsUnavailable', async () => {
    stubApi({
      'GET /auth/me': { status: 200, body: account },
      'POST /meal-assistant/general': problem(503, 'AI_UNAVAILABLE', 'AI suggestions are not available right now.'),
    })
    openMeals()

    fireEvent.change(await screen.findByLabelText('Ingredients'), { target: { value: 'rice' } })
    fireEvent.click(screen.getByRole('button', { name: 'Suggest recipes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('AI suggestions are not available right now.')
  })

  it('savesARecipeToAnEditableProfilesFoodLog', async () => {
    const stub = stubApi({
      'GET /auth/me': { status: 200, body: account },
      'GET /profiles?size=100': page([profile('p1', 'Viewer Only', 'VIEW'), profile('p2', 'Child Synthetic', 'EDIT')]),
      'POST /meal-assistant/general': { status: 200, body: suggestions },
      'POST /profiles/p2/recipes': { status: 201, body: { id: 'r1' } },
      'POST /profiles/p2/food-log/from-recipe/r1': [
        problem(503, 'UNAVAILABLE', 'Try again'),
        { status: 201, body: { id: 'e1' } },
      ],
    })
    openMeals()

    fireEvent.change(await screen.findByLabelText('Ingredients'), { target: { value: 'rice' } })
    fireEvent.click(screen.getByRole('button', { name: 'Suggest recipes' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Save to food log: Plain rice' }))

    const dialog = await screen.findByRole('dialog', { name: 'Save to food log' })
    const person = within(dialog).getByLabelText('Person')
    expect(within(person).queryByRole('option', { name: 'Viewer Only' })).not.toBeInTheDocument()
    fireEvent.change(within(dialog).getByLabelText('Meal'), { target: { value: 'DINNER' } })
    fireEvent.change(within(dialog).getByLabelText('Date'), { target: { value: '2026-03-01' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save to food log' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Try again')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save to food log' }))

    expect(await screen.findByText("Added to Child Synthetic's food log")).toBeInTheDocument()
    expect(requestBody(stub, 'POST', '/profiles/p2/recipes')).toEqual({
      title: 'Plain rice',
      ingredients: ['1 cup rice'],
      steps: ['Cook the rice.'],
      mode: 'GENERAL',
      promptVersion: 'recipes-v1',
    })
    // The retry reused the saved recipe instead of saving it twice.
    await waitFor(() =>
      expect(stub.mock.calls.filter(([url]) => url === '/api/v1/profiles/p2/recipes')).toHaveLength(1),
    )
    expect(requestBody(stub, 'POST', '/profiles/p2/food-log/from-recipe/r1')).toEqual({
      mealType: 'DINNER',
      occurredOn: '2026-03-01',
      occurredTime: null,
    })
  })
})
