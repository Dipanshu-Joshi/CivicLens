import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { AppLayout } from '../components/AppLayout'
import { Icon } from '../components/Icon'
import { Badge, Button, Card, PageHeader, StatePanel, StatCard, StatusBadge } from '../components/ui'
import { listMyComplaints } from '../lib/api/citizen'
import { errorMessage } from '../lib/api/errors'
import { useAuth } from '../lib/auth/AuthContext'
import { buildDashboardStats } from '../lib/utils/analytics'
import { formatDate, humanizeStatus, truncate } from '../lib/utils/format'

export default function CitizenDashboard() {
  const { user } = useAuth()
  const complaints = useQuery({
    queryKey: ['citizen', 'complaints'],
    queryFn: () => listMyComplaints(),
  })

  const stats = buildDashboardStats(complaints.data ?? [])
  const firstName = user?.fullName.split(' ')[0] ?? 'there'

  return (
    <AppLayout>
      <PageHeader
        eyebrow={`Welcome back, ${firstName}`}
        title="Your civic overview"
        description="A live view of the reports you have submitted and where they stand."
        actions={
          <Link to="/complaints/new">
            <Button icon={<Icon name="plus" className="h-4 w-4" />}>Report an issue</Button>
          </Link>
        }
      />

      {complaints.isPending && (
        <Card className="flex items-center justify-center gap-3 p-12 text-sm text-stone-500">
          <span className="h-5 w-5 animate-spin rounded-full border-2 border-brand-200 border-t-brand-600" />
          Loading your complaints…
        </Card>
      )}

      {complaints.isError && (
        <StatePanel
          tone="error"
          title="Could not load your dashboard"
          description={errorMessage(complaints.error, 'The service did not return your complaints.')}
          action={<Button variant="secondary" onClick={() => void complaints.refetch()}>Try again</Button>}
        />
      )}

      {complaints.isSuccess && (
        <div className="space-y-6">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Total reports" value={stats.total} icon="clipboard" tone="info" />
            <StatCard label="Pending" value={stats.pending} icon="clock" tone="warn" />
            <StatCard label="In progress" value={stats.inProgress} icon="sparkle" tone="info" />
            <StatCard label="Resolved" value={stats.resolved} icon="check-circle" tone="success" />
          </div>

          <div className="grid gap-6 lg:grid-cols-[1.35fr_0.65fr]">
            <Card className="overflow-hidden">
              <div className="flex items-center justify-between border-b border-stone-200 px-5 py-4">
                <div>
                  <h2 className="text-sm font-semibold text-stone-900">Recent reports</h2>
                  <p className="mt-0.5 text-xs text-stone-500">Your latest submissions</p>
                </div>
                <Link
                  to="/complaints"
                  className="text-xs font-semibold text-brand-700 hover:underline"
                >
                  View all
                </Link>
              </div>
              {stats.recent.length === 0 ? (
                <div className="p-6">
                  <StatePanel
                    icon="clipboard"
                    title="No reports yet"
                    description="When you submit your first report, it will appear here with live status updates."
                    action={
                      <Link to="/complaints/new">
                        <Button size="sm">Create a report</Button>
                      </Link>
                    }
                  />
                </div>
              ) : (
                <ul className="divide-y divide-stone-100">
                  {stats.recent.map((complaint) => (
                    <li key={complaint.id}>
                      <Link
                        to={`/complaints/${complaint.id}`}
                        className="flex flex-col gap-3 px-5 py-4 transition hover:bg-stone-50 sm:flex-row sm:items-center sm:justify-between"
                      >
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="font-mono text-xs font-semibold text-brand-700">
                              {complaint.complaintNumber}
                            </span>
                            <StatusBadge status={complaint.status} />
                          </div>
                          <p className="mt-1.5 text-sm text-stone-800">
                            {truncate(complaint.description, 110)}
                          </p>
                          <p className="mt-1 text-xs text-stone-500">
                            {complaint.categoryCode ? `${complaint.categoryCode} · ` : ''}
                            {formatDate(complaint.createdAt)}
                          </p>
                        </div>
                        <Icon
                          name="arrow-right"
                          className="h-4 w-4 shrink-0 text-stone-400 sm:ml-4"
                        />
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card className="p-5">
              <h2 className="text-sm font-semibold text-stone-900">Status distribution</h2>
              <p className="mt-0.5 text-xs text-stone-500">Calculated from your reports</p>
              {stats.distribution.length === 0 ? (
                <p className="mt-8 text-center text-sm text-stone-500">No data to show yet.</p>
              ) : (
                <div className="mt-5 space-y-4">
                  {stats.distribution.map((slice) => {
                    const percentage = Math.round((slice.count / stats.total) * 100)
                    return (
                      <div key={slice.status}>
                        <div className="mb-1.5 flex items-center justify-between gap-2 text-xs">
                          <span className="font-medium text-stone-700">
                            {humanizeStatus(slice.status)}
                          </span>
                          <Badge tone="neutral">{slice.count}</Badge>
                        </div>
                        <div className="h-2 overflow-hidden rounded-full bg-stone-100">
                          <div
                            className="h-full rounded-full bg-brand-500 transition-all duration-500"
                            style={{ width: `${percentage}%` }}
                          />
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </Card>
          </div>

          <div className="flex items-start gap-3 rounded-2xl border border-brand-200 bg-brand-50/70 p-4 text-xs text-brand-900">
            <Icon name="info" className="mt-0.5 h-4 w-4 shrink-0" />
            <p>
              Counts and charts are derived only from complaints returned by your authenticated API.
              Reports with confirmed photos may also carry an advisory AI result; those without
              photos remain plain submissions until a department acts on them.
            </p>
          </div>
        </div>
      )}
    </AppLayout>
  )
}
