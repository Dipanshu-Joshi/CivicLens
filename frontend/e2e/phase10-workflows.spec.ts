import { expect, test } from '@playwright/test'
import { installApiStubs } from './api-stubs'

const GENERIC_MESSAGE = 'If the account exists, a password reset link has been sent.'

test('citizen sign-in → notification → complaint detail → information reply', async ({ page }) => {
  const state = await installApiStubs(page)
  await page.goto('/login')

  await page.getByLabel('Email address').fill('citizen@example.com')
  await page.locator('input[autocomplete="current-password"]').fill('Citizen!Pass1')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL(/\/dashboard$/)

  await page.getByRole('link', { name: /notifications/i }).click()
  await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible()
  await page.getByRole('link', { name: /view complaint/i }).click()
  await expect(page).toHaveURL(/\/complaints\/complaint-1$/)

  await expect(page.getByText('Information requested')).toBeVisible()
  const reply = page.getByLabel('Reply to information request')
  await reply.fill('Yes, the pothole is still present this morning.')
  const replyRequest = page.waitForRequest(
    (request) =>
      request.url().includes('/citizen/complaints/complaint-1/respond-info') &&
      request.method() === 'POST',
  )
  await page.getByRole('button', { name: 'Send response' }).click()
  await replyRequest

  expect(state.infoReplies).toEqual([
    {
      complaintId: 'complaint-1',
      body: 'Yes, the pothole is still present this morning.',
    },
  ])
  await expect(reply).toHaveValue('')
})

test('department notifications deep-link into the department complaint surface', async ({
  page,
}) => {
  await installApiStubs(page, { role: 'DEPARTMENT_ADMIN', authenticated: true })
  await page.goto('/notifications')

  const link = page.getByRole('link', { name: /view complaint/i })
  await expect(link).toHaveAttribute('href', '/department/complaints/complaint-1')
  await link.click()
  await expect(page).toHaveURL(/\/department\/complaints\/complaint-1$/)
})

test('admin notifications deep-link into the admin complaint surface', async ({ page }) => {
  await installApiStubs(page, { role: 'SUPER_ADMIN', authenticated: true })
  await page.goto('/notifications')

  await expect(page.getByRole('link', { name: /view complaint/i })).toHaveAttribute(
    'href',
    '/admin/complaints/complaint-1',
  )
})

test('a citizen cannot enter the admin surface', async ({ page }) => {
  await installApiStubs(page, { role: 'CITIZEN', authenticated: true })
  await page.goto('/admin')

  await expect(page).toHaveURL(/\/forbidden$/)
  await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible()
})

test('forgot-password and reset-password forms complete with stubbed APIs', async ({ page }) => {
  const state = await installApiStubs(page)
  await page.goto('/login')
  await page.getByRole('link', { name: 'Forgot password?' }).click()
  await expect(page).toHaveURL(/\/forgot-password$/)

  await page.getByLabel('Email address').fill('citizen@example.com')
  await page.getByRole('button', { name: /send reset link/i }).click()
  await expect(page.getByText(GENERIC_MESSAGE)).toBeVisible()
  expect(state.forgotEmails).toEqual(['citizen@example.com'])

  await page.goto('/reset-password?token=e2e-one-time-token')
  await expect(page.getByText('e2e-one-time-token')).toHaveCount(0)
  await page.getByLabel(/^new password/i).fill('Str0ng!Pass1')
  await page.getByLabel(/confirm new password/i).fill('Str0ng!Pass1')
  await page.getByRole('button', { name: /update password/i }).click()

  await expect(page.getByText(/password has been changed/i)).toBeVisible()
  expect(state.resetRequests).toEqual([{ token: 'e2e-one-time-token', password: 'Str0ng!Pass1' }])
  expect(await page.evaluate(() => localStorage.getItem('resetToken'))).toBeNull()
})
