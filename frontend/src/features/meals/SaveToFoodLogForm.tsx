import { useState, type FormEvent } from 'react'
import { errorMessage } from '../../api/client'
import { addRecipeToFoodLog, saveRecipe, type MealMode, type MealType, type SuggestedRecipe } from '../../api/meals'
import { Button } from '../../components/Button'
import { ErrorNote } from '../../components/Feedback'
import { inputClass } from '../../components/formStyles'
import { useNavProfiles } from '../shell/NavProfiles'

const labelClass = 'block text-sm font-medium text-slate-800'

const MEALS: { value: MealType | ''; label: string }[] = [
  { value: '', label: 'Not specified' },
  { value: 'BREAKFAST', label: 'Breakfast' },
  { value: 'LUNCH', label: 'Lunch' },
  { value: 'DINNER', label: 'Dinner' },
  { value: 'SNACK', label: 'Snack' },
  { value: 'OTHER', label: 'Other' },
]

function localToday() {
  return new Date().toLocaleDateString('en-CA')
}

type Props = {
  recipe: SuggestedRecipe
  mode: MealMode
  promptVersion: string
  /** Who the ideas were for, offered first when this account can edit their records. */
  defaultProfileId?: string | null
  /** Called with the person's name once the entry is in their food log. */
  onSaved: (name: string) => void
}

/**
 * Keeps the recipe on a chosen profile, then adds it to that profile's food log. Only profiles
 * this account can edit are offered; the backend checks access again.
 */
export function SaveToFoodLogForm({ recipe, mode, promptVersion, defaultProfileId, onSaved }: Props) {
  const { profiles } = useNavProfiles()
  const editable = (profiles ?? []).filter((profile) => profile.accessLevel !== 'VIEW')
  const [profileId, setProfileId] = useState(
    editable.find((profile) => profile.id === defaultProfileId)?.id ?? editable[0]?.id ?? '',
  )
  const [mealType, setMealType] = useState<MealType | ''>('')
  const [occurredOn, setOccurredOn] = useState(localToday)
  const [occurredTime, setOccurredTime] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Kept so a retry after a failed food log step doesn't save the recipe twice.
  const [saved, setSaved] = useState<{ profileId: string; recipeId: string } | null>(null)

  if (editable.length === 0) {
    return (
      <p className="text-sm text-slate-700">
        You can save to a food log only for people whose records you can edit. Add a person from Home first.
      </p>
    )
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (occurredTime && !occurredOn) {
      setError('A time needs a date.')
      return
    }
    const person = editable.find((profile) => profile.id === profileId)
    if (!person) {
      setError('Choose a person.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      let recipeId = saved?.profileId === person.id ? saved.recipeId : null
      if (!recipeId) {
        const created = await saveRecipe(person.id, {
          title: recipe.title,
          ingredients: recipe.ingredients,
          steps: recipe.steps,
          mode,
          promptVersion,
        })
        recipeId = created.id
        setSaved({ profileId: person.id, recipeId })
      }
      await addRecipeToFoodLog(person.id, recipeId, {
        mealType: mealType || null,
        occurredOn: occurredOn || null,
        occurredTime: occurredTime || null,
      })
      onSaved(person.fullName)
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <p className="text-sm text-slate-700">
        <span className="font-medium text-slate-900">{recipe.title}</span> is kept as an AI-generated recipe and
        added to the food log.
      </p>
      <label className={labelClass}>
        Person
        <select className={inputClass} value={profileId} onChange={(event) => setProfileId(event.target.value)}>
          {editable.map((profile) => (
            <option key={profile.id} value={profile.id}>
              {profile.fullName}
            </option>
          ))}
        </select>
      </label>
      <label className={labelClass}>
        Meal
        <select
          className={inputClass}
          value={mealType}
          onChange={(event) => setMealType(event.target.value as MealType | '')}
        >
          {MEALS.map((meal) => (
            <option key={meal.value} value={meal.value}>
              {meal.label}
            </option>
          ))}
        </select>
      </label>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className={labelClass}>
          Date
          <input
            className={inputClass}
            type="date"
            value={occurredOn}
            onChange={(event) => setOccurredOn(event.target.value)}
          />
        </label>
        <label className={labelClass}>
          Time
          <input
            className={inputClass}
            type="time"
            value={occurredTime}
            onChange={(event) => setOccurredTime(event.target.value)}
          />
        </label>
      </div>
      <ErrorNote message={error} />
      <Button type="submit" variant="primary" size="md" busy={busy}>
        Save to food log
      </Button>
    </form>
  )
}
