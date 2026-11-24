import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { PublicLayout } from '../components/PublicLayout'
import { Icon } from '../components/Icon'
import { Button, Eyebrow, Field, inputClass } from '../components/ui'
import { errorMessage } from '../lib/api/errors'
import { resetPassword } from '../lib/api/client'

const PASSWORD_RULE =
  'Use at least 10 characters with upper and lower case letters, a number, and a symbol.'

export default function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token')?.trim() ?? ''
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (password !== confirmPassword) {
      setError('Passwords do not match.')
      return
    }

    setSubmitting(true)
    try {
      await resetPassword(token, password)
      setSuccess(true)
    } catch (err) {
      setError(errorMessage(err, 'Unable to reset your password'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <PublicLayout>
      <main className="mx-auto w-full max-w-xl px-5 py-12 sm:px-8 lg:py-20">
        <div className="animate-rise rounded-2xl border border-stone-200 bg-white p-6 shadow-soft sm:p-8">
          <Eyebrow>Account recovery</Eyebrow>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight text-stone-900 sm:text-4xl">
            {success ? 'Password updated' : 'Choose a new password'}
          </h1>

          {success ? (
            <div className="mt-5 space-y-5">
              <div className="rounded-2xl border border-brand-200 bg-brand-50 p-5">
                <div className="flex items-start gap-3">
                  <Icon name="check-circle" className="mt-0.5 h-5 w-5 shrink-0 text-brand-700" />
                  <p className="text-sm leading-relaxed text-brand-800">
                    Your password has been changed. Existing sessions have been revoked; sign in
                    again to continue.
                  </p>
                </div>
              </div>
              <Link
                to="/login"
                className="inline-flex items-center gap-1.5 text-sm font-semibold text-brand-800 hover:underline"
              >
                Go to sign in
                <Icon name="arrow-right" className="h-4 w-4" />
              </Link>
            </div>
          ) : !token ? (
            <div className="mt-5 space-y-5">
              <div
                role="alert"
                className="rounded-xl border border-amber-200 bg-amber-50 px-3 py-2.5 text-sm text-amber-800"
              >
                This reset link is missing its one-time token. Request a new link to continue.
              </div>
              <Link
                to="/forgot-password"
                className="inline-flex items-center gap-1.5 text-sm font-semibold text-brand-700 hover:underline"
              >
                Request a new link
                <Icon name="arrow-right" className="h-4 w-4" />
              </Link>
            </div>
          ) : (
            <form className="mt-7 space-y-5" onSubmit={submit}>
              <p className="text-sm leading-relaxed text-stone-600">
                {PASSWORD_RULE} This link can be used only once.
              </p>
              <Field label="New password" required hint={PASSWORD_RULE}>
                <input
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  required
                  minLength={10}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  className={`${inputClass} pr-16`}
                  placeholder="••••••••••"
                />
              </Field>
              <Field label="Confirm new password" required>
                <input
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  required
                  minLength={10}
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  className={inputClass}
                  placeholder="••••••••••"
                />
              </Field>
              <button
                type="button"
                onClick={() => setShowPassword((visible) => !visible)}
                className="text-xs font-semibold text-brand-700 hover:underline"
              >
                {showPassword ? 'Hide passwords' : 'Show passwords'}
              </button>

              {error && (
                <div
                  role="alert"
                  className="rounded-xl border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700"
                >
                  {error}
                </div>
              )}

              <Button type="submit" size="lg" fullWidth loading={submitting}>
                {submitting ? 'Updating password…' : 'Update password'}
              </Button>
            </form>
          )}

          <p className="mt-6 text-center text-xs text-stone-400">
            <Link to="/login" className="hover:text-brand-700">
              ← Back to sign in
            </Link>
          </p>
        </div>
      </main>
    </PublicLayout>
  )
}
