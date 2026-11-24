import { Link } from 'react-router-dom'
import { PublicLayout } from '../components/PublicLayout'
import { Icon } from '../components/Icon'
import { Eyebrow } from '../components/ui'
import { useAuth } from '../lib/auth/AuthContext'

const steps = [
  {
    icon: 'camera' as const,
    title: 'Capture the details',
    body: 'Take a photo or upload one from your device, then describe what needs attention.',
  },
  {
    icon: 'location' as const,
    title: 'Pin it precisely',
    body: 'Use GPS or place the marker yourself so the right department can find the issue.',
  },
  {
    icon: 'clock' as const,
    title: 'Follow every update',
    body: 'A complete timeline keeps you informed from submission through resolution.',
  },
]

const timeline = [
  { label: 'Submitted', time: '09:12', done: true },
  { label: 'Assigned to Public Works', time: '09:40', done: true },
  { label: 'In progress', time: 'Today', done: false },
]

function ReportPreview() {
  return (
    <div
      aria-hidden="true"
      className="relative mx-auto w-full max-w-md rotate-1 rounded-2xl border border-stone-900/10 bg-white p-4 shadow-lift transition duration-500 hover:rotate-0"
    >
      <div className="relative h-44 overflow-hidden rounded-xl border border-stone-200 bg-paper-deep">
        <div className="bg-grid absolute inset-0 opacity-80" style={{ maskImage: 'none' }} />
        <svg className="absolute inset-0 h-full w-full" viewBox="0 0 400 176" fill="none">
          <path d="M-10 120 C 90 100, 150 150, 250 110 S 380 70, 420 90" stroke="#d6d3d1" strokeWidth="14" />
          <path d="M120 -10 C 140 50, 110 100, 150 190" stroke="#d6d3d1" strokeWidth="10" />
          <path d="M-10 120 C 90 100, 150 150, 250 110 S 380 70, 420 90" stroke="#fff" strokeWidth="2" strokeDasharray="8 8" />
        </svg>
        <div className="absolute top-[34%] left-[56%] -translate-x-1/2 -translate-y-full">
          <div className="flex h-9 w-9 items-center justify-center rounded-full bg-clay-500 text-white shadow-lg ring-4 ring-white">
            <Icon name="location" className="h-4 w-4" />
          </div>
        </div>
        <span className="absolute bottom-2 left-2 rounded-md bg-white/90 px-2 py-1 text-[10px] font-semibold text-stone-600 ring-1 ring-stone-200">
          Elm Street &amp; 4th Ave
        </span>
      </div>

      <div className="mt-4 flex items-start justify-between gap-3">
        <div>
          <p className="text-[10px] font-semibold tracking-[0.14em] text-stone-500 uppercase">
            Report #2041
          </p>
          <p className="mt-1 font-display text-lg leading-tight font-semibold text-stone-900">
            Streetlight out near crosswalk
          </p>
        </div>
        <span className="inline-flex shrink-0 items-center gap-1.5 rounded-full bg-stone-900 px-2.5 py-1 text-[11px] font-semibold text-stone-50">
          <span className="h-1.5 w-1.5 rounded-full bg-clay-300" />
          Assigned
        </span>
      </div>

      <ol className="mt-4 space-y-2.5 border-t border-stone-200 pt-4">
        {timeline.map((item) => (
          <li key={item.label} className="flex items-center gap-3 text-xs">
            <span
              className={`flex h-5 w-5 items-center justify-center rounded-full ${
                item.done
                  ? 'bg-brand-700 text-white'
                  : 'border border-dashed border-stone-400 text-stone-400'
              }`}
            >
              {item.done && <Icon name="check" className="h-3 w-3" />}
            </span>
            <span className={item.done ? 'font-medium text-stone-800' : 'text-stone-500'}>
              {item.label}
            </span>
            <span className="ml-auto text-stone-400">{item.time}</span>
          </li>
        ))}
      </ol>
    </div>
  )
}

