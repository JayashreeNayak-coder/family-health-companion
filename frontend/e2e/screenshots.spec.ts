import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

/**
 * Creates a synthetic family through the real API, then captures every page at the project's
 * width. The images are for looking at, not for comparison, so the test only fails when a page
 * doesn't render or a step can't be reached.
 */

// Node's Buffer, reached without adding @types/node for this one use.
const NodeBuffer = (globalThis as unknown as { Buffer: { from(text: string): Uint8Array } }).Buffer

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

async function shot(page: Page, name: string, projectName: string) {
  await page.waitForLoadState('networkidle')
  await expect(page.getByRole('status').filter({ hasText: 'Loading' })).toHaveCount(0)
  await page.screenshot({ path: `e2e/screenshots/${projectName}/${name}.png`, fullPage: true })
  // The page itself must never scroll sideways, at any width.
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
  expect(overflow, `${name} is ${overflow}px wider than the screen`).toBeLessThanOrEqual(0)
}

test('captures every page', async ({ page }, info) => {
  await page.goto('/')
  await shot(page, '01-sign-in', info.project.name)

  await seed(page.request)
  await page.goto('/')
  await shot(page, '02-home', info.project.name)

  await page.getByRole('button', { name: 'Ravi Demo' }).first().click()
  const tabs = [
    'Details',
    'Health history',
    'Visits and symptoms',
    'Medications',
    'Lab results',
    'Food log',
    'Documents',
    'Timeline',
    'Access',
  ]
  for (const [index, tab] of tabs.entries()) {
    await page.getByRole('navigation', { name: 'Profile sections' }).getByRole('button', { name: tab }).click()
    await shot(page, `${String(index + 3).padStart(2, '0')}-profile-${tab.toLowerCase().replaceAll(' ', '-')}`, info.project.name)
  }

  await page.getByRole('navigation', { name: 'Profile sections' }).getByRole('button', { name: 'Health history' }).click()
  await page.getByRole('button', { name: /^Delete Penicillin/ }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.screenshot({ path: `e2e/screenshots/${info.project.name}/20-confirm-dialog.png` })
})
