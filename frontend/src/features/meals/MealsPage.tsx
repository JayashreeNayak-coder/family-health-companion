import { CircleAlert, Clock, Sparkles, UsersRound, UtensilsCrossed } from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { errorMessage } from '../../api/client'
import {
  splitList,
  suggestGeneral,
  suggestHealthAware,
  type MealMode,
  type MealSuggestions,
  type SuggestedRecipe,
} from '../../api/meals'
import { AiBadge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import { Dialog } from '../../components/Dialog'
import { EmptyState, ErrorNote } from '../../components/Feedback'
import { inputClass } from '../../components/formStyles'
import { useToast } from '../../components/Toast'
import { useDocumentTitle } from '../../components/useDocumentTitle'
import { useNavProfiles } from '../shell/NavProfiles'
import { PersonFacts } from './PersonFacts'
import { SaveToFoodLogForm } from './SaveToFoodLogForm'

const labelClass = 'block text-sm font-medium text-slate-800'

const MAX_ITEMS = 20
const MAX_ITEM_LENGTH = 80

function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <div>
      <label className={labelClass}>
        {label}
        {children}
      </label>
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </div>
  )
}

/** Checks a typed list against the backend's limits, so the user sees the problem before sending. */
function listProblem(name: string, items: string[]): string | null {
  if (items.length > MAX_ITEMS) {
    return `List at most ${MAX_ITEMS} ${name}.`
  }
  const long = items.find((item) => item.length > MAX_ITEM_LENGTH)
  return long ? `Keep each of the ${name} under ${MAX_ITEM_LENGTH} characters.` : null
}

function RecipeCard({ recipe, onSave }: { recipe: SuggestedRecipe; onSave: () => void }) {
  return (
    <Card
      title={recipe.title}
      description={
        <span className="flex flex-wrap gap-x-4 gap-y-1">
          <span className="inline-flex items-center gap-1">
            <Clock aria-hidden="true" className="size-4" />
            {recipe.minutes ? `About ${recipe.minutes} minutes` : 'Time not given'}
          </span>
          <span className="inline-flex items-center gap-1">
            <UsersRound aria-hidden="true" className="size-4" />
            {recipe.servings ? `Serves ${recipe.servings}` : 'Servings not given'}
          </span>
        </span>
      }
      actions={
        <Button icon={UtensilsCrossed} onClick={onSave} aria-label={`Save to food log: ${recipe.title}`}>
          Save to food log
        </Button>
      }
    >
      {recipe.avoidMatches.length > 0 && (
        <p className="mb-4 flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          <CircleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
          <span>
            <strong className="font-semibold">Mentions something you asked to avoid:</strong>{' '}
            {recipe.avoidMatches.join(', ')}
          </span>
        </p>
      )}
      <div className="grid gap-6 md:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        <div>
          <h4 className="text-sm font-semibold text-slate-900">Ingredients</h4>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-700">
            {recipe.ingredients.map((item, index) => (
              <li key={index}>{item}</li>
            ))}
          </ul>
        </div>
        <div>
          <h4 className="text-sm font-semibold text-slate-900">Steps</h4>
          <ol className="mt-2 list-decimal space-y-2 pl-5 text-sm text-slate-700">
            {recipe.steps.map((step, index) => (
              <li key={index}>{step}</li>
            ))}
          </ol>
        </div>
      </div>
    </Card>
  )
}

/**
 * The meal assistant. General mode sends only what is typed here; it never reads a family
 * member's records. Health-aware mode adds one person's recorded allergies, restrictions, and
 * preferences, and only the conditions the user ticks (docs/02 section 3.8). The typed fields
 * stay in this page's state, so switching modes keeps them.
 */
