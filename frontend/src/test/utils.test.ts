import { describe, expect, it } from 'vitest'
import { buildDashboardStats } from '../lib/utils/analytics'
import { latLonToTile, pointToLatLon, tileToLatLon } from '../lib/utils/geo'
import { formatBytes, validateImageFiles } from '../lib/utils/images'
import { humanizeStatus } from '../lib/utils/format'
import type { ComplaintSummary } from '../lib/api/citizen'

function complaint(id: string, status: ComplaintSummary['status'], createdAt: string): ComplaintSummary {
  return {
    id,
    complaintNumber: `CIV-2026-${id}`,
    description: 'A clear description of the issue.',
    status,
    severity: 'MEDIUM',
    categoryCode: null,
    ward: null,
    city: null,
    departmentId: null,
    createdAt,
  }
}

describe('citizen dashboard aggregation', () => {
  it('derives every count from the supplied complaint rows', () => {
    const complaints = [
      complaint('1', 'SUBMITTED', '2026-01-01T10:00:00Z'),
      complaint('2', 'PENDING_ASSIGNMENT', '2026-01-02T10:00:00Z'),
      complaint('3', 'IN_PROGRESS', '2026-01-03T10:00:00Z'),
      complaint('4', 'RESOLVED', '2026-01-04T10:00:00Z'),
      complaint('5', 'CLOSED', '2026-01-05T10:00:00Z'),
    ]

    const stats = buildDashboardStats(complaints)

    expect(stats.total).toBe(5)
    expect(stats.pending).toBe(2)
    expect(stats.inProgress).toBe(1)
    expect(stats.resolved).toBe(2)
    expect(stats.recent).toHaveLength(5)
    expect(stats.recent[0]?.id).toBe('5')
    expect(stats.distribution.reduce((sum, slice) => sum + slice.count, 0)).toBe(5)
  })

  it('returns empty statistics when there are no rows', () => {
    const stats = buildDashboardStats([])
    expect(stats.total).toBe(0)
    expect(stats.pending).toBe(0)
    expect(stats.distribution).toEqual([])
    expect(stats.recent).toEqual([])
  })
})

describe('map tile helpers', () => {
  it('round-trips latitude and longitude through tile coordinates', () => {
    const tile = latLonToTile(28.6139, 77.209, 14)
    const point = tileToLatLon(tile.x, tile.y, tile.z)
    expect(point.latitude).toBeCloseTo(28.6139, 4)
    expect(point.longitude).toBeCloseTo(77.209, 4)
  })

  it('converts a viewport click into a valid location', () => {
    const center = latLonToTile(28.6139, 77.209, 14)
    const point = pointToLatLon(320, 150, 640, 300, center)
    expect(point.latitude).toBeCloseTo(28.6139, 4)
    expect(point.longitude).toBeCloseTo(77.209, 4)
  })
})

describe('image validation', () => {
  it('accepts images and rejects unsupported or oversized files', () => {
    const image = new File(['image'], 'pothole.png', { type: 'image/png' })
    const documentFile = new File(['text'], 'notes.txt', { type: 'text/plain' })
    const huge = new File([new Uint8Array(11 * 1024 * 1024)], 'huge.jpg', { type: 'image/jpeg' })

    const result = validateImageFiles([image, documentFile, huge])

    expect(result.accepted).toHaveLength(1)
    expect(result.accepted[0]?.file.name).toBe('pothole.png')
    expect(result.rejected.map((item) => item.reason)).toEqual([
      'Use JPEG, PNG, or WebP',
      'Larger than 10 MB',
    ])
  })

  it('caps the number of selected photos', () => {
    const files = Array.from(
      { length: 6 },
      (_, index) => new File(['x'], `photo-${index}.png`, { type: 'image/png' }),
    )
    const result = validateImageFiles(files)
    expect(result.accepted).toHaveLength(5)
    expect(result.rejected[0]?.reason).toBe('Maximum 5 photos')
  })

  it('formats file sizes for the review step', () => {
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(2048)).toBe('2 KB')
  })
})

describe('display helpers', () => {
  it('humanizes enum values', () => {
    expect(humanizeStatus('PENDING_ASSIGNMENT')).toBe('Pending Assignment')
    expect(humanizeStatus('DEPARTMENT_ADMIN')).toBe('Department Admin')
  })
})
