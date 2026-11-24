import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { PublicLayout } from '../components/PublicLayout'
import { Icon } from '../components/Icon'
import { Button, Eyebrow, Field, inputClass } from '../components/ui'
import { DEMO_ACCOUNTS, DEMO_ENABLED } from '../demo/feature'
import { errorMessage } from '../lib/api/errors'
import type { Role } from '../lib/api/client'
import { useAuth } from '../lib/auth/AuthContext'

function homeFor(role: Role): string {
  if (role === 'SUPER_ADMIN') return '/admin'
  if (role === 'DEPARTMENT_ADMIN') return '/department'
  if (role === 'CITIZEN') return '/dashboard'
  return '/notifications'
}

export default function LoginPage() {
  const { login, isAuthenticated, isLoading, user } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [demoAccount, setDemoAccount] = useState<string | null>(null)

  if (isLoading) return null
  if (isAuthenticated && user) return <Navigate to={homeFor(user.role)} replace />

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const result = await login(email.trim(), password)
      navigate(homeFor(result.user.role), { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Unable to sign in'))
    } finally {
      setSubmitting(false)
    }
  }

  async function signInAsDemo(account: (typeof DEMO_ACCOUNTS)[number]) {
    setError(null)
    setDemoAccount(account.key)
    setEmail(account.email)
    setPassword(account.password)
    try {
      const result = await login(account.email, account.password)
      navigate(homeFor(result.user.role), { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Unable to sign in to the demo'))
    } finally {
      setDemoAccount(null)
    }
  }

  return (
    <PublicLayout>
      <main className="mx-auto grid w-full max-w-6xl items-center gap-10 px-5 py-12 sm:px-8 lg:grid-cols-2 lg:py-20">
        <div className="animate-rise">
          <Eyebrow>Welcome back</Eyebrow>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight text-stone-900 sm:text-4xl">
            Sign in to CivicSense
          </h1>
          <p className="mt-3 max-w-md text-sm leading-relaxed text-stone-600">
            Your reports, their status, and department updates are all in one place.
          </p>
          <div className="mt-8 hidden space-y-4 lg:block">
            {[
              { icon: 'shield' as const, text: 'Sessions are issued by the CivicSense API' },
              { icon: 'clock' as const, text: 'Access tokens stay in memory, never browser storage' },
              { icon: 'clipboard' as const, text: 'You only ever see complaints you own' },
            ].map((item) => (
              <div key={item.text} className="flex items-center gap-3 text-sm text-stone-600">
                <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-white text-stone-700 ring-1 ring-stone-200">
                  <Icon name={item.icon} className="h-4 w-4" />
                </span>
                {item.text}
              </div>
            ))}
          </div>
        </div>

        <div className="animate-rise-delay rounded-2xl border border-stone-200 bg-white p-6 shadow-soft sm:p-8">
          {DEMO_ENABLED && (
            <section
              aria-label="Demo accounts"
              className="mb-6 rounded-xl border border-clay-200 bg-clay-50 p-4"
            >
              <div className="flex items-center justify-between gap-3">
                <h2 className="text-sm font-semibold text-stone-900">Local demo accounts</h2>
                <span className="rounded-full border border-clay-300 bg-white px-2 py-0.5 text-[10px] font-bold tracking-wide text-clay-700 uppercase">
                  Demo mode
                </span>
              </div>
              <p className="mt-1.5 text-xs leading-relaxed text-stone-600">
                One click signs in against local browser data. No server, credentials or AI API is used.
              </p>
              <div className="mt-3 grid gap-2">
                {DEMO_ACCOUNTS.map((account) => (
                  <button
                    key={account.key}
                    type="button"
                    onClick={() => void signInAsDemo(account)}
                    disabled={submitting || demoAccount !== null}
                    className="rounded-lg border border-clay-200 bg-white px-3 py-2 text-left transition hover:border-clay-500 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    <span className="block text-xs font-semibold text-stone-900">
                      {demoAccount === account.key ? 'Signing in…' : `Sign in as ${account.label}`}
                    </span>
                    <span className="mt-0.5 block font-mono text-[11px] text-stone-500">
                      {account.email} / {account.password}
                    </span>
                    <span className="mt-0.5 block text-[11px] text-stone-500">
                      {account.description}
                    </span>
                  </button>
                ))}
              </div>
            </section>
          )}
          <form className="space-y-5" onSubmit={submit}>
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
            <Field label="Password" required>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  className={`${inputClass} pr-16`}
                  placeholder="••••••••"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((visible) => !visible)}
                  className="absolute top-1/2 right-3 -translate-y-1/2 text-xs font-semibold text-brand-700 hover:underline"
                >
                  {showPassword ? 'Hide' : 'Show'}
                </button>
              </div>
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
              {submitting ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>

          <p className="mt-6 text-center text-sm text-stone-600">
            New to CivicSense?{' '}
            <Link to="/register" className="font-semibold text-brand-700 hover:underline">
              Create an account
            </Link>
          </p>
          <p className="mt-3 text-center text-sm">
            <Link to="/forgot-password" className="font-semibold text-brand-700 hover:underline">
              Forgot password?
            </Link>
          </p>
          <p className="mt-4 text-center text-xs text-stone-400">
            <Link to="/" className="hover:text-brand-700">
              ← Back to home
            </Link>
          </p>
        </div>
      </main>
    </PublicLayout>
  )
}