export function MealsPage() {
  useDocumentTitle('Meal ideas')
  const toast = useToast()
  const [ingredients, setIngredients] = useState('')
  const [avoid, setAvoid] = useState('')
  const [maxMinutes, setMaxMinutes] = useState('')
  const [preferences, setPreferences] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<MealSuggestions | null>(null)
  const [saving, setSaving] = useState<SuggestedRecipe | null>(null)
  const { profiles } = useNavProfiles()
  const [mode, setMode] = useState<MealMode>('GENERAL')
  const [personId, setPersonId] = useState('')
  const [conditionIds, setConditionIds] = useState<string[]>([])
  const [confirmed, setConfirmed] = useState(false)
  const [resultPersonId, setResultPersonId] = useState<string | null>(null)
  const person = profiles?.find((profile) => profile.id === personId) ?? null

  // Choosing another person clears the ticked conditions and the confirmation.
  // Results always match the mode and person shown, so old ones are cleared on a change.
  function choosePerson(id: string) {
    setPersonId(id)
    setConditionIds([])
    setConfirmed(false)
    setResult(null)
  }

  function chooseMode(next: MealMode) {
    setMode(next)
    setResult(null)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const ingredientList = splitList(ingredients)
    const avoidList = splitList(avoid)
    const problem =
      (ingredientList.length === 0 ? 'Type at least one ingredient.' : null) ??
      listProblem('ingredients', ingredientList) ??
      listProblem('ingredients to avoid', avoidList) ??
      (mode === 'HEALTH_AWARE' && !person ? 'Choose who the meal is for.' : null) ??
      (mode === 'HEALTH_AWARE' && !confirmed ? 'Confirm that this person’s records may be sent.' : null)
    if (problem) {
      setError(problem)
      return
    }
    setBusy(true)
    setError(null)
    try {
      const request = {
        ingredients: ingredientList,
        avoid: avoidList,
        maxMinutes: maxMinutes ? Number(maxMinutes) : null,
        preferences: preferences.trim() || null,
      }
      if (mode === 'HEALTH_AWARE' && person) {
        setResult(await suggestHealthAware(person.id, { ...request, conditionIds }))
        setResultPersonId(person.id)
      } else {
        setResult(await suggestGeneral(request))
        setResultPersonId(null)
      }
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900">Meal ideas</h1>
        <p className="mt-1 text-sm text-slate-600">
          Type what you have, and get recipe ideas you can save to someone&rsquo;s food log.
        </p>
      </header>

      <Card
        title="What do you have?"
        description={
          mode === 'GENERAL'
            ? "General ideas. This doesn't use anyone's health records, so list anything to avoid yourself."
            : 'Ideas for one person, using what is recorded for them. They are not confirmed safe for them.'
        }
      >
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <fieldset>
            <legend className={labelClass}>Who are the ideas for?</legend>
            <div className="mt-2 flex flex-wrap gap-2">
              {(
                [
                  ['GENERAL', 'Anyone (general)'],
                  ['HEALTH_AWARE', 'A family member'],
                ] as const
              ).map(([value, label]) => (
                <label
                  key={value}
                  className={`flex min-h-10 cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm font-medium ${
                    mode === value
                      ? 'border-brand-600 bg-brand-50 text-brand-900'
                      : 'border-slate-300 bg-white text-slate-700'
                  }`}
                >
                  <input
                    type="radio"
                    name="meal-mode"
                    className="size-4 accent-brand-600"
                    checked={mode === value}
                    onChange={() => chooseMode(value)}
                  />
                  {label}
                </label>
              ))}
            </div>
          </fieldset>
          {mode === 'HEALTH_AWARE' && (
            <>
              <Field label="Person">
                <select className={inputClass} value={personId} onChange={(event) => choosePerson(event.target.value)}>
                  <option value="">Choose a person</option>
                  {profiles?.map((profile) => (
                    <option key={profile.id} value={profile.id}>
                      {profile.fullName}
                    </option>
                  ))}
                </select>
              </Field>
              {person && (
                <PersonFacts
                  key={person.id}
                  profileId={person.id}
                  name={person.fullName}
                  conditionIds={conditionIds}
                  onConditionIdsChange={setConditionIds}
                  confirmed={confirmed}
                  onConfirmedChange={setConfirmed}
                />
              )}
            </>
          )}
          <Field label="Ingredients" hint="Separate with commas or new lines, for example: rice, lentils, spinach.">
            <textarea
              className={`${inputClass} min-h-20`}
              value={ingredients}
              onChange={(event) => setIngredients(event.target.value)}
              required
            />
          </Field>
          <Field
            label="Ingredients to avoid"
            hint={
              mode === 'GENERAL'
                ? 'For example, allergies. Recipes that mention them are flagged.'
                : 'Anything else to leave out today. Recorded allergies are already included.'
            }
          >
            <input className={inputClass} value={avoid} onChange={(event) => setAvoid(event.target.value)} />
          </Field>
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Most minutes to cook" hint="Optional, 5 to 600.">
              <input
                className={inputClass}
                type="number"
                inputMode="numeric"
                min={5}
                max={600}
                value={maxMinutes}
                onChange={(event) => setMaxMinutes(event.target.value)}
              />
            </Field>
            <Field label="Preferences" hint="Optional, for example: South Indian, one pot, no oven.">
              <input
                className={inputClass}
                maxLength={300}
                value={preferences}
                onChange={(event) => setPreferences(event.target.value)}
              />
            </Field>
          </div>
          <p className="flex items-start gap-2 rounded-lg bg-violet-50 px-3 py-2 text-sm text-violet-900">
            <Sparkles aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            <span>
              What you type here is sent to an outside AI service. Don&rsquo;t include names or other personal
              details.
            </span>
          </p>
          <ErrorNote message={error} />
          <Button type="submit" variant="primary" size="md" icon={Sparkles} busy={busy}>
            {busy ? 'Thinking of ideas…' : 'Suggest recipes'}
          </Button>
        </form>
      </Card>

      {result ? (
        <section aria-labelledby="suggestions-heading" className="space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <h2 id="suggestions-heading" className="text-lg font-semibold text-slate-900">
              Suggestions
            </h2>
            <AiBadge />
          </div>
          <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            <p className="font-medium">{result.disclaimer}</p>
            <ul className="mt-1 list-disc space-y-0.5 pl-5">
              {result.limitations.map((limitation) => (
                <li key={limitation}>{limitation}</li>
              ))}
            </ul>
          </div>
          {result.considered && (
            <div className="rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700">
              <p className="font-medium text-slate-900">Records used for these ideas</p>
              <dl className="mt-1 grid gap-x-4 gap-y-1 sm:grid-cols-[max-content_1fr]">
                {(
                  [
                    ['Allergies', result.considered.allergies],
                    ['Restrictions', result.considered.restrictions],
                    ['Preferences', result.considered.preferences],
                    ['Conditions', result.considered.conditions],
                  ] as const
                ).map(([label, items]) => (
                  <div key={label} className="contents">
                    <dt className="font-medium">{label}</dt>
                    <dd>{items.length > 0 ? items.join(', ') : <span className="text-slate-500 italic">None</span>}</dd>
                  </div>
                ))}
              </dl>
            </div>
          )}
          {result.recipes.map((recipe, index) => (
            <RecipeCard key={`${index}-${recipe.title}`} recipe={recipe} onSave={() => setSaving(recipe)} />
          ))}
        </section>
      ) : (
        !busy && (
          <EmptyState icon={UtensilsCrossed} title="No suggestions yet">
            Ideas appear here after you list some ingredients.
          </EmptyState>
        )
      )}

      <Dialog open={saving !== null} title="Save to food log" onClose={() => setSaving(null)}>
        {saving && result && (
          <SaveToFoodLogForm
            recipe={saving}
            mode={result.mode}
            promptVersion={result.promptVersion}
            defaultProfileId={resultPersonId}
            onSaved={(name) => {
              setSaving(null)
              toast(`Added to ${name}'s food log`)
            }}
          />
        )}
      </Dialog>
    </div>
  )
}
