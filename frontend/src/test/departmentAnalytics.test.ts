import { describe, expect, it } from 'vitest'
import type { TrendPoint } from '../lib/api/department'
import {
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

describe('department analytics helpers', () => {
  it('labels supported reporting periods', () => {
    expect(periodLabel(7)).toBe('Last 7 days')
    expect(periodLabel(30)).toBe('Last 30 days')
    expect(periodLabel(90)).toBe('Last 90 days')
  })

  it('formats optional numeric metrics without inventing values', () => {
    expect(formatHours(null)).toBe('—')
    expect(formatHours(undefined)).toBe('—')
    expect(formatHours(Number.NaN)).toBe('—')
    expect(formatHours(12.36)).toBe('12.4 h')
    expect(formatPercent(null)).toBe('—')
    expect(formatPercent(87.6)).toBe('88%')
  })

  it('groups status counts and preserves unknown statuses', () => {
    const buckets = statusBuckets({
      SUBMITTED: 4,
      ASSIGNED: 2,
      IN_PROGRESS: 1,
      RESOLVED: 3,
      REJECTED: 1,
      MYSTERY: 2,
    })

    expect(buckets).toEqual([
      { key: 'pending', label: 'Pending intake', count: 4 },
      { key: 'active', label: 'In progress', count: 3 },
      { key: 'resolved', label: 'Resolved', count: 3 },
      { key: 'rejected', label: 'Rejected', count: 1 },
      { key: 'other', label: 'Other', count: 2 },
    ])
    expect(buckets.reduce((sum, bucket) => sum + bucket.count, 0)).toBe(13)
  })

  it('scales trend bars from the API values', () => {
    const points: TrendPoint[] = [
      { period: 'Mon', received: 4, resolved: 2 },
      { period: 'Tue', received: 10, resolved: 6 },
    ]

    expect(trendScaleMax([])).toBe(1)
    expect(trendScaleMax(points)).toBe(10)
    expect(barWidth(5, 10)).toBe(50)
    expect(barWidth(25, 10)).toBe(100)
    expect(barWidth(-2, 10)).toBe(0)
    expect(barWidth(5, 0)).toBe(0)
  })

  it('sorts workload deterministically by count then label', () => {
    const categories = sortCategoryWorkload([
      { code: 'WATER', name: 'Water', count: 3 },
      { code: 'ROAD', name: 'Roads', count: 5 },
      { code: 'PARKS', name: 'Parks', count: 5 },
    ])
    const wards = sortWardWorkload([
      { ward: 'WARD-2', count: 1 },
      { ward: 'WARD-1', count: 4 },
    ])

    expect(categories.map((item) => item.code)).toEqual(['PARKS', 'ROAD', 'WATER'])
    expect(wards.map((item) => item.ward)).toEqual(['WARD-1', 'WARD-2'])
    expect(workloadMax([])).toBe(1)
    expect(workloadMax(categories)).toBe(5)
  })
})
