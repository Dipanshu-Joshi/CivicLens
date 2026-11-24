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
  StatusBadge,
} from '../components/ui'
import {
  getUnreadNotificationCount,
  listNotifications,
} from '../lib/api/citizen'
import { errorMessage } from '../lib/api/errors'
import {
  getDepartmentOverview,
  type ActivityEntry,
  type PeriodDays,
  type TrendPoint,
} from '../lib/api/department'
import {
  PERIOD_OPTIONS,
  barWidth,
  formatHours,
  formatPercent,
  periodLabel,
  sortCategoryWorkload,
  sortWardWorkload,
  statusBuckets,
  trendScaleMax,
  workloadMax,
} from '../lib/utils/departmentAnalytics'
import { formatDate, formatDateTime, humanizeStatus, truncate } from '../lib/utils/format'

function TrendRow({ point, max }: { point: TrendPoint; max: number }) {
  return (
    <>
      <td className="px-4 py-3 text-sm font-medium text-stone-800">{point.period}</td>
      <td className="px-4 py-3">
        <div className="flex items-center gap-3">
          <span className="w-8 text-right text-xs font-semibold text-stone-700">
            {point.received}
          </span>
          <div className="h-2 w-full max-w-40 overflow-hidden rounded-full bg-stone-100">
            <div
              className="h-full rounded-full bg-brand-500"
              style={{ width: `${barWidth(point.received, max)}%` }}
            />
          </div>
        </div>
      </td>
      <td className="px-4 py-3">
        <div className="flex items-center gap-3">
          <span className="w-8 text-right text-xs font-semibold text-stone-700">
            {point.resolved}
          </span>
          <div className="h-2 w-full max-w-40 overflow-hidden rounded-full bg-stone-100">
            <div
              className="h-full rounded-full bg-clay-500"
              style={{ width: `${barWidth(point.resolved, max)}%` }}
            />
          </div>
        </div>
      </td>
    </>
  )
}

function ActivityItem({ entry }: { entry: ActivityEntry }) {
  return (
    <li>
      <Link
        to={`/department/complaints/${entry.complaintId}`}
        className="flex flex-col gap-2 px-5 py-4 transition hover:bg-stone-50 sm:flex-row sm:items-center sm:justify-between"
      >
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xs font-semibold text-brand-700">
              {entry.complaintNumber}
            </span>
            <StatusBadge status={entry.toStatus} />
          </div>
          <p className="mt-1 text-xs text-stone-600">
            {entry.fromStatus ? `${humanizeStatus(entry.fromStatus)} → ` : ''}
            {humanizeStatus(entry.toStatus)}
            {entry.actorRole ? ` · ${humanizeStatus(entry.actorRole)}` : ''}
          </p>
          {entry.reason && (
            <p className="mt-1 text-xs text-stone-500">{truncate(entry.reason, 130)}</p>
          )}
        </div>
        <span className="shrink-0 text-[11px] text-stone-500">
          {formatDateTime(entry.createdAt)}
        </span>
      </Link>
    </li>
  )
}

