import AxeBuilder from '@axe-core/playwright'
import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

/**
 * Creates a synthetic family through the real API, then captures every page at the project's
 * width. The images are for looking at, not for comparison, so the test only fails when a page
 * doesn't render or a step can't be reached.
 */

// Node's Buffer, reached without adding @types/node for this one use.
const NodeBuffer = (globalThis as unknown as { Buffer: { from(text: string): Uint8Array } }).Buffer

/** A tiny synthetic avatar (96 px PNG), for the photo uploads. */
const AVATAR = NodeBuffer.from('iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAIAAABt+uBvAAABL0lEQVR42u3asXHDMBBE0a3EJTp2OS7HRbgEBU6UqwOZMwSOOPDN/ApeBtzm9/GnNwUBIECAAAECBAiQAAECBAgQIECABAgQIECAAB3q+fP9vpsC/euyiFS66FxllC40VzGlnU6xUTrqVBqlqU6ZEaAFgCbp1BiltU6BESBAKwMV6Mw2AgQIECBAgAABAgQIkLcYIED3A/Kj6E8akLuYy6rbvHXHXdYd9kEWZjaKAtQH6OPr80i3AzrosoJUutBcxZReNPVMaapTZpS+OjVGaa1TYJTuOrONsoHOVKPsoTPPKNvoTDLKTjozjLKZznAjQIVAi+iMNQJUBbSUzkCj7KozyggQoMuBltUZYgRoMtDiOueNAAG6EKiFzkkjQIAALQrUSOeMESBAgAABWhjoBeIOy2mm5fDwAAAAAElFTkSuQmCC', 'base64')

const PDF = NodeBuffer.from(
  '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj ' +
    '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n',
)

async function csrf(request: APIRequestContext): Promise<Record<string, string>> {
  const response = await request.get('/api/v1/auth/csrf')
  const body = (await response.json()) as { headerName: string; token: string }
  return { [body.headerName]: body.token }
}

async function post<T>(request: APIRequestContext, path: string, data: unknown): Promise<T> {
  const response = await request.post(`/api/v1${path}`, { data, headers: await csrf(request) })
  expect(response.ok(), `${path} ${response.status()}`).toBeTruthy()
  return (await response.json()) as T
}

async function put(request: APIRequestContext, path: string, data: unknown) {
  const response = await request.put(`/api/v1${path}`, { data, headers: await csrf(request) })
  expect(response.ok(), `${path} ${response.status()}`).toBeTruthy()
}

