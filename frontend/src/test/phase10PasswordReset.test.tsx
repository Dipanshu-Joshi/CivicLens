import type { ReactNode } from 'react'
import { AxiosError, type AxiosResponse } from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ForgotPasswordPage from '../pages/ForgotPasswordPage'
import ResetPasswordPage from '../pages/ResetPasswordPage'

const mocks = vi.hoisted(() => ({
  forgotPassword: vi.fn(),
  resetPassword: vi.fn(),
}))

vi.mock('../lib/api/client', () => ({
  forgotPassword: mocks.forgotPassword,
  resetPassword: mocks.resetPassword,
}))

const GENERIC_MESSAGE = 'If the account exists, a password reset link has been sent.'
const USER = userEvent.setup()

function apiError(status: number, data: Record<string, unknown>): AxiosError {
  const response = { status, data, statusText: '', headers: {}, config: {} as never } as AxiosResponse
  return new AxiosError('request failed', undefined, {} as never, undefined, response)
}

function renderAt(path: string, element: ReactNode) {
  return render(<MemoryRouter initialEntries={[path]}>{element}</MemoryRouter>)
}

describe('Phase 10 password recovery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('requests a reset link and shows the generic success message', async () => {
    mocks.forgotPassword.mockResolvedValue(GENERIC_MESSAGE)
    renderAt('/forgot-password', <ForgotPasswordPage />)

    await USER.type(screen.getByLabelText(/email address/i), ' citizen@example.com ')
    await USER.click(screen.getByRole('button', { name: /send reset link/i }))

    await waitFor(() => expect(mocks.forgotPassword).toHaveBeenCalledWith('citizen@example.com'))
    expect(await screen.findByText(GENERIC_MESSAGE)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /return to sign in/i })).toHaveAttribute('href', '/login')
  })

  it('shows an error when a reset request fails', async () => {
    mocks.forgotPassword.mockRejectedValue(apiError(429, { detail: 'Too many requests' }))
    renderAt('/forgot-password', <ForgotPasswordPage />)

    await USER.type(screen.getByLabelText(/email address/i), 'citizen@example.com')
    await USER.click(screen.getByRole('button', { name: /send reset link/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many requests')
    expect(mocks.forgotPassword).toHaveBeenCalledTimes(1)
  })

  it('submits the URL token and password once for a valid reset link', async () => {
    mocks.resetPassword.mockResolvedValue(undefined)
    renderAt('/reset-password?token=one-time-token', <ResetPasswordPage />)

    expect(screen.queryByText(/one-time-token/)).not.toBeInTheDocument()
    await USER.type(screen.getByLabelText(/^new password/i), 'Str0ng!Pass1')
    await USER.type(screen.getByLabelText(/confirm new password/i), 'Str0ng!Pass1')
    await USER.click(screen.getByRole('button', { name: /update password/i }))

    await waitFor(() =>
      expect(mocks.resetPassword).toHaveBeenCalledWith('one-time-token', 'Str0ng!Pass1'),
    )
    expect(await screen.findByText(/password has been changed/i)).toBeInTheDocument()
  })

  it('rejects mismatched passwords before calling the API', async () => {
    renderAt('/reset-password?token=one-time-token', <ResetPasswordPage />)

    await USER.type(screen.getByLabelText(/^new password/i), 'Str0ng!Pass1')
    await USER.type(screen.getByLabelText(/confirm new password/i), 'Different!Pass1')
    await USER.click(screen.getByRole('button', { name: /update password/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Passwords do not match')
    expect(mocks.resetPassword).not.toHaveBeenCalled()
  })

  it('does not offer a reset form without a URL token', async () => {
    renderAt('/reset-password', <ResetPasswordPage />)

    expect(screen.getByRole('alert')).toHaveTextContent(/missing its one-time token/i)
    expect(screen.queryByRole('button', { name: /update password/i })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /request a new link/i })).toHaveAttribute(
      'href',
      '/forgot-password',
    )
    expect(mocks.resetPassword).not.toHaveBeenCalled()
  })

  it('shows the backend error when reset fails', async () => {
    mocks.resetPassword.mockRejectedValue(
      apiError(400, { title: 'Invalid or expired reset token' }),
    )
    renderAt('/reset-password?token=expired-token', <ResetPasswordPage />)

    await USER.type(screen.getByLabelText(/^new password/i), 'Str0ng!Pass1')
    await USER.type(screen.getByLabelText(/confirm new password/i), 'Str0ng!Pass1')
    await USER.click(screen.getByRole('button', { name: /update password/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid or expired reset token')
  })
})
