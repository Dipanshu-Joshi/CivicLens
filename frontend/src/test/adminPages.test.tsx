import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminAuditPage from '../pages/AdminAuditPage'
import AdminComplaintDetailPage from '../pages/AdminComplaintDetailPage'
import AdminComplaintsPage from '../pages/AdminComplaintsPage'
import AdminDashboard from '../pages/AdminDashboard'
import AdminDepartmentsPage from '../pages/AdminDepartmentsPage'
import AdminPlatformPage from '../pages/AdminPlatformPage'
import AdminUsersPage from '../pages/AdminUsersPage'

const mocks = vi.hoisted(() => ({
  getOverview: vi.fn(),
  getAnalytics: vi.fn(),
  getActivity: vi.fn(),
  listDepartments: vi.fn(),
  getDepartment: vi.fn(),
  verifyDepartment: vi.fn(),
  updateDepartmentStatus: vi.fn(),
  updateDepartmentOperations: vi.fn(),
  listUsers: vi.fn(),
  updateUserStatus: vi.fn(),
  updateUserRole: vi.fn(),
  listComplaints: vi.fn(),
  listUnassigned: vi.fn(),
  getQueue: vi.fn(),
  getComplaint: vi.fn(),
  getAnalyses: vi.fn(),
  getRoutingDecisions: vi.fn(),
  overrideClassification: vi.fn(),
  reroute: vi.fn(),
  assign: vi.fn(),
  unassign: vi.fn(),
  listReassignments: vi.fn(),
  reviewReassignment: vi.fn(),
  listCategories: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  listJurisdictions: vi.fn(),
  createJurisdiction: vi.fn(),
  listServiceAreas: vi.fn(),
  listAuditLogs: vi.fn(),
  listNotifications: vi.fn(),
  getUnreadCount: vi.fn(),
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

vi.mock('../lib/auth/AuthContext', () => ({ useAuth: mocks.useAuth }))

vi.mock('../lib/api/uploads', () => ({ getImageViewUrl: mocks.getImageViewUrl }))

vi.mock('../lib/api/citizen', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../lib/api/citizen')>()
  return {
    ...actual,
    listNotifications: mocks.listNotifications,
    getUnreadNotificationCount: mocks.getUnreadCount,
  }
})

vi.mock('../lib/api/admin', () => ({
  getAdminOverview: mocks.getOverview,
  getAdminAnalytics: mocks.getAnalytics,
  getAdminActivity: mocks.getActivity,
  listAdminDepartments: mocks.listDepartments,
  getAdminDepartment: mocks.getDepartment,
  verifyAdminDepartment: mocks.verifyDepartment,
  updateAdminDepartmentStatus: mocks.updateDepartmentStatus,
  updateAdminDepartmentOperations: mocks.updateDepartmentOperations,
  listAdminUsers: mocks.listUsers,
  updateAdminUserStatus: mocks.updateUserStatus,
  updateAdminUserRole: mocks.updateUserRole,
  listAdminComplaints: mocks.listComplaints,
  listUnassignedAdminComplaints: mocks.listUnassigned,
  getAdminComplaintQueue: mocks.getQueue,
  getAdminComplaint: mocks.getComplaint,
  listAdminAiAnalyses: mocks.getAnalyses,
  listAdminRoutingDecisions: mocks.getRoutingDecisions,
  overrideAdminClassification: mocks.overrideClassification,
  rerouteAdminComplaint: mocks.reroute,
  assignAdminComplaint: mocks.assign,
  unassignAdminComplaint: mocks.unassign,
  listAdminReassignmentRequests: mocks.listReassignments,
  reviewAdminReassignment: mocks.reviewReassignment,
  listAdminCategories: mocks.listCategories,
  createAdminCategory: mocks.createCategory,
  updateAdminCategory: mocks.updateCategory,
  listAdminJurisdictions: mocks.listJurisdictions,
  createAdminJurisdiction: mocks.createJurisdiction,
  listAdminServiceAreas: mocks.listServiceAreas,
  listAdminAuditLogs: mocks.listAuditLogs,
}))

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
  status: 'ASSIGNED',
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
  resolutionSummary: null,
  resolutionDueAt: '2026-01-03T09:00:00Z',
  createdAt: '2026-01-01T08:00:00Z',
  timeline: [],
  comments: [],
  assignments: [],
  feedback: {
    rating: 2,
    comment: 'The pothole is still there.',
    resolutionConfirmed: false,
    disputeReason: 'The repair did not cover the school entrance.',
    createdAt: '2026-01-02T10:00:00Z',
  },
  images: [],
  analysis: null,
}

beforeEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  vi.stubGlobal('confirm', vi.fn(() => true))
  mocks.useAuth.mockReturnValue({
    user: {
      id: 'admin-1',
      email: 'admin@example.com',
      fullName: 'Platform Admin',
      role: 'SUPER_ADMIN',
      accountStatus: 'ACTIVE',
      verificationStatus: null,
      departmentId: null,
      permissions: [],
    },
    isLoading: false,
    isAuthenticated: true,
    login: vi.fn(),
    logout: vi.fn(),
    registerCitizen: vi.fn(),
    registerDepartment: vi.fn(),
  })
  mocks.listDepartments.mockResolvedValue([])
  mocks.listUsers.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  mocks.getQueue.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  mocks.getAnalyses.mockResolvedValue({ analyses: [] })
  mocks.getRoutingDecisions.mockResolvedValue([])
  mocks.listReassignments.mockResolvedValue([])
  mocks.getDepartment.mockResolvedValue({})
  mocks.listCategories.mockResolvedValue([])
  mocks.listJurisdictions.mockResolvedValue([])
  mocks.listServiceAreas.mockResolvedValue([])
  mocks.listAuditLogs.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  mocks.listNotifications.mockResolvedValue([])
  mocks.getUnreadCount.mockResolvedValue(0)
  mocks.getImageViewUrl.mockResolvedValue({
    imageId: 'image-1',
    url: 'https://example.test/secure-image',
    expiresAt: '2026-01-02T08:00:00Z',
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('admin dashboard', () => {
  it('renders real overview counts, distributions and governance queues', async () => {
    mocks.getOverview.mockResolvedValue({
      periodDays: 30,
      stats: {
        total: 37,
        resolved: 21,
        pending: 9,
        unassigned: 3,
        overdue: 2,
        failedAi: 1,
        averageResolutionHours: 18.4,
        slaCompliancePercent: 91.2,
      },
      governance: {
        pendingDepartments: 4,
        suspendedDepartments: 1,
        ambiguous: 2,
        lowConfidence: 3,
        failedAi: 1,
        reassignmentRequests: 2,
      },
      byStatus: [{ key: 'ASSIGNED', label: 'Assigned', count: 12 }],
      byCategory: [{ key: 'ROAD', label: 'Roads', count: 7 }],
      byDepartment: [{ key: 'dept-1', label: 'Roads Department', count: 7 }],
      byJurisdiction: [{ key: 'jur-1', label: 'Central', count: 7 }],
      activity: [
        {
          complaintId: 'complaint-1',
          complaintNumber: 'CIV-2026-0001',
          fromStatus: 'SUBMITTED',
          toStatus: 'ASSIGNED',
          actorRole: 'SYSTEM',
          reason: 'Routed to Roads',
          createdAt: '2026-01-01T10:00:00Z',
        },
      ],
      audit: [
        {
          id: 'audit-1',
          actorName: 'Platform Admin',
          actorRole: 'SUPER_ADMIN',
          action: 'DEPARTMENT_VERIFY',
          entityType: 'department',
          entityId: 'dept-1',
          reason: 'Application reviewed',
          correlationId: 'corr-1',
          createdAt: '2026-01-01T11:00:00Z',
        },
      ],
    })

    mocks.listNotifications.mockResolvedValue([
      {
        id: 'notification-1',
        type: 'REASSIGNMENT_REQUESTED',
        title: 'Reassignment requested',
        body: 'Roads Department asked to transfer this complaint.',
        channel: 'IN_APP',
        deliveryStatus: 'SENT',
        readAt: null,
        createdAt: '2026-01-01T12:00:00Z',
        sentAt: '2026-01-01T12:00:01Z',
        complaintId: 'complaint-1',
        complaintNumber: 'CIV-2026-0001',
      },
    ])
    mocks.getUnreadCount.mockResolvedValue(2)

    const user = userEvent.setup()
    renderPage(<AdminDashboard />)

    expect(await screen.findByText('Total complaints')).toBeInTheDocument()
    expect(screen.getByText('Platform overview')).toBeInTheDocument()
    expect(screen.getAllByText('37').length).toBeGreaterThan(0)
    expect(screen.getByText('Departments awaiting verification')).toBeInTheDocument()
    expect(screen.getAllByText('CIV-2026-0001').length).toBeGreaterThan(0)
    expect(screen.getByText('Department Verify')).toBeInTheDocument()
    expect(screen.getByText('2 unread notifications')).toBeInTheDocument()
    expect(screen.getByText('Reassignment requested')).toBeInTheDocument()
    expect(mocks.getOverview).toHaveBeenCalledWith(30)

    await user.click(screen.getByRole('button', { name: 'Last 7 days' }))
    await waitFor(() => expect(mocks.getOverview).toHaveBeenCalledWith(7))
  })

  it('offers a retry when overview data cannot be loaded', async () => {
    mocks.getOverview.mockRejectedValue(new Error('Backend unavailable'))

    renderPage(<AdminDashboard />)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Could not load platform overview')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    await waitFor(() => expect(mocks.getOverview).toHaveBeenCalledTimes(2))
  })
})

describe('admin department governance', () => {
  it('requires an audited reason before verification is submitted', async () => {
    const user = userEvent.setup()
    mocks.listDepartments.mockResolvedValue([
      {
        id: 'dept-1',
        name: 'Rivers Department',
        departmentCode: 'RIVERS',
        departmentType: 'MUNICIPAL',
        officialEmail: 'rivers@example.com',
        city: 'Springfield',
        verificationStatus: 'PENDING_VERIFICATION',
        isActive: true,
        acceptsComplaints: false,
        verifiedAt: null,
      },
    ])
    mocks.updateDepartmentStatus.mockResolvedValue({})

    renderPage(<AdminDepartmentsPage />)

    expect(await screen.findByText('Rivers Department')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Verify' }))
    const reason = screen.getByLabelText(/Reason for change/)
    await user.type(reason, 'too short')
    await user.click(screen.getByRole('button', { name: 'Confirm status change' }))

    expect(await screen.findByText(/Enter a reason of 10/)).toBeInTheDocument()
    expect(mocks.updateDepartmentStatus).not.toHaveBeenCalled()

    await user.clear(reason)
    await user.type(reason, 'Verified after site inspection')
    await user.click(screen.getByRole('button', { name: 'Confirm status change' }))

    await waitFor(() =>
      expect(mocks.updateDepartmentStatus).toHaveBeenCalledWith('dept-1', {
        status: 'VERIFIED',
        reason: 'Verified after site inspection',
      }),
    )
    expect(await screen.findByText(/recorded in the audit log/)).toBeInTheDocument()
    expect(window.confirm).toHaveBeenCalled()
  })

  it('submits audited routing operations', async () => {
    const user = userEvent.setup()
    mocks.listDepartments.mockResolvedValue([
      {
        id: 'dept-1',
        name: 'Roads Department',
        departmentCode: 'ROADS',
        departmentType: 'MUNICIPAL',
        officialEmail: 'roads@example.com',
        city: 'Springfield',
        verificationStatus: 'VERIFIED',
        isActive: true,
        acceptsComplaints: true,
        verifiedAt: '2026-01-01T00:00:00Z',
      },
    ])
    mocks.getDepartment.mockResolvedValue({
      routingPriority: 10,
      maxOpenComplaints: 500,
      autoAssignEnabled: true,
    })
    mocks.updateDepartmentOperations.mockResolvedValue({})

    renderPage(<AdminDepartmentsPage />)

    expect(await screen.findByText('Roads Department')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Routing operations' }))
    expect(await screen.findByLabelText(/Routing priority/)).toHaveValue(10)
    await user.clear(screen.getByLabelText(/Routing priority/))
    await user.type(screen.getByLabelText(/Routing priority/), '25')
    await user.type(screen.getByLabelText(/Reason for change/), 'Capacity reviewed by super admin')
    await user.click(screen.getByRole('button', { name: 'Save operations' }))

    await waitFor(() =>
      expect(mocks.updateDepartmentOperations).toHaveBeenCalledWith('dept-1', {
        routingPriority: 25,
        maxOpenComplaints: 500,
        autoAssignEnabled: true,
        reason: 'Capacity reviewed by super admin',
      }),
    )
  })
})

describe('admin user management', () => {
  it('confirms status and role changes and disables self-changes', async () => {
    const user = userEvent.setup()
    mocks.listUsers.mockResolvedValue({
      content: [
        {
          id: 'admin-1',
          email: 'admin@example.com',
          fullName: 'Platform Admin',
          role: 'SUPER_ADMIN',
          accountStatus: 'ACTIVE',
          departmentId: null,
        },
        {
          id: 'user-2',
          email: 'citizen@example.com',
          fullName: 'Casey Citizen',
          role: 'CITIZEN',
          accountStatus: 'ACTIVE',
          departmentId: null,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    })
    mocks.updateUserStatus.mockResolvedValue({})
    mocks.updateUserRole.mockResolvedValue({})

    renderPage(<AdminUsersPage />)

    expect(await screen.findByText('Casey Citizen')).toBeInTheDocument()
    expect(screen.getByText(/Self-service changes are disabled here/)).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Change status for admin@example.com' }),
    ).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Change status for citizen@example.com' }))
    await user.selectOptions(screen.getByLabelText(/New account status/), 'SUSPENDED')
    await user.type(screen.getByLabelText(/Reason for change/), 'Repeated policy violations')
    await user.click(screen.getByRole('button', { name: 'Confirm change' }))
    await waitFor(() =>
      expect(mocks.updateUserStatus).toHaveBeenCalledWith('user-2', {
        status: 'SUSPENDED',
        reason: 'Repeated policy violations',
      }),
    )
    expect(window.confirm).toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Change role for citizen@example.com' }))
    await user.selectOptions(screen.getByLabelText(/New role/), 'DEPARTMENT_ADMIN')
    await user.type(screen.getByLabelText(/Reason for change/), 'Promoted to department administrator')
    await user.click(screen.getByRole('button', { name: 'Confirm change' }))
    await waitFor(() =>
      expect(mocks.updateUserRole).toHaveBeenCalledWith('user-2', {
        role: 'DEPARTMENT_ADMIN',
        reason: 'Promoted to department administrator',
      }),
    )
  })
})

describe('admin complaint queue', () => {
  it('applies governance filters and renders responsive links', async () => {
    const user = userEvent.setup()
    mocks.getQueue.mockResolvedValue({
      content: [complaintFixture],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })

    const { container } = renderPage(<AdminComplaintsPage />)

    expect((await screen.findAllByText('CIV-2026-0001')).length).toBeGreaterThan(0)
    expect(container.querySelector('.hidden.sm\\:block')).toBeTruthy()
    expect(container.querySelector('.grid.sm\\:hidden')).toBeTruthy()
    expect(
      screen.getAllByRole('link', { name: /CIV-2026-0001/ })[0].getAttribute('href'),
    ).toBe('/admin/complaints/complaint-1')

    await user.selectOptions(screen.getByLabelText('Status'), 'IN_PROGRESS')
    await user.type(screen.getByLabelText(/Routing outcome/), 'ASSIGNED')
    await user.click(screen.getByRole('button', { name: 'Apply' }))

    await waitFor(() =>
      expect(mocks.getQueue).toHaveBeenCalledWith(
        expect.objectContaining({
          status: 'IN_PROGRESS',
          routingOutcome: 'ASSIGNED',
          page: 0,
          size: 20,
          sort: 'createdAt,desc',
        }),
      ),
    )
  })
})

describe('admin complaint governance detail', () => {
  it('shows AI sources, override reasons and routing candidate score reasons', async () => {
    mocks.getComplaint.mockResolvedValue(complaintFixture)
    mocks.getAnalyses.mockResolvedValue({
      analyses: [
        {
          id: 'analysis-1',
          status: 'COMPLETED',
          source: 'AI',
          isCivicIssue: true,
          categoryCode: 'ROAD',
          subcategoryCode: 'POTHOLE',
          severity: 'HIGH',
          description: 'Pothole obstructing traffic.',
          confidence: 0.82,
          requiresHumanReview: false,
          adminOverrideReason: null,
          createdAt: '2026-01-01T08:05:00Z',
        },
        {
          id: 'analysis-2',
          status: 'COMPLETED',
          source: 'ADMIN_OVERRIDE',
          isCivicIssue: true,
          categoryCode: 'ROAD',
          subcategoryCode: 'POTHOLE',
          severity: 'CRITICAL',
          description: 'Escalated after site inspection.',
          confidence: null,
          requiresHumanReview: false,
          adminOverrideReason: 'Confirmed safety risk near school entrance',
          actorName: 'Platform Admin',
          actorRole: 'SUPER_ADMIN',
          createdAt: '2026-01-01T09:05:00Z',
        },
      ],
    })
    mocks.getRoutingDecisions.mockResolvedValue([
      {
        id: 'decision-1',
        outcome: 'ASSIGNED',
        current: true,
        reason: 'Best scoring eligible department',
        selectedDepartmentId: 'dept-1',
        rulesetVersion: 'v2',
        confidence: 0.82,
        candidates: JSON.stringify([
          {
            departmentId: 'dept-1',
            departmentName: 'Roads Department',
            geoScore: 40,
            geoMatch: 'JURISDICTION',
            catScore: 7,
            catMatch: 'SUBCATEGORY',
            priority: 10,
            total: 57,
            atCapacity: false,
            reasons: ['jurisdiction match', 'category capability 7'],
          },
        ]),
        createdAt: '2026-01-01T09:00:00Z',
      },
    ])

    renderPage(<AdminComplaintDetailPage />)

    expect(await screen.findByText('Generated by AI')).toBeInTheDocument()
    expect(screen.getByText('Administrator override')).toBeInTheDocument()
    expect(screen.getByText('Confirmed safety risk near school entrance')).toBeInTheDocument()
    expect(screen.getByText('Platform Admin · Super Admin')).toBeInTheDocument()
    expect(screen.getByText('category capability 7')).toBeInTheDocument()
    expect(screen.getByText('Best scoring eligible department')).toBeInTheDocument()
    expect(screen.getByText('Citizen feedback')).toBeInTheDocument()
    expect(screen.getByText('Disputed')).toBeInTheDocument()
    expect(screen.getByText('The repair did not cover the school entrance.')).toBeInTheDocument()
  })

  it('loads secure evidence URLs and submits confirmed classification overrides', async () => {
    const user = userEvent.setup()
    mocks.getComplaint.mockResolvedValue({
      ...complaintFixture,
      images: [
        {
          id: 'image-1',
          storageKey: 'secret/key',
          contentType: 'image/jpeg',
          sizeBytes: 1024,
          imageKind: 'CITIZEN',
          uploadStatus: 'VERIFIED',
          createdAt: '2026-01-01T08:00:00Z',
        },
      ],
    })

    renderPage(<AdminComplaintDetailPage />)

    expect(await screen.findByAltText('Citizen evidence')).toHaveAttribute(
      'src',
      'https://example.test/secure-image',
    )
    expect(mocks.getImageViewUrl).toHaveBeenCalledWith('image-1')

    await user.click(screen.getByRole('button', { name: 'Override classification' }))
    await user.type(screen.getByLabelText(/Category code/), 'ROAD')
    await user.type(screen.getByLabelText(/Subcategory code/), 'POTHOLE')
    await user.selectOptions(screen.getByLabelText(/^Severity/), 'CRITICAL')
    await user.type(screen.getByLabelText(/Override reason/), 'Inspection confirmed a deeper hazard')
    await user.click(screen.getByRole('button', { name: 'Apply override' }))

    await waitFor(() =>
      expect(mocks.overrideClassification).toHaveBeenCalledWith('complaint-1', {
        categoryCode: 'ROAD',
        subcategoryCode: 'POTHOLE',
        severity: 'CRITICAL',
        description: undefined,
        reason: 'Inspection confirmed a deeper hazard',
      }),
    )
    expect(window.confirm).toHaveBeenCalled()
  })
})

describe('admin platform reads', () => {
  it('renders categories, jurisdictions and service areas from the admin APIs', async () => {
    const user = userEvent.setup()
    mocks.listCategories.mockResolvedValue([
      {
        id: 'category-1',
        code: 'ROAD',
        name: 'Roads',
        description: 'Road defects',
        icon: null,
        color: null,
        displayOrder: 1,
        active: true,
        children: [
          {
            id: 'category-2',
            code: 'POTHOLE',
            name: 'Potholes',
            description: null,
            icon: null,
            color: null,
            displayOrder: 1,
            active: true,
            children: [],
          },
        ],
      },
    ])
    mocks.listJurisdictions.mockResolvedValue([
      { id: 'jur-1', code: 'CENTRAL', name: 'Central', description: 'Central ward', active: true },
    ])
    mocks.listServiceAreas.mockResolvedValue([
      {
        id: 'area-1',
        departmentId: 'dept-1',
        departmentName: 'Roads Department',
        name: 'Central coverage',
        coversWard: 'WARD-12',
        coversZone: null,
        jurisdictionCode: 'CENTRAL',
        active: true,
        createdAt: '2026-01-01T00:00:00Z',
      },
    ])

    renderPage(<AdminPlatformPage />)

    expect((await screen.findAllByText('Roads')).length).toBeGreaterThan(0)
    expect(screen.getByText('Potholes')).toBeInTheDocument()
    expect(screen.getByText('Central')).toBeInTheDocument()
    expect(screen.getByText('Central coverage')).toBeInTheDocument()
    expect(mocks.listCategories).toHaveBeenCalledWith(true)
    expect(mocks.listJurisdictions).toHaveBeenCalledWith(true)

    await user.selectOptions(screen.getByLabelText('Active state'), 'true')
    await user.click(screen.getByRole('button', { name: 'Apply filters' }))
    await waitFor(() => expect(mocks.listServiceAreas).toHaveBeenCalledWith({ active: true }))
  })
})

describe('admin audit explorer', () => {
  it('renders audit fields and applies server-side filters', async () => {
    const user = userEvent.setup()
    mocks.listAuditLogs.mockResolvedValue({
      content: [
        {
          id: 'audit-1',
          actorName: 'Platform Admin',
          actorRole: 'SUPER_ADMIN',
          action: 'DEPARTMENT_VERIFY',
          entityType: 'department',
          entityId: 'dept-1',
          reason: 'Verified after documents checked',
          correlationId: 'corr-123',
          createdAt: '2026-01-01T11:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })

    renderPage(<AdminAuditPage />)

    expect((await screen.findAllByText('Platform Admin')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Department Verify').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Verified after documents checked').length).toBeGreaterThan(0)
    expect(screen.getAllByText('corr-123').length).toBeGreaterThan(0)

    await user.type(screen.getByLabelText(/^Action/), 'DEPARTMENT_VERIFY')
    await user.click(screen.getByRole('button', { name: 'Apply filters' }))
    await waitFor(() =>
      expect(mocks.listAuditLogs).toHaveBeenCalledWith(
        expect.objectContaining({ action: 'DEPARTMENT_VERIFY', page: 0, size: 20 }),
      ),
    )
  })
})