/** A demo family with a little of everything, all clearly synthetic. */
async function seed(request: APIRequestContext) {
  const email = `screens-${Date.now()}-${Math.round(Math.random() * 1e6)}@example.test`
  const password = 'synthetic-password-1'
  await post(request, '/auth/register', { email, password, displayName: 'Asha Demo' })
  await post(request, '/auth/login', { email, password })
  const family = await post<{ id: string }>(request, '/families', { name: 'Demo Family' })
  const child = await post<{ id: string }>(request, `/families/${family.id}/profiles`, {
    fullName: 'Ravi Demo',
    kind: 'DEPENDENT',
    dateOfBirth: '2016-04-12',
  })
  await post(request, `/families/${family.id}/profiles`, { fullName: 'Asha Demo', kind: 'SELF' })
  const base = `/profiles/${child.id}`
  await post(request, `${base}/allergies`, { allergen: 'Penicillin (synthetic)', reactionAsRecorded: 'Rash' })
  await post(request, `${base}/conditions`, { name: 'Seasonal allergy (synthetic)', status: 'ACTIVE', onsetOn: '2024-03-01' })
  await post(request, `${base}/dietary-restrictions`, { kind: 'PREFERENCE', description: 'Vegetarian' })
  await post(request, `${base}/emergency-contacts`, { name: 'Meera Demo', relationship: 'Aunt', phone: '+91 555 0100' })
  await post(request, `${base}/visits`, { reason: 'Follow-up', occurredOn: '2026-03-14', doctorName: 'Dr. Demo', facility: 'Demo Clinic' })
  await post(request, `${base}/symptoms`, { description: 'Sneezing', severityAsReported: 'Mild', occurredOn: '2026-03-12' })
  const medication = await post<{ id: string }>(request, `${base}/medications`, {
    name: 'Cetirizine (synthetic)',
    dosageAsRecorded: '5 mg',
    status: 'ACTIVE',
    startOn: '2026-03-14',
  })
  await put(request, `${base}/medications/${medication.id}/schedules`, {
    schedules: [{ timeOfDay: '20:00', timeZone: 'Asia/Kolkata', reminderEnabled: true }],
  })
  for (const [value, date] of [
    [5.6, '2025-09-10'],
    [5.8, '2026-01-10'],
    [5.7, '2026-03-10'],
  ] as const) {
    await post(request, `${base}/lab-results`, { testName: 'HbA1c', valueNumeric: value, unit: '%', resultOn: date, referenceRangeAsPrinted: '4.0-5.6' })
  }
  await post(request, `${base}/food-log`, { description: 'Idli and sambar', mealType: 'BREAKFAST', occurredOn: '2026-03-14' })
  const upload = await request.post(`/api/v1${base}/documents`, {
    headers: await csrf(request),
    multipart: {
      file: { name: 'demo-prescription.pdf', mimeType: 'application/pdf', buffer: PDF as never },
      category: 'PRESCRIPTION',
      description: 'Synthetic prescription',
    },
  })
  expect(upload.ok()).toBeTruthy()
}

/** Runs the axe accessibility rules for WCAG 2.2 A and AA, and fails with a readable list. */
async function accessible(page: Page, name: string) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze()
  const problems = results.violations.map(
    (violation) =>
      `${violation.id} (${violation.impact}): ${violation.help} -> ${violation.nodes
        .slice(0, 3)
        .map((node) => node.target.join(' '))
        .join(' | ')}`,
  )
  expect.soft(problems, `${name} accessibility`).toEqual([])
}

async function shot(page: Page, name: string, projectName: string) {
  await page.waitForLoadState('networkidle')
  await expect(page.getByRole('status').filter({ hasText: 'Loading' })).toHaveCount(0)
  await page.screenshot({ path: `e2e/screenshots/${projectName}/${name}.png`, fullPage: true })
  // The page itself must never scroll sideways, at any width.
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow, `${name} is ${overflow}px wider than the screen`).toBeLessThanOrEqual(0)
  await accessible(page, name)
}

