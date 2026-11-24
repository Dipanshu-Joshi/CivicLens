import { expect, test } from '@playwright/test'

const CITIZEN_EMAIL = 'citizen@demo.com'
const ADMIN_EMAIL = 'admin@demo.com'

test('demo accounts route by role, persist on refresh, switch and log out without API calls', async ({
  page,
}) => {
  let apiRequests = 0
  await page.route('**/api/v1/**', (route) => {
    apiRequests += 1
    return route.abort()
  })

  await page.goto('/login')
  await expect(page.getByText('DEMO MODE', { exact: false }).first()).toBeVisible()

  await page.getByRole('button', { name: /Sign in as Citizen/i }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByRole('heading', { name: /overview/i }).first()).toBeVisible()

  await page.locator('#demo-account-switcher').selectOption(ADMIN_EMAIL)
  await expect(page).toHaveURL(/\/admin$/)
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('civicsense.demo.session.v1') ?? '{}').role)).toBe(
    'SUPER_ADMIN',
  )

  await page.reload()
  await expect(page).toHaveURL(/\/admin$/)
  await expect(page.getByText('DEMO MODE', { exact: false }).first()).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).first().click()
  await expect(page).toHaveURL(/\/login$/)
  expect(await page.evaluate(() => localStorage.getItem('civicsense.demo.session.v1'))).toBeNull()
  expect(apiRequests).toBe(0)
})

test('demo comments update browser state and RESET DEMO DATA restores the seed', async ({ page }) => {
  await page.goto('/login')
  await page.getByRole('button', { name: /Sign in as Citizen/i }).click()
  await expect(page).toHaveURL(/\/dashboard$/)

  const marker = 'Local demo E2E comment'
  await page.goto('/complaints/complaint-101')
  await page.locator('#citizen-comment').fill(marker)
  await page.getByRole('button', { name: 'Send', exact: true }).click()
  await expect(page.getByText(marker)).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('civicsense.demo.state.v1'))).toContain(marker)

  page.once('dialog', (dialog) => void dialog.accept())
  await page.getByRole('button', { name: /RESET DEMO DATA/i }).first().click()
  await expect(page).toHaveURL(/\/login$/)
  expect(await page.evaluate(() => localStorage.getItem('civicsense.demo.state.v1'))).not.toContain(marker)

  await page.getByRole('button', { name: /Sign in as Citizen/i }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('civicsense.demo.session.v1') ?? '{}').email)).toBe(
    CITIZEN_EMAIL,
  )
  await page.goto('/complaints/complaint-101')
  await expect(page.getByText(marker)).toHaveCount(0)
})
