import type { ComplaintStatus } from '../api/citizen'
import type {
  CategoryCount,
  PeriodDays,
  TrendPoint,
  WardCount,
} from '../api/department'

export const PERIOD_OPTIONS: readonly PeriodDays[] = [7, 30, 90]

export function periodLabel(days: PeriodDays | number): string {
  return `Last ${days} days`
}

export function formatHours(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—'
  return `${value.toFixed(1)} h`
}

export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '—'
  return `${Math.round(value)}%`
}

interface BucketDefinition {
  key: string
  label: string
  statuses: ComplaintStatus[]
}

const BUCKET_DEFINITIONS: BucketDefinition[] = [
  {
    key: 'pending',
    label: 'Pending intake',
    statuses: [
      'SUBMITTED',
      'AI_ANALYSIS_PENDING',
      'AI_ANALYSIS_COMPLETED',
      'AI_ANALYSIS_FAILED',
      'PENDING_ASSIGNMENT',
      'UNASSIGNED',
    ],
  },
  {
    key: 'active',
    label: 'In progress',
    statuses: ['ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'ON_HOLD'],
  },
  {
    key: 'resolved',
    label: 'Resolved',
    statuses: ['RESOLVED', 'REOPENED', 'CLOSED'],
  },
  { key: 'rejected', label: 'Rejected', statuses: ['REJECTED'] },
]

export interface StatusBucket {
  key: string
  label: string
  count: number
}

/** Groups raw status counts into display buckets without losing unknown keys. */
export function statusBuckets(byStatus: Record<string, number> = {}): StatusBucket[] {
  const buckets: StatusBucket[] = BUCKET_DEFINITIONS.map((definition) => ({
    key: definition.key,
    label: definition.label,
    count: definition.statuses.reduce(
      (sum, status) => sum + (Number.isFinite(byStatus[status]) ? byStatus[status] : 0),
      0,
    ),
  }))

  const known = new Set<string>(BUCKET_DEFINITIONS.flatMap((definition) => definition.statuses))
  let other = 0
  for (const [status, count] of Object.entries(byStatus)) {
    if (!known.has(status) && Number.isFinite(count) && count > 0) other += count
  }
  if (other > 0) buckets.push({ key: 'other', label: 'Other', count: other })

  return buckets
}

/** Largest trend value, with a floor of one so empty charts remain valid. */
export function trendScaleMax(points: TrendPoint[]): number {
  const values = points.flatMap((point) => [point.received, point.resolved])
  return Math.max(1, ...values.filter((value) => Number.isFinite(value)))
}

/** Bar width as a bounded percentage of the supplied scale. */
export function barWidth(value: number, max: number): number {
  if (!Number.isFinite(value) || !Number.isFinite(max) || max <= 0) return 0
  return Math.max(0, Math.min(100, (value / max) * 100))
}

export function workloadMax(items: Array<{ count: number }>): number {
  return Math.max(1, ...items.map((item) => item.count).filter((value) => Number.isFinite(value)))
}

export function sortCategoryWorkload(items: CategoryCount[]): CategoryCount[] {
  return [...items].sort((a, b) => b.count - a.count || a.code.localeCompare(b.code))
}

export function sortWardWorkload(items: WardCount[]): WardCount[] {
  return [...items].sort((a, b) => b.count - a.count || a.ward.localeCompare(b.ward))
}