export default function DepartmentDashboard() {
  const [period, setPeriod] = useState<PeriodDays>(7)

  const overview = useQuery({
    queryKey: ['department', 'overview', period],
    queryFn: () => getDepartmentOverview(period),
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

  const periodControls = (
    <div role="group" aria-label="Reporting period" className="flex rounded-xl border border-stone-300 bg-white p-1">
      {PERIOD_OPTIONS.map((option) => (
        <button
          key={option}
          type="button"
          aria-pressed={period === option}
          onClick={() => setPeriod(option)}
          className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
            period === option
              ? 'bg-stone-900 text-stone-50'
              : 'text-stone-600 hover:bg-stone-100'
          }`}
        >
          {periodLabel(option)}
        </button>
      ))}
    </div>
  )

  if (overview.isPending) {
    return (
      <AppLayout>
        <PageHeader eyebrow="Department workspace" title="Department overview" />
        <Card className="flex items-center justify-center gap-3 p-12 text-sm text-stone-500">
          <Spinner className="h-5 w-5 text-brand-600" />
          <span role="status">Loading department overview…</span>
        </Card>
      </AppLayout>
    )
  }

  if (overview.isError || !overview.data) {
    return (
      <AppLayout>
        <PageHeader eyebrow="Department workspace" title="Department overview" />
        <div role="alert">
          <StatePanel
            tone="error"
            title="Could not load department overview"
            description={errorMessage(overview.error, 'The service did not return department analytics.')}
            action={
              <Button variant="secondary" onClick={() => void overview.refetch()}>
                Try again
              </Button>
            }
          />
        </div>
      </AppLayout>
    )
  }

  const { context, analytics, activity } = overview.data
  const buckets = statusBuckets(analytics.byStatus)
  const maxBucket = Math.max(1, ...buckets.map((bucket) => bucket.count))
  const categories = sortCategoryWorkload(analytics.byCategory)
  const wards = sortWardWorkload(analytics.byWard)
  const categoryMax = workloadMax(categories)
  const wardMax = workloadMax(wards)
  const trendMax = trendScaleMax(analytics.trend)
  const latestNotifications = notifications.data?.slice(0, 3) ?? []
  const unreadCount = unread.data ?? 0
  const needsAttention =
    context.verificationStatus !== 'VERIFIED' || !context.acceptsComplaints

  return (
    <AppLayout>
      <PageHeader
        eyebrow={`${context.departmentName} · ${context.departmentCode}`}
        title="Department overview"
        description="Live workload and service metrics calculated by the department API."
        actions={
          <>
            {periodControls}
            <Link to="/department/complaints">
              <Button icon={<Icon name="clipboard" className="h-4 w-4" />}>
                Complaint queue
              </Button>
            </Link>
          </>
        }
      />

      {needsAttention && (
        <div
          role="status"
          className="mb-6 flex items-start gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
        >
          <Icon name="warning" className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            Department verification is {humanizeStatus(context.verificationStatus)} and complaint
            intake is {context.acceptsComplaints ? 'enabled' : 'paused'}. Automated assignment may be
            unavailable until verification is complete and intake is enabled.
          </p>
        </div>
      )}

      <div className="space-y-6">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <StatCard
            label="Total complaints"
            value={analytics.total}
            detail="All recorded complaints for this department"
            icon="clipboard"
            tone="info"
          />
          <StatCard
            label={`Received · ${periodLabel(period)}`}
            value={analytics.receivedInPeriod}
            detail={periodLabel(period)}
            icon="sparkle"
          />
          <StatCard
            label={`Resolved · ${periodLabel(period)}`}
            value={analytics.resolvedInPeriod}
            detail="Closed within the selected window"
            icon="check-circle"
            tone="success"
          />
          <StatCard
            label="Overdue"
            value={analytics.overdue}
            detail="Past the resolution target"
            icon="clock"
            tone="danger"
          />
        </div>

        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {[
            { label: 'Pending', value: analytics.pending },
            { label: 'Accepted', value: analytics.accepted },
            { label: 'In progress', value: analytics.inProgress },
            { label: 'Awaiting assignment', value: analytics.unassigned },
          ].map((item) => (
            <Card key={item.label} className="flex items-center justify-between px-4 py-3">
              <span className="text-xs font-medium text-stone-500">{item.label}</span>
              <span className="text-lg font-semibold text-stone-900">{item.value}</span>
            </Card>
          ))}
        </div>

        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {[
            {
              label: 'Avg. resolution time',
              value: formatHours(analytics.averageResolutionHours),
            },
            { label: 'Avg. open age', value: formatHours(analytics.averageOpenAgeHours) },
            { label: 'SLA compliance', value: formatPercent(analytics.slaCompliancePercent) },
            { label: 'Reporting window', value: `${analytics.periodDays} days` },
          ].map((item) => (
            <Card key={item.label} className="px-4 py-3">
              <p className="text-xs font-medium text-stone-500">{item.label}</p>
              <p className="mt-1 text-lg font-semibold text-stone-900">{item.value}</p>
            </Card>
          ))}
        </div>

        {analytics.total === 0 && (
          <StatePanel
            icon="clipboard"
            title="No complaints yet"
            description="Complaints assigned to this department will appear in the queue and charts."
            action={
              <Link to="/department/complaints">
                <Button size="sm" variant="secondary">
                  Open complaint queue
                </Button>
              </Link>
            }
          />
        )}

        <div className="grid gap-6 lg:grid-cols-[1.35fr_0.65fr]">
          <Card className="overflow-hidden">
            <div className="border-b border-stone-200 px-5 py-4">
              <h2 className="text-sm font-semibold text-stone-900">Complaint trend</h2>
              <p className="mt-0.5 text-xs text-stone-500">
                Received versus resolved · {periodLabel(period)}
              </p>
            </div>
            {analytics.trend.length === 0 ? (
              <div className="p-6">
                <StatePanel icon="info" title="No trend data" description="No activity in this window." />
              </div>
            ) : (
              <>
                <div className="hidden overflow-x-auto sm:block">
                  <table className="w-full text-left">
                    <thead className="bg-stone-50 text-[11px] font-semibold tracking-wide text-stone-500 uppercase">
                      <tr>
                        <th scope="col" className="px-4 py-3">Period</th>
                        <th scope="col" className="px-4 py-3">Received</th>
                        <th scope="col" className="px-4 py-3">Resolved</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-stone-100">
                      {analytics.trend.map((point) => (
                        <tr key={point.period}>
                          <TrendRow point={point} max={trendMax} />
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <ul className="divide-y divide-stone-100 sm:hidden">
                  {analytics.trend.map((point) => (
                    <li key={point.period} className="space-y-2 px-5 py-4">
                      <div className="flex items-center justify-between text-xs">
                        <span className="font-semibold text-stone-800">{point.period}</span>
                        <span className="text-stone-500">
                          {point.received} in · {point.resolved} out
                        </span>
                      </div>
                      <div className="h-2 overflow-hidden rounded-full bg-stone-100">
                        <div
                          className="h-full rounded-full bg-brand-500"
                          style={{ width: `${barWidth(point.received, trendMax)}%` }}
                        />
                      </div>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </Card>

          <Card className="p-5">
            <h2 className="text-sm font-semibold text-stone-900">Status mix</h2>
            <p className="mt-0.5 text-xs text-stone-500">Based on current complaint statuses</p>
            {buckets.every((bucket) => bucket.count === 0) ? (
              <p className="mt-8 text-center text-sm text-stone-500">No status data available.</p>
            ) : (
              <div className="mt-5 space-y-4">
                {buckets.map((bucket) => (
                  <div key={bucket.key}>
                    <div className="mb-1.5 flex items-center justify-between gap-2 text-xs">
                      <span className="font-medium text-stone-700">{bucket.label}</span>
                      <Badge tone="neutral">{bucket.count}</Badge>
                    </div>
                    <div className="h-2 overflow-hidden rounded-full bg-stone-100">
                      <div
                        className="h-full rounded-full bg-brand-500"
                        style={{ width: `${barWidth(bucket.count, maxBucket)}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>

        <div className="grid gap-6 lg:grid-cols-2">
          <Card className="p-5">
            <h2 className="text-sm font-semibold text-stone-900">Workload by category</h2>
            {categories.length === 0 ? (
              <p className="mt-6 text-sm text-stone-500">No category data available.</p>
            ) : (
              <div className="mt-5 space-y-4">
                {categories.slice(0, 6).map((category) => (
                  <div key={category.code}>
                    <div className="mb-1.5 flex items-center justify-between gap-2 text-xs">
                      <span className="font-medium text-stone-700">{category.name}</span>
                      <span className="font-semibold text-stone-900">{category.count}</span>
                    </div>
                    <div className="h-2 overflow-hidden rounded-full bg-stone-100">
                      <div
                        className="h-full rounded-full bg-clay-500"
                        style={{ width: `${barWidth(category.count, categoryMax)}%` }}
                      />
                    </div>
                  </div>
                ))}
                {categories.length > 6 && (
                  <p className="text-xs text-stone-500">
                    +{categories.length - 6} more categories in the API response
                  </p>
                )}
              </div>
            )}
          </Card>

          <Card className="p-5">
            <h2 className="text-sm font-semibold text-stone-900">Workload by ward</h2>
            {wards.length === 0 ? (
              <p className="mt-6 text-sm text-stone-500">No ward data available.</p>
            ) : (
              <div className="mt-5 space-y-4">
                {wards.slice(0, 6).map((ward) => (
                  <div key={ward.ward}>
                    <div className="mb-1.5 flex items-center justify-between gap-2 text-xs">
                      <span className="font-medium text-stone-700">{ward.ward}</span>
                      <span className="font-semibold text-stone-900">{ward.count}</span>
                    </div>
                    <div className="h-2 overflow-hidden rounded-full bg-stone-100">
                      <div
                        className="h-full rounded-full bg-brand-500"
                        style={{ width: `${barWidth(ward.count, wardMax)}%` }}
                      />
                    </div>
                  </div>
                ))}
                {wards.length > 6 && (
                  <p className="text-xs text-stone-500">
                    +{wards.length - 6} more wards in the API response
                  </p>
                )}
              </div>
            )}
          </Card>
        </div>

        <div className="grid gap-6 lg:grid-cols-[1.35fr_0.65fr]">
          <Card className="overflow-hidden">
            <div className="flex items-center justify-between border-b border-stone-200 px-5 py-4">
              <div>
                <h2 className="text-sm font-semibold text-stone-900">Recent activity</h2>
                <p className="mt-0.5 text-xs text-stone-500">Latest status changes from the API</p>
              </div>
              <Link
                to="/department/complaints"
                className="text-xs font-semibold text-brand-700 hover:underline"
              >
                View queue
              </Link>
            </div>
            {activity.length === 0 ? (
              <div className="p-6">
                <StatePanel icon="clock" title="No recent activity" description="Status changes will appear here." />
              </div>
            ) : (
              <ul className="divide-y divide-stone-100">
                {activity.map((entry) => (
                  <ActivityItem key={`${entry.complaintId}-${entry.createdAt}`} entry={entry} />
                ))}
              </ul>
            )}
          </Card>

          <Card className="p-5">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-sm font-semibold text-stone-900">Notifications</h2>
              <Link to="/notifications" className="text-xs font-semibold text-brand-700 hover:underline">
                View all
              </Link>
            </div>
            <div className="mt-2 flex items-center gap-2">
              <p role="status" className="text-xs text-stone-500">
                {unread.isError
                  ? 'Unread count is unavailable right now.'
                  : `${unreadCount} unread notification${unreadCount === 1 ? '' : 's'}`}
              </p>
              {unread.isError && (
                <Button variant="ghost" size="sm" onClick={() => void unread.refetch()}>
                  Retry
                </Button>
              )}
            </div>
            {notifications.isError ? (
              <div role="alert" className="mt-4 text-sm text-red-600">
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
              <p className="mt-6 text-center text-sm text-stone-500">Nothing new to report.</p>
            ) : (
              <ul className="mt-4 space-y-3">
                {latestNotifications.map((notification) => {
                  const content = (
                    <>
                      <div className="flex items-center justify-between gap-2">
                        <span className="truncate text-xs font-semibold text-stone-800">
                          {notification.title}
                        </span>
                        <span className="shrink-0 text-[10px] text-stone-500">
                          {formatDate(notification.createdAt)}
                        </span>
                      </div>
                      <p className="mt-1 line-clamp-2 text-xs text-stone-600">
                        {truncate(notification.body, 110)}
                      </p>
                    </>
                  )
                  return (
                    <li
                      key={notification.id}
                      className={`rounded-xl border p-3 ${
                        notification.readAt
                          ? 'border-stone-200 bg-white'
                          : 'border-brand-200 bg-brand-50/60'
                      }`}
                    >
                      {notification.complaintId ? (
                        <Link to={`/department/complaints/${notification.complaintId}`}>
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
        </div>
      </div>
    </AppLayout>
  )
}
