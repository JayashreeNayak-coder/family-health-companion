import { apiPost } from './client'

export type MealMode = 'GENERAL' | 'HEALTH_AWARE'

export type MealType = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK' | 'OTHER'

export type SuggestedRecipe = {
  title: string
  minutes: number | null
  servings: number | null
  ingredients: string[]
  steps: string[]
  /** Ingredients to avoid that this recipe's text mentions. Empty never means safe. */
  avoidMatches: string[]
}

/** The recorded facts that were sent in health-aware mode. */
export type Considered = {
  allergies: string[]
  restrictions: string[]
  preferences: string[]
  conditions: string[]
}

export type MealSuggestions = {
  contentType: 'AI_GENERATED'
  mode: MealMode
  promptVersion: string
  recipes: SuggestedRecipe[]
  limitations: string[]
  disclaimer: string
  /** Null in general mode. */
  considered: Considered | null
}

export type GeneralMealRequest = {
  ingredients: string[]
  maxMinutes: number | null
  preferences: string | null
  avoid: string[]
}

export type SavedRecipe = {
  id: string
  profileId: string
  title: string
  ingredients: string[]
  steps: string[]
  mode: MealMode
  promptVersion: string
  contentType: 'AI_GENERATED'
  createdAt: string
  version: number
}

/** General mode: the backend uses only what is sent here, never a profile's records. */
export function suggestGeneral(request: GeneralMealRequest): Promise<MealSuggestions> {
  return apiPost('/meal-assistant/general', request)
}

/** Health-aware mode: the backend adds the profile's recorded facts and the chosen conditions only. */
export function suggestHealthAware(
  profileId: string,
  request: GeneralMealRequest & { conditionIds: string[] },
): Promise<MealSuggestions> {
  return apiPost(`/profiles/${profileId}/meal-assistant/health-aware`, request)
}

export function saveRecipe(
  profileId: string,
  recipe: { title: string; ingredients: string[]; steps: string[]; mode: MealMode; promptVersion: string },
): Promise<SavedRecipe> {
  return apiPost(`/profiles/${profileId}/recipes`, recipe)
}

export function addRecipeToFoodLog(
  profileId: string,
  recipeId: string,
  when: { mealType: MealType | null; occurredOn: string | null; occurredTime: string | null },
): Promise<{ id: string }> {
  return apiPost(`/profiles/${profileId}/food-log/from-recipe/${recipeId}`, when)
}

/** Splits typed text on commas and new lines, dropping empty entries and repeats. */
export function splitList(text: string): string[] {
  const seen = new Set<string>()
  const items: string[] = []
  for (const part of text.split(/[,\n]/)) {
    const item = part.trim()
    const key = item.toLowerCase()
    if (item && !seen.has(key)) {
      seen.add(key)
      items.push(item)
    }
  }
  return items
}
