import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { PublicLayout } from '../components/PublicLayout'
import { Icon } from '../components/Icon'
import { Button, Eyebrow, Field, inputClass } from '../components/ui'
import { errorMessage } from '../lib/api/errors'
import { forgotPassword } from '../lib/api/client'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      setMessage(await forgotPassword(email.trim()))
    } catch (err) {
      setError(errorMessage(err, 'Unable to request a reset link'))
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
            Forgot your password?
          </h1>
          <p className="mt-2 text-sm leading-relaxed text-stone-600">
            Enter the email address linked to your CivicSense account. If it exists, we will send a
            one-time reset link.
          </p>

          {message ? (
            <div className="mt-7 rounded-2xl border border-brand-200 bg-brand-50 p-5">
              <div className="flex items-start gap-3">
                <Icon name="check-circle" className="mt-0.5 h-5 w-5 shrink-0 text-brand-700" />
                <div>
                  <p className="text-sm font-semibold text-brand-900">Check your inbox</p>
                  <p className="mt-1 text-sm leading-relaxed text-brand-800">{message}</p>
                  <Link
                    to="/login"
                    className="mt-4 inline-flex items-center gap-1.5 text-sm font-semibold text-brand-800 hover:underline"
                  >
                    Return to sign in
                    <Icon name="arrow-right" className="h-4 w-4" />
                  </Link>
                </div>
              </div>
            </div>
          ) : (
            <form className="mt-7 space-y-5" onSubmit={submit}>
              <Field label="Email address" required>
                <input
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  className={inputClass}
                  placeholder="you@example.com"
                />
              </Field>

              {error && (
                <div
                  role="alert"
                  className="rounded-xl border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700"
                >
                  {error}
                </div>
              )}

              <Button type="submit" size="lg" fullWidth loading={submitting}>
                {submitting ? 'Sending link…' : 'Send reset link'}
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