export default function LandingPage() {
  const { isAuthenticated, user } = useAuth()
  const isDepartmentAdmin = user?.role === 'DEPARTMENT_ADMIN'
  const primaryCta = isDepartmentAdmin ? '/department' : '/dashboard'
  const complaintsCta = isDepartmentAdmin ? '/department/complaints' : '/complaints'

  return (
    <PublicLayout>
      <section className="relative overflow-hidden border-b border-stone-200">
        <div className="relative mx-auto grid max-w-7xl items-center gap-14 px-5 py-16 sm:px-8 lg:grid-cols-[1.1fr_0.9fr] lg:py-24">
          <div className="animate-rise">
            <span className="inline-flex items-center gap-2 rounded-full border border-stone-300 bg-white px-3 py-1.5 text-[11px] font-semibold tracking-wide text-stone-700 uppercase">
              <span className="h-1.5 w-1.5 rounded-full bg-clay-500" />
              Civic reporting, with clarity
            </span>
            <h1 className="mt-6 text-5xl leading-[1.02] font-semibold tracking-[-0.035em] text-stone-900 sm:text-6xl lg:text-7xl">
              Report what matters.
              <br />
              <span className="text-brand-700 italic">See what happens next.</span>
            </h1>
            <p className="mt-6 max-w-xl text-base leading-relaxed text-stone-600 sm:text-lg">
              CivicSense gives residents one calm, dependable place to report local issues, share
              location context, and follow progress all the way to resolution.
            </p>
            <div className="mt-8 flex flex-wrap gap-3">
              {isAuthenticated ? (
                <Link
                  to={primaryCta}
                  className="inline-flex items-center gap-2 rounded-full bg-brand-700 px-6 py-3 text-sm font-semibold text-white transition hover:bg-brand-800"
                >
                  {isDepartmentAdmin ? 'Open department overview' : 'Open your dashboard'}
                  <Icon name="arrow-right" className="h-4 w-4" />
                </Link>
              ) : (
                <Link
                  to="/register"
                  className="inline-flex items-center gap-2 rounded-full bg-brand-700 px-6 py-3 text-sm font-semibold text-white transition hover:bg-brand-800"
                >
                  Report an issue
                  <Icon name="arrow-right" className="h-4 w-4" />
                </Link>
              )}
              <Link
                to={isAuthenticated ? complaintsCta : '/login'}
                className="inline-flex items-center gap-2 rounded-full border border-stone-300 bg-white px-6 py-3 text-sm font-semibold text-stone-800 transition hover:border-stone-900"
              >
                {isAuthenticated
                  ? isDepartmentAdmin
                    ? 'View complaint queue'
                    : 'View my complaints'
                  : 'Sign in'}
              </Link>
            </div>
            <div className="mt-10 flex flex-wrap gap-x-6 gap-y-2 text-xs text-stone-500">
              <span className="flex items-center gap-1.5">
                <Icon name="check-circle" className="h-4 w-4 text-brand-600" />
                Secure Spring Boot authentication
              </span>
              <span className="flex items-center gap-1.5">
                <Icon name="check-circle" className="h-4 w-4 text-brand-600" />
                Department-owned workflows
              </span>
            </div>
          </div>

          <div className="animate-rise-delay">
            <ReportPreview />
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-20 sm:px-8">
        <div className="grid gap-8 lg:grid-cols-[0.8fr_1.2fr] lg:gap-16">
          <div>
            <Eyebrow>A better reporting loop</Eyebrow>
            <h2 className="mt-3 text-3xl font-semibold tracking-tight text-stone-900 sm:text-4xl">
              Simple for residents. Actionable for departments.
            </h2>
            <p className="mt-4 text-sm leading-relaxed text-stone-600">
              Every report carries the context a team needs, while the resident keeps a clear view
              of what changed and when.
            </p>
          </div>
          <ol className="divide-y divide-stone-200 border-y border-stone-200">
            {steps.map((step, index) => (
              <li
                key={step.title}
                className="group flex items-start gap-5 py-6 transition hover:bg-white/60 sm:px-3"
              >
                <span className="font-display text-3xl font-semibold text-stone-300 tabular-nums transition group-hover:text-clay-500">
                  {String(index + 1).padStart(2, '0')}
                </span>
                <div className="flex-1">
                  <h3 className="text-lg font-semibold text-stone-900">{step.title}</h3>
                  <p className="mt-1.5 max-w-md text-sm leading-relaxed text-stone-600">
                    {step.body}
                  </p>
                </div>
                <span className="mt-1 flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-white text-stone-700 ring-1 ring-stone-200 transition group-hover:bg-brand-700 group-hover:text-white group-hover:ring-brand-700">
                  <Icon name={step.icon} />
                </span>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 pb-20 sm:px-8">
        <div className="relative overflow-hidden rounded-3xl bg-brand-950 px-6 py-12 text-paper sm:px-12">
          <div className="bg-grid pointer-events-none absolute inset-0 opacity-20 invert" />
          <div className="relative flex flex-col gap-6 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-3xl font-semibold tracking-tight sm:text-4xl">
                Notice something that needs attention?
              </h2>
              <p className="mt-3 max-w-xl text-sm text-stone-300">
                Create your account, pin the location, and submit a structured report in a few
                focused steps.
              </p>
            </div>
            <Link
              to={isAuthenticated ? complaintsCta : '/register'}
              className="inline-flex shrink-0 items-center justify-center gap-2 rounded-full bg-paper px-6 py-3 text-sm font-semibold text-brand-950 transition hover:bg-white"
            >
              {isAuthenticated && isDepartmentAdmin ? 'Review complaint queue' : 'Start a report'}
              <Icon name="arrow-right" className="h-4 w-4" />
            </Link>
          </div>
        </div>
      </section>
    </PublicLayout>
  )
}
