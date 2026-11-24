import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { complaintPathForRole } from '../lib/notifications'
import ComplaintDetailPage from '../pages/ComplaintDetailPage'
import NotificationsPage from '../pages/NotificationsPage'

const mocks = vi.hoisted(() => ({
  listNotifications: vi.fn(),
  getUnreadCount: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
  getMyComplaint: vi.fn(),
  addComment: vi.fn(),
  respondToInfoRequest: vi.fn(),
  reopenComplaint: vi.fn(),
  submitFeedback: vi.fn(),
  getImageViewUrl: vi.fn(),
  useAuth: vi.fn(),
}))

vi.mock('../components/AppLayout', () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}))

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useParams: () => ({ id: 'complaint-1' }) }
})

vi.mock('../lib/api/citizen', () => ({
  listNotifications: mocks.listNotifications,
  getUnreadNotificationCount: mocks.getUnreadCount,
  markNotificationRead: mocks.markNotificationRead,
  markAllNotificationsRead: mocks.markAllNotificationsRead,
  getMyComplaint: mocks.getMyComplaint,
  addComment: mocks.addComment,
  respondToInfoRequest: mocks.respondToInfoRequest,
  reopenComplaint: mocks.reopenComplaint,
  submitFeedback: mocks.submitFeedback,
}))

vi.mock('../lib/auth/AuthContext', () => ({ useAuth: mocks.useAuth }))

vi.mock('../lib/api/uploads', () => ({ getImageViewUrl: mocks.getImageViewUrl }))

function renderPage(page: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{page}</MemoryRouter>
    </QueryClientProvider>,
  )
}

const complaintFixture = {
  id: 'complaint-1',
  complaintNumber: 'CIV-2026-0001',
  description: 'A large pothole is blocking the school entrance.',
  additionalInfo: null,
  status: 'IN_PROGRESS',
  severity: 'HIGH',
  categoryCode: 'ROAD',
  subcategoryCode: 'POTHOLE',
  addressLine: '12 School Road',
  city: 'Springfield',
  state: 'Central',
  postalCode: '400001',
  country: 'India',
  ward: 'WARD-12',
  zone: 'ZONE-A',
  locationSource: 'GPS',
  latitude: 21.1,
  longitude: 79.1,
  departmentId: 'dept-1',
  departmentName: 'Roads Department',
  assignedAt: '2026-01-01T09:00:00Z',
  resolvedAt: null,
  resolutionDueAt: '2026-01-03T09:00:00Z',
  createdAt: '2026-01-01T08:00:00Z',
  timeline: [],
  comments: [
    {
      id: 'comment-1',
      authorName: 'Roads Department',
      authorRole: 'DEPARTMENT_ADMIN',
      body: 'Please confirm whether the pothole is still blocking the entrance.',
      createdAt: '2026-01-02T09:00:00Z',
    },
  ],
  assignments: [],
  feedback: null,
  images: [],
  analysis: null,
}

beforeEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  mocks.listNotifications.mockResolvedValue([])
  mocks.getUnreadCount.mockResolvedValue(0)
  mocks.getImageViewUrl.mockResolvedValue({
    imageId: 'image-1',
    url: 'https://example.test/secure-image',
    expiresAt: '2026-01-02T08:00:00Z',
  })
  mocks.useAuth.mockReturnValue({
    user: {
      id: 'user-1',
      email: 'citizen@example.com',
      fullName: 'Test Citizen',
      role: 'CITIZEN',
      accountStatus: 'ACTIVE',
      verificationStatus: null,
      departmentId: null,
      permissions: [],
    },
    isLoading: false,
    isAuthenticated: true,
  })
})

describe('phase 9 notification navigation', () => {
  it('maps complaint links to the recipient role surface', () => {
    expect(complaintPathForRole('CITIZEN', 'complaint-1')).toBe('/complaints/complaint-1')
    expect(complaintPathForRole('DEPARTMENT_ADMIN', 'complaint-1')).toBe(
      '/department/complaints/complaint-1',
    )
    expect(complaintPathForRole('SUPER_ADMIN', 'complaint-1')).toBe('/admin/complaints/complaint-1')
  })

  it('uses the numeric unread count and role-aware complaint link', async () => {
    mocks.getUnreadCount.mockResolvedValue(2)
    mocks.useAuth.mockReturnValue({
      user: {
        id: 'dept-admin-1',
        email: 'department@example.com',
        fullName: 'Department Admin',
        role: 'DEPARTMENT_ADMIN',
        accountStatus: 'ACTIVE',
        verificationStatus: 'VERIFIED',
        departmentId: 'dept-1',
        permissions: [],
      },
      isLoading: false,
      isAuthenticated: true,
    })
    mocks.listNotifications.mockResolvedValue([
      {
        id: 'notification-1',
        type: 'INFO_REQUESTED',
        title: 'More information requested',
        body: 'Please confirm the location.',
        channel: 'IN_APP',
        deliveryStatus: 'SENT',
        readAt: null,
        createdAt: '2026-01-01T10:00:00Z',
        sentAt: '2026-01-01T10:00:01Z',
        complaintId: 'complaint-1',
        complaintNumber: 'CIV-2026-0001',
      },
    ])

    const user = userEvent.setup()
    renderPage(<NotificationsPage />)

    expect(await screen.findByText('More information requested')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Unread only \(2\)/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /View complaint CIV-2026-0001/ })).toHaveAttribute(
      'href',
      '/department/complaints/complaint-1',
    )

    await user.click(screen.getByRole('button', { name: 'Mark read' }))
    await waitFor(() =>
      expect(mocks.markNotificationRead).toHaveBeenCalledWith('notification-1', expect.anything()),
    )
  })
})

describe('phase 9 citizen workflow', () => {
  it('shows a pending information request and posts a dedicated reply', async () => {
    mocks.getMyComplaint.mockResolvedValue(complaintFixture)
    mocks.respondToInfoRequest.mockResolvedValue(undefined)

    const user = userEvent.setup()
    renderPage(<ComplaintDetailPage />)

    expect(await screen.findByText('Information requested')).toBeInTheDocument()
    expect(
      screen.getAllByText('Please confirm whether the pothole is still blocking the entrance.')
        .length,
    ).toBeGreaterThan(0)

    await user.type(
      screen.getByLabelText('Reply to information request'),
      'Yes, it is still blocking the entrance.',
    )
    await user.click(screen.getByRole('button', { name: 'Send response' }))

    await waitFor(() =>
      expect(mocks.respondToInfoRequest).toHaveBeenCalledWith(
        'complaint-1',
        'Yes, it is still blocking the entrance.',
      ),
    )
    expect(mocks.addComment).not.toHaveBeenCalled()
  })
})
