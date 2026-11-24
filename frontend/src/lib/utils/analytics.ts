import type { ComplaintStatus, ComplaintSummary } from '../api/citizen'

const PENDING: ComplaintStatus[] = [
  'SUBMITTED',
  'AI_ANALYSIS_PENDING',
  'AI_ANALYSIS_FAILED',
  'PENDING_ASSIGNMENT',
  'UNASSIGNED',
]

const IN_PROGRESS: ComplaintStatus[] = ['ASSIGNED', 'ACKNOWLEDGED', 'IN_PROGRESS', 'ON_HOLD']

const RESOLVED: ComplaintStatus[] = ['RESOLVED', 'REOPENED', 'REJECTED', 'CLOSED']

export interface StatusSlice {
  status: ComplaintStatus
  count: number
}

export interface CitizenDashboardStats {
  total: number
  pending: number
  inProgress: number
  resolved: number
  recent: ComplaintSummary[]
  distribution: StatusSlice[]
}

/** Display-only aggregation of the authenticated citizen's persisted rows. */
export function buildDashboardStats(complaints: ComplaintSummary[]): CitizenDashboardStats {
  const counts = new Map<ComplaintStatus, number>()
  for (const complaint of complaints) {
    counts.set(complaint.status, (counts.get(complaint.status) ?? 0) + 1)
  }

  const distribution = [...counts.entries()]
    .map(([status, count]) => ({ status, count }))
    .sort((a, b) => b.count - a.count || a.status.localeCompare(b.status))

  const countFor = (statuses: ComplaintStatus[]) =>
    statuses.reduce((sum, status) => sum + (counts.get(status) ?? 0), 0)

  return {
    total: complaints.length,
    pending: countFor(PENDING),
    inProgress: countFor(IN_PROGRESS),
    resolved: countFor(RESOLVED),
    recent: [...complaints]
      .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
      .slice(0, 5),
    distribution,
  }
}
