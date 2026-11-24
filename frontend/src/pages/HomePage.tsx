import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { getHealth } from '../lib/api/client'
import { useAuth } from '../lib/auth/AuthContext'

function StatusPill({ label, value }: { label: string; value?: string }) {
  const ok = value === 'UP'
  return (
    <div className="flex items-center justify-between rounded-lg border border-stone-200 bg-white px-4 py-3 text-sm shadow-sm">
      <span className="text-stone-600">{label}</span>
      <span
        className={
          ok
            ? 'rounded-full bg-brand-50 px-2.5 py-1 text-xs font-semibold text-brand-700'
            : 'rounded-full bg-red-50 px-2.5 py-1 text-xs font-semibold text-red-600'
        }
      >
        {value ?? 'UNKNOWN'}
      </span>
    </div>
  )
}

export default function HomePage() {
  const { user, logout } = useAuth()
  const health = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
    retry: 1,
    refetchInterval: 30_000,
  })

  return (
    <main className="min-h-screen bg-stone-950 text-stone-100">
      <div className="mx-auto max-w-5xl px-6 py-14">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-semibold tracking-[0.2em] text-brand-500 uppercase">
              CivicSense · {user?.role ?? 'unauthenticated'}
            </p>
            <h1 className="mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">
              Secure reporting starts here.
            </h1>
          </div>
          <button
            type="button"
            onClick={() => void logout()}
            className="rounded-lg border border-stone-700 px-4 py-2 text-sm text-stone-300 transition hover:border-brand-500 hover:text-white"
          >
            Sign out
          </button>
        </header>

        {user && (
          <section className="mt-8 rounded-2xl border border-stone-700 bg-stone-900 p-6">
            <h2 className="text-lg font-semibold">Session</h2>
            <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-stone-500">Signed in as</dt>
                <dd className="text-stone-200">{user.email}</dd>
              </div>
              <div>
                <dt className="text-stone-500">Permissions</dt>
                <dd className="text-stone-200">{user.permissions.join(', ') || 'none'}</dd>
              </div>
              {user.departmentId && (
                <div className="sm:col-span-2">
                  <dt className="text-stone-500">Department verification</dt>
                  <dd
                    className={
                      user.verificationStatus === 'VERIFIED'
                        ? 'text-brand-500'
                        : 'text-amber-500'
                    }
                  >
                    {user.verificationStatus}
                  </dd>
                </div>
              )}
            </dl>
            {user.role === 'SUPER_ADMIN' && (
              <Link
                to="/admin"
                className="mt-5 inline-block rounded-lg bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700"
              >
                Review departments
              </Link>
            )}
            {user.role === 'DEPARTMENT_ADMIN' && user.verificationStatus !== 'VERIFIED' && (
              <p className="mt-5 rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-500">
                Your department is awaiting verification. Department endpoints return 403
                until a super admin approves the application.
              </p>
            )}
          </section>
        )}

        <section className="mt-8 rounded-2xl border border-stone-700 bg-stone-900 p-6 shadow-xl">
          <div className="mb-5 flex items-center justify-between">
            <h2 className="text-lg font-semibold">Backend connectivity</h2>
            <button
              type="button"
              onClick={() => void health.refetch()}
              className="rounded-lg border border-stone-700 px-3 py-1.5 text-xs font-medium text-stone-300 transition hover:border-brand-500 hover:text-white"
            >
              {health.isFetching ? 'Checking…' : 'Refresh'}
            </button>
          </div>

          {health.isError ? (
            <div className="rounded-lg border border-red-900/60 bg-red-950/40 p-4 text-sm text-red-200">
              <p className="font-semibold">Cannot reach the API.</p>
              <p className="mt-1 text-red-300/80">
                Confirm the backend is running and that CORS allows this origin.
              </p>
            </div>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2">
              <StatusPill label="Application" value={health.data?.status} />
              <StatusPill label="Database" value={health.data?.database} />
              <StatusPill label="PostGIS" value={health.data?.postgis} />
              <StatusPill label="Object storage" value={health.data?.objectStorage} />
            </div>
          )}

          <p className="mt-5 font-mono text-xs text-stone-500">
            API: {import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'}
          </p>
          <p className="mt-2 text-xs text-stone-600">
            Hosted Supabase checks remain pending until credentials are configured.
          </p>
        </section>
      </div>
    </main>
  )
}
