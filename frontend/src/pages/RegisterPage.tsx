import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { PublicLayout } from '../components/PublicLayout'
import { Icon } from '../components/Icon'
import { Button, Eyebrow, Field, inputClass } from '../components/ui'
import { errorMessage } from '../lib/api/errors'
import { useAuth } from '../lib/auth/AuthContext'

type Mode = 'citizen' | 'department'

export default function RegisterPage() {
  const { registerCitizen, registerDepartment } = useAuth()
  const [mode, setMode] = useState<Mode>('citizen')
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [phone, setPhone] = useState('')
  const [departmentName, setDepartmentName] = useState('')
  const [departmentType, setDepartmentType] = useState('ROAD_MAINTENANCE')
  const [officialEmail, setOfficialEmail] = useState('')
  const [city, setCity] = useState('')

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setDone(null)
    setSubmitting(true)
    try {
      if (mode === 'citizen') {
        await registerCitizen({
          fullName: fullName.trim(),
          email: email.trim(),
          password,
          phone: phone.trim() || undefined,
        })
        setDone('Account created. You can sign in and submit your first report.')
      } else {
        const result = await registerDepartment({
          department: {
            name: departmentName.trim(),
            departmentType,
            officialEmail: officialEmail.trim(),
            city: city.trim() || undefined,
          },
          administrator: {
            fullName: fullName.trim(),
            email: email.trim(),
            password,
            phone: phone.trim() || undefined,
          },
        })
        setDone(
          `Department application ${result.departmentId} received. Status: ${result.verificationStatus}. A super admin must verify it before department access is granted.`,
        )
      }
    } catch (err) {
      setError(errorMessage(err, 'Registration failed'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <PublicLayout>
      <main className="mx-auto w-full max-w-3xl px-5 py-12 sm:px-8 lg:py-16">
        <div className="animate-rise rounded-2xl border border-stone-200 bg-white p-6 shadow-soft sm:p-9">
          <Eyebrow>Join CivicSense</Eyebrow>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight text-stone-900 sm:text-4xl">
            Create your account
          </h1>
          <p className="mt-2 text-sm text-stone-600">
            Residents report and track issues. Departments apply for verified access.
          </p>

          <div className="mt-7 flex gap-1 rounded-xl bg-stone-100 p-1">
            {(['citizen', 'department'] as Mode[]).map((value) => (
              <button
                key={value}
                type="button"
                onClick={() => {
                  setMode(value)
                  setError(null)
                  setDone(null)
                }}
                className={`flex-1 rounded-lg px-3 py-2 text-sm font-semibold capitalize transition ${
                  mode === value
                    ? 'bg-white text-brand-800 shadow-sm'
                    : 'text-stone-500 hover:text-stone-800'
                }`}
              >
                {value === 'citizen' ? 'I am a resident' : 'I represent a department'}
              </button>
            ))}
          </div>

          {done ? (
            <div className="mt-7 rounded-2xl border border-brand-200 bg-brand-50 p-5">
              <div className="flex items-start gap-3">
                <Icon name="check-circle" className="mt-0.5 h-5 w-5 shrink-0 text-brand-700" />
                <div>
                  <p className="text-sm font-semibold text-brand-900">You're all set</p>
                  <p className="mt-1 text-sm leading-relaxed text-brand-800">{done}</p>
                  <Link
                    to="/login"
                    className="mt-4 inline-flex items-center gap-1.5 text-sm font-semibold text-brand-800 hover:underline"
                  >
                    Go to sign in
                    <Icon name="arrow-right" className="h-4 w-4" />
                  </Link>
                </div>
              </div>
            </div>
          ) : (
            <form className="mt-7 space-y-5" onSubmit={submit}>
              {mode === 'department' && (
                <div className="grid gap-4 rounded-2xl border border-stone-200 bg-stone-50 p-4 sm:grid-cols-2">
                  <Field label="Department name" required>
                    <input
                      required
                      value={departmentName}
                      onChange={(event) => setDepartmentName(event.target.value)}
                      className={inputClass}
                    />
                  </Field>
                  <Field label="Department type" required>
                    <input
                      required
                      value={departmentType}
                      onChange={(event) => setDepartmentType(event.target.value)}
                      className={inputClass}
                    />
                  </Field>
                  <Field label="Official email" required>
                    <input
                      type="email"
                      required
                      value={officialEmail}
                      onChange={(event) => setOfficialEmail(event.target.value)}
                      className={inputClass}
                    />
                  </Field>
                  <Field label="City">
                    <input
                      value={city}
                      onChange={(event) => setCity(event.target.value)}
                      className={inputClass}
                    />
                  </Field>
                </div>
              )}

              <Field label="Full name" required>
                <input
                  required
                  value={fullName}
                  onChange={(event) => setFullName(event.target.value)}
                  className={inputClass}
                  placeholder="Your name"
                />
              </Field>
              <Field label="Email address" required>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  className={inputClass}
                  placeholder="you@example.com"
                />
              </Field>
              <Field
                label="Password"
                required
                hint="At least 10 characters with upper case, lower case, a number and a symbol."
              >
                <input
                  type="password"
                  required
                  minLength={10}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  className={inputClass}
                />
              </Field>
              <Field label="Phone (optional)">
                <input
                  value={phone}
                  onChange={(event) => setPhone(event.target.value)}
                  className={inputClass}
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
                {submitting
                  ? 'Creating account…'
                  : mode === 'citizen'
                    ? 'Create resident account'
                    : 'Submit department application'}
              </Button>
            </form>
          )}

          <p className="mt-6 text-center text-sm text-stone-600">
            Already have an account?{' '}
            <Link to="/login" className="font-semibold text-brand-700 hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </main>
    </PublicLayout>
  )
}
