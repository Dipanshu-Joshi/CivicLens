import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { AppLayout } from '../components/AppLayout'
import { Icon } from '../components/Icon'
import {
  Badge,
  Button,
  Card,
  PageHeader,
  Spinner,
  StatePanel,
  StatCard,
} from '../components/ui'
import { getAdminOverview, type AdminCount } from '../lib/api/admin'
import { getUnreadNotificationCount, listNotifications } from '../lib/api/citizen'
import { errorMessage } from '../lib/api/errors'
import { complaintPathForRole } from '../lib/notifications'
import { formatDateTime, humanizeStatus, truncate } from '../lib/utils/format'

const periods = [7, 30, 90, 365]

function DistributionCard({ title, items }: { title: string; items: AdminCount[] }) {
  const max = Math.max(0, ...items.map((item) => item.count))
  return (
    <Card className="p-5">
      <h2 className="text-sm font-semibold text-stone-900">{title}</h2>
      {items.length === 0 ? (
        <p className="mt-4 rounded-xl bg-stone-50 px-4 py-6 text-center text-xs text-stone-500">
          No data for this period.
        </p>
      ) : (
        <ul className="mt-4 space-y-4">
          {items.map((item) => (
            <li key={item.key}>
              <div className="mb-1.5 flex items-baseline justify-between gap-3 text-xs">
                <span className="min-w-0 truncate font-medium text-stone-700">{item.label}</span>
                <span className="font-semibold text-stone-900">{item.count}</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-stone-100">
                <div
                  className="h-full rounded-full bg-brand-500"
                  style={{ width: `${max === 0 ? 0 : Math.round((item.count / max) * 100)}%` }}
                />
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

export default function AdminDashboard() {
  const [periodDays, setPeriodDays] = useState(30)
  const overview = useQuery({
    queryKey: ['admin', 'overview', periodDays],
    queryFn: () => getAdminOverview(periodDays),
    retry: false,
  })
  const notifications = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: listNotifications,
    retry: false,
  })
  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: getUnreadNotificationCount,
    retry: false,
  })

  const data = overview.data
  const latestNotifications = notifications.data?.slice(0, 3) ?? []
  const unreadCount = unread.data ?? 0
  const governance = data
    ? [
        {
          label: 'Departments awaiting verification',
          value: data.governance.pendingDepartments,
          to: '/admin/departments',
        },
        {
          label: 'Suspended departments',
          value: data.governance.suspendedDepartments,
          to: '/admin/departments',
        },
        {
          label: 'Ambiguous classifications',
          value: data.governance.ambiguous,
          to: '/admin/complaints?aiStatus=AMBIGUOUS',
        },
        {
          label: 'Low-confidence AI',
          value: data.governance.lowConfidence,
          to: '/admin/complaints?aiStatus=LOW_CONFIDENCE',
        },
        {
          label: 'Failed AI analyses',
          value: data.governance.failedAi,
          to: '/admin/complaints?aiStatus=FAILED',
        },
        {
          label: 'Reassignment requests',
          value: data.governance.reassignmentRequests,
          to: '/admin/complaints',
        },
      ]
    : []

  return (
    <AppLayout>
      <PageHeader
        eyebrow="Super admin"
        title="Platform overview"
        description="Live platform counts and governance queues calculated from persisted CivicSense records."
        actions={
          <div className="flex flex-wrap gap-2" role="group" aria-label="Reporting period">
            {periods.map((period) => (
              <Button
                key={period}
                size="sm"
                variant={period === periodDays ? 'primary' : 'secondary'}
                aria-pressed={period === periodDays}
                onClick={() => setPeriodDays(period)}
              >
                Last {period} days
              </Button>
            ))}
          </div>
        }
      />

      {overview.isPending && (
        <Card className="flex items-center justify-center gap-3 p-12 text-sm text-stone-500">
          <Spinner className="h-5 w-5 text-brand-600" />
          <span role="status">Loading platform overview…</span>
        </Card>
      )}

      {overview.isError && (
        <div role="alert">
          <StatePanel
            tone="error"
            title="Could not load platform overview"
            description={errorMessage(overview.error, 'The service did not return platform statistics.')}
            action={
              <Button variant="secondary" onClick={() => void overview.refetch()}>
                Try again
              </Button>
            }
          />
        </div>
      )}

      {data && (
        <div className="space-y-6">
          <p role="status" className="text-xs font-medium text-stone-500">
            Reporting window: last {data.periodDays} days
          </p>

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              label="Total complaints"
              value={data.stats.total}
              detail="All complaints on the platform"
              icon="clipboard"
              tone="info"
            />
            <StatCard
              label="Resolved"
              value={data.stats.resolved}
              detail="Resolved or closed"
              icon="check-circle"
              tone="success"
            />
            <StatCard
              label="Pending"
              value={data.stats.pending}
              detail="Awaiting department action"
              icon="clock"
              tone="warn"
            />
            <StatCard
              label="Unassigned"
              value={data.stats.unassigned}
              detail="No owning department"
              icon="clipboard"
              tone="warn"
            />
            <StatCard
              label="Overdue"
              value={data.stats.overdue}
              detail="Past resolution target"
              icon="clock"
              tone="danger"
            />
            <StatCard
              label="Failed AI analyses"
              value={data.stats.failedAi}
              detail="Requires manual classification"
              icon="sparkle"
              tone="warn"
            />
            <StatCard
              label="Average resolution"
              value={
                data.stats.averageResolutionHours === null
                  ? '—'
                  : data.stats.averageResolutionHours.toFixed(1)
              }
              detail="Hours to resolution"
              icon="clock"
            />
            <StatCard
              label="SLA compliance"
              value={
                data.stats.slaCompliancePercent === null
                  ? '—'
                  : `${data.stats.slaCompliancePercent.toFixed(1)}%`
              }
              detail="Within target window"
              icon="check-circle"
              tone="success"
            />
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <DistributionCard title="By status" items={data.byStatus} />
            <DistributionCard title="By category" items={data.byCategory} />
            <DistributionCard title="By department" items={data.byDepartment} />
            <DistributionCard title="By jurisdiction" items={data.byJurisdiction} />
          </div>

          <Card className="p-5">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-base font-semibold text-stone-900">Governance queues</h2>
              <Badge tone="info">Action required</Badge>
            </div>
            <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {governance.map((queue) => (
                <Link key={queue.label} to={queue.to}>
                  <div className="flex h-full items-center justify-between gap-3 rounded-xl border border-stone-200 p-4 transition hover:border-brand-300 hover:bg-brand-50/40">
                    <div>
                      <p className="text-xs text-stone-600">{queue.label}</p>
                      <p className="mt-1 text-2xl font-semibold text-stone-900">{queue.value}</p>
                    </div>
                    <Icon name="arrow-right" className="h-4 w-4 text-brand-700" />
                  </div>
                </Link>
              ))}
            </div>
          </Card>

          <Card className="p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-stone-900">Notifications</h2>
                <p role="status" className="mt-1 text-xs text-stone-500">
                  {unread.isError
                    ? 'Unread count is unavailable right now.'
                    : `${unreadCount} unread notification${unreadCount === 1 ? '' : 's'}`}
                </p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {unread.isError && (
                  <Button variant="ghost" size="sm" onClick={() => void unread.refetch()}>
                    Retry count
                  </Button>
                )}
                <Link to="/notifications" className="text-xs font-semibold text-brand-700 hover:underline">
                  View all
                </Link>
              </div>
            </div>
            {notifications.isError ? (
              <div role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700">
                <span>{errorMessage(notifications.error, 'Could not load notifications.')}</span>
                <Button
                  variant="ghost"
                  size="sm"
                  className="ml-2"
                  onClick={() => void notifications.refetch()}
                >
                  Retry
                </Button>
              </div>
            ) : latestNotifications.length === 0 ? (
              <p className="mt-4 rounded-xl bg-stone-50 px-4 py-6 text-center text-xs text-stone-500">
                No notifications have been delivered to this account yet.
              </p>
            ) : (
              <ul className="mt-4 divide-y divide-stone-100">
                {latestNotifications.map((notification) => {
                  const content = (
                    <>
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge tone={notification.readAt ? 'neutral' : 'success'}>
                          {notification.readAt ? 'Read' : 'Unread'}
                        </Badge>
                        <span className="text-xs font-semibold text-stone-800">
                          {notification.title}
                        </span>
                        <span className="text-[11px] text-stone-500">
                          {formatDateTime(notification.createdAt)}
                        </span>
                      </div>
                      <p className="mt-1 line-clamp-2 text-xs text-stone-600">
                        {truncate(notification.body, 120)}
                      </p>
                    </>
                  )
                  return (
                    <li
                      key={notification.id}
                      className={`py-3 first:pt-0 ${notification.readAt ? '' : 'text-brand-900'}`}
                    >
                      {notification.complaintId ? (
                        <Link
                          to={complaintPathForRole('SUPER_ADMIN', notification.complaintId)}
                          className="block"
                        >
                          {content}
                        </Link>
                      ) : (
                        content
                      )}
                    </li>
                  )
                })}
              </ul>
            )}
          </Card>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card className="p-5">
              <h2 className="text-base font-semibold text-stone-900">Recent complaint activity</h2>
              {data.activity.length === 0 ? (
                <p className="mt-4 rounded-xl bg-stone-50 px-4 py-6 text-center text-xs text-stone-500">
                  No status changes in this period.
                </p>
              ) : (
                <ul className="mt-4 divide-y divide-stone-100">
                  {data.activity.map((entry, index) => (
                    <li key={`${entry.complaintId}-${entry.createdAt}-${index}`} className="py-3 first:pt-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <Link
                          to={`/admin/complaints/${entry.complaintId}`}
                          className="font-mono text-xs font-semibold text-brand-700 hover:underline"
                        >
                          {entry.complaintNumber}
                        </Link>
                        <span className="text-xs text-stone-600">
                          {humanizeStatus(entry.fromStatus)} → {humanizeStatus(entry.toStatus)}
                        </span>
                      </div>
                      <p className="mt-1 text-[11px] text-stone-500">
                        {formatDateTime(entry.createdAt)}
                        {entry.actorRole ? ` · ${humanizeStatus(entry.actorRole)}` : ''}
                      </p>
                      {entry.reason && (
                        <p className="mt-1 text-xs text-stone-600">{entry.reason}</p>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card className="p-5">
              <h2 className="text-base font-semibold text-stone-900">Recent audit entries</h2>
              {data.audit.length === 0 ? (
                <p className="mt-4 rounded-xl bg-stone-50 px-4 py-6 text-center text-xs text-stone-500">
                  No administrative changes in this period.
                </p>
              ) : (
                <ul className="mt-4 divide-y divide-stone-100">
                  {data.audit.map((entry) => (
                    <li key={entry.id} className="py-3 first:pt-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <Badge tone="neutral">{humanizeStatus(entry.action)}</Badge>
                        <span className="text-xs font-medium text-stone-800">
                          {entry.actorName ?? entry.actorRole ?? 'Unknown actor'}
                        </span>
                      </div>
                      <p className="mt-1 text-[11px] text-stone-500">
                        {entry.entityType}
                        {entry.entityId ? ` · ${entry.entityId}` : ''} · {formatDateTime(entry.createdAt)}
                      </p>
                      {entry.reason && <p className="mt-1 text-xs text-stone-600">{entry.reason}</p>}
                    </li>
                  ))}
                </ul>
              )}
              <Link to="/admin/audit" className="mt-4 inline-flex text-xs font-semibold text-brand-700 hover:underline">
                Open audit explorer
              </Link>
            </Card>
          </div>
        </div>
      )}
    </AppLayout>
  )
}