test('captures every page', async ({ page }, info) => {
  await page.goto('/')
  await shot(page, '01-sign-in', info.project.name)
  await page.getByRole('button', { name: 'Show password' }).click()
  await page.goto('/forgot-password')
  await shot(page, '01a-forgot-password', info.project.name)
  await page.goto('/reset-password#token=synthetic-token')
  await shot(page, '01b-reset-password', info.project.name)

  await seed(page.request)
  await page.goto('/')
  await shot(page, '02-home', info.project.name)

  // Managing a family: name, photo, and the delete rule.
  await page.getByRole('button', { name: 'Manage family' }).click()
  const manage = page.getByRole('dialog', { name: 'Manage Demo Family' })
  await manage.getByLabel('Add a photo').setInputFiles({ name: 'family.png', mimeType: 'image/png', buffer: AVATAR })
  await expect(manage.getByRole('button', { name: 'Remove photo' })).toBeVisible()
  await page.screenshot({ path: `e2e/screenshots/${info.project.name}/02c-manage-family.png` })
  await accessible(page, 'manage family dialog')
  await page.keyboard.press('Escape')

  await page.getByRole('button', { name: 'Invite someone' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.screenshot({ path: `e2e/screenshots/${info.project.name}/02a-invite-dialog.png` })
  await accessible(page, 'invite dialog')
  await page.keyboard.press('Escape')

  if (info.project.name === 'phone' || info.project.name === 'narrow') {
    await page.getByRole('button', { name: 'Open menu' }).click()
    await page.getByRole('dialog', { name: 'Menu' }).getByRole('link', { name: 'Ravi Demo' }).waitFor()
    await page.screenshot({ path: `e2e/screenshots/${info.project.name}/02b-phone-menu.png` })
    await page.keyboard.press('Escape')
  }

  await page.getByRole('main').getByRole('link', { name: 'Ravi Demo' }).click()
  const tabs = [
    'Overview',
    'Details',
    'Health history',
    'Visits and symptoms',
    'Medications',
    'Lab results',
    'Food log',
    'Documents',
    'Timeline',
    'Summaries',
    'Emergency card',
    'Access',
  ]
  for (const [index, tab] of tabs.entries()) {
    await page.getByRole('navigation', { name: 'Profile sections' }).getByRole('link', { name: tab }).click()
    await shot(page, `${String(index + 3).padStart(2, '0')}-profile-${tab.toLowerCase().replaceAll(' ', '-')}`, info.project.name)
  }

  // A profile photo, then Home showing both photos.
  await page.getByRole('navigation', { name: 'Profile sections' }).getByRole('link', { name: 'Details' }).click()
  await page.getByLabel('Add a photo').setInputFiles({ name: 'ravi.png', mimeType: 'image/png', buffer: AVATAR })
  await expect(page.getByRole('button', { name: 'Remove photo' })).toBeVisible()
  await shot(page, '15a-profile-photo', info.project.name)
  await page.goto('/')
  await expect(page.locator('main img').first()).toBeVisible()
  await shot(page, '15b-home-with-photos', info.project.name)
  await page.getByRole('main').getByRole('link', { name: 'Ravi Demo' }).click()

  // The emergency card: choose lines, save, turn on, then open the public page from the link.
  await page.getByRole('navigation', { name: 'Profile sections' }).getByRole('link', { name: 'Emergency card' }).click()
  await page.getByLabel('Name on the card').fill('Ravi')
  await page.getByLabel('Penicillin (synthetic): Rash').check()
  await page.getByLabel('Cetirizine (synthetic), 5 mg').check()
  await page.getByLabel('Meera Demo (Aunt): +91 555 0100').check()
  await page.getByRole('button', { name: 'Save card' }).click()
  await expect(page.getByRole('button', { name: 'Turn on' })).toBeEnabled()
  await page.getByRole('button', { name: 'Turn on' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Turn on' }).click()
  const cardLink = await page.getByRole('textbox', { name: 'Emergency card link' }).inputValue()
  await shot(page, '17a-emergency-card-link', info.project.name)
  await page.goto(cardLink.replace(/^https?:\/\/[^/]+/, ''))
  await expect(page.getByText('Penicillin (synthetic): Rash')).toBeVisible()
  await shot(page, '17b-emergency-card-public', info.project.name)
  await page.goBack()

  // The lab chart's hover readout.
  await page.getByRole('navigation', { name: 'Profile sections' }).getByRole('link', { name: 'Lab results' }).click()
  const chart = page.getByRole('img', { name: /HbA1c from/ })
  await chart.scrollIntoViewIfNeeded()
  const box = await chart.boundingBox()
  if (box) {
    await page.mouse.move(box.x + box.width * 0.55, box.y + box.height / 2)
  }
  await page.screenshot({ path: `e2e/screenshots/${info.project.name}/18-lab-chart-hover.png` })

  // A page address survives a refresh.
  await page.getByRole('navigation', { name: 'Profile sections' }).getByRole('link', { name: 'Health history' }).click()
  await page.reload()
  await expect(page.getByRole('link', { name: 'Health history', exact: true })).toHaveAttribute('aria-current', 'page')
  await page.getByRole('button', { name: /^Edit Penicillin/ }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.screenshot({ path: `e2e/screenshots/${info.project.name}/19-edit-dialog.png` })
  await accessible(page, 'edit dialog')
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: /^Delete Penicillin/ }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.screenshot({ path: `e2e/screenshots/${info.project.name}/20-confirm-dialog.png` })
  await page.keyboard.press('Escape')

  // Meal ideas. The AI answer is stubbed in the browser, so no AI key is needed. Saving uses the real backend.
  await page.route('**/api/v1/meal-assistant/general', (route) => route.fulfill({ json: MEAL_SUGGESTIONS }))
  await page.goto('/meals')
  await shot(page, '21-meal-ideas', info.project.name)
  await page.getByLabel('Ingredients', { exact: true }).fill('rice, lentils, spinach, peanuts')
  await page.getByLabel('Ingredients to avoid').fill('peanuts')
  await page.getByRole('button', { name: 'Suggest recipes' }).click()
  await page.getByRole('heading', { name: 'Spinach dal' }).waitFor()
  await shot(page, '22-meal-suggestions', info.project.name)
  await page.getByRole('button', { name: 'Save to food log: Spinach dal' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.screenshot({ path: `e2e/screenshots/${info.project.name}/23-save-recipe-dialog.png` })
  await accessible(page, 'save recipe dialog')
  await page.getByRole('dialog').getByRole('button', { name: 'Save to food log' }).click()
  await expect(page.getByText(/^Added to .*food log$/)).toBeVisible()

  // Health-aware mode reads the person's real records from the backend; the AI answer is stubbed.
  await page.route('**/api/v1/profiles/*/meal-assistant/health-aware', (route) =>
    route.fulfill({ json: HEALTH_AWARE_SUGGESTIONS }),
  )
  await page.getByLabel('A family member').check()
  await page.getByLabel('Person').selectOption({ label: 'Ravi Demo' })
  await page.getByLabel(/I understand that Ravi Demo/).check()
  await shot(page, '24-meal-health-aware-form', info.project.name)
  await page.getByRole('button', { name: 'Suggest recipes' }).click()
  await page.getByText('Records used for these ideas').waitFor()
  await shot(page, '25-meal-health-aware-suggestions', info.project.name)

  // A summary. The AI answer is stubbed; the list, storage, and screen are real.
  await page.route('**/api/v1/profiles/*/summaries', async (route) => {
    if (route.request().method() !== 'POST') {
      return route.fallback()
    }
    return route.fulfill({ status: 201, json: { ...SUMMARY, profileId: 'stub', createdAt: new Date().toISOString() } })
  })
  await page.goto('/')
  await page.getByRole('main').getByRole('link', { name: 'Ravi Demo' }).click()
  await page.getByRole('navigation', { name: 'Profile sections' }).getByRole('link', { name: 'Summaries' }).click()
  await page.getByLabel(/I understand that Ravi Demo/).check()
  await page.getByRole('button', { name: 'Summarise records' }).click()
  await page.getByText('Records that disagree').waitFor()
  await shot(page, '26-summary', info.project.name)
})

const SUMMARY = {
  id: 'stub-summary',
  contentType: 'AI_GENERATED',
  promptVersion: 'summaries-v1',
  model: 'stub',
  periodFrom: '2025-11-09',
  periodTo: '2026-11-09',
  points: [
    {
      topic: 'ALLERGIES',
      text: 'Allergy to Penicillin (synthetic).',
      sources: [{ ref: 'R1', type: 'ALLERGY', id: 'a', date: null, title: 'Penicillin (synthetic)', detail: null, sourceType: 'USER_REPORTED' }],
    },
    {
      topic: 'LAB_RESULTS',
      text: 'HbA1c was 6.1 % on 2026-02-10 and 5.8 % on 2026-08-10, as recorded.',
      sources: [
        { ref: 'R2', type: 'LAB_RESULT', id: 'b', date: '2026-02-10', title: 'HbA1c', detail: '6.1 %', sourceType: 'DOCUMENTED' },
        { ref: 'R3', type: 'LAB_RESULT', id: 'c', date: '2026-08-10', title: 'HbA1c', detail: '5.8 %', sourceType: 'USER_REPORTED' },
      ],
    },
  ],
  gaps: ['The medication Synthetic tablet has no dosage recorded.'],
  conflicts: [
    {
      text: 'Two records give different start dates for Synthetic tablet.',
      sources: [
        { ref: 'R4', type: 'MEDICATION', id: 'd', date: '2026-01-05', title: 'Synthetic tablet', detail: null, sourceType: 'USER_REPORTED' },
        { ref: 'R5', type: 'MEDICATION', id: 'e', date: '2026-01-15', title: 'Synthetic tablet', detail: null, sourceType: 'DOCUMENTED' },
      ],
    },
  ],
  limitations: [
    'This summary only restates recorded information. It does not interpret results or give advice, and records may be incomplete.',
    'No symptoms are recorded in this period.',
    '"From document" means taken from an attached document. The documents were not checked.',
  ],
  disclaimer:
    'AI-generated summary of recorded information. It is not medical advice, a diagnosis, or a medical certificate. Talk to a qualified healthcare professional about health questions.',
}

const HEALTH_AWARE_SUGGESTIONS = {
  contentType: 'AI_GENERATED',
  mode: 'HEALTH_AWARE',
  promptVersion: 'recipes-v1',
  recipes: [
    {
      title: 'Vegetable pulao',
      minutes: 30,
      servings: 2,
      ingredients: ['1 cup rice', '1 cup mixed vegetables', '1 tbsp oil', 'Salt to taste'],
      steps: ['Fry the vegetables in the oil.', 'Add the rice, water, and salt, and cook until done.'],
      avoidMatches: [],
    },
  ],
  limitations: [
    'Based only on what is recorded for this person. Records may be incomplete or out of date.',
    'No dietary restrictions or preferences are recorded for this person.',
    'No health conditions were included.',
    'Recorded allergies and ingredients to avoid were checked by simple word matching, which can miss other names and foods made from them. Check every ingredient yourself.',
  ],
  disclaimer:
    'These are AI-generated recipe ideas, not medical or nutrition advice. They are not confirmed safe for this person.',
  considered: { allergies: ['Penicillin'], restrictions: [], preferences: [], conditions: [] },
}

const MEAL_SUGGESTIONS = {
  contentType: 'AI_GENERATED',
  mode: 'GENERAL',
  promptVersion: 'recipes-v1',
  recipes: [
    {
      title: 'Spinach dal',
      minutes: 35,
      servings: 3,
      ingredients: ['1 cup yellow lentils', '2 cups chopped spinach', '1 tsp cumin seeds', '1 tbsp oil', 'Salt to taste'],
      steps: [
        'Rinse the lentils and cook them in three cups of water until soft.',
        'Heat the oil, add the cumin seeds, and let them sizzle.',
        'Add the spinach and cook until it wilts, then stir in the lentils and salt.',
      ],
      avoidMatches: [],
    },
    {
      title: 'Peanut rice',
      minutes: 20,
      servings: 2,
      ingredients: ['2 cups cooked rice', '1/4 cup roasted peanuts', '1 tsp mustard seeds', 'Curry leaves'],
      steps: ['Temper the mustard seeds and curry leaves in oil.', 'Add the peanuts and rice, and toss well.'],
      avoidMatches: ['peanuts'],
    },
  ],
  limitations: [
    "General mode doesn't use any family member's health records.",
    'Ingredients to avoid were checked by simple word matching, which can miss other names and foods made from them. Check every ingredient yourself.',
  ],
  disclaimer: 'These are AI-generated recipe ideas, not medical or nutrition advice.',
}
