import type { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DepartmentComplaintDetailPage from '../pages/DepartmentComplaintDetailPage'
import DepartmentComplaintsPage from '../pages/DepartmentComplaintsPage'
import DepartmentDashboard from '../pages/DepartmentDashboard'
import DepartmentProfilePage from '../pages/DepartmentProfilePage'

const mocks = vi.hoisted(() => ({
  getOverview: vi.fn(),
  listComplaints: vi.fn(),
  getComplaint: vi.fn(),
  listNotes: vi.fn(),
  acceptComplaint: vi.fn(),
  rejectComplaint: vi.fn(),
  updateStatus: vi.fn(),
  requestInfo: vi.fn(),
  addNote: vi.fn(),
  uploadEvidence: vi.fn(),
  resolveComplaint: vi.fn(),
  requestReassignment: vi.fn(),
  getProfile: vi.fn(),
  updateProfile: vi.fn(),
  listCapabilities: vi.fn(),
  updateCapabilities: vi.fn(),
  listTeamMembers: vi.fn(),
  updateTeamMember: vi.fn(),
  listServiceAreas: vi.fn(),
  createServiceArea: vi.fn(),
  updateServiceArea: vi.fn(),
  deleteServiceArea: vi.fn(),
  listNotifications: vi.fn(),
  getUnreadCount: vi.fn(),
  listCategories: vi.fn(),
}))

vi.mock('../components/AppLayout', () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}))

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return {
    ...actual,
    useParams: () => ({ id: 'complaint-1' }),
  }
})

vi.mock('../lib/api/department', () => ({
  getDepartmentOverview: mocks.getOverview,
  listDepartmentComplaints: mocks.listComplaints,
  getDepartmentComplaint: mocks.getComplaint,
  listDepartmentNotes: mocks.listNotes,
  acceptDepartmentComplaint: mocks.acceptComplaint,
  rejectDepartmentComplaint: mocks.rejectComplaint,
  updateDepartmentComplaintStatus: mocks.updateStatus,
  requestDepartmentComplaintInfo: mocks.requestInfo,
  addDepartmentNote: mocks.addNote,
  uploadDepartmentEvidence: mocks.uploadEvidence,
  resolveDepartmentComplaint: mocks.resolveComplaint,
  requestDepartmentReassignment: mocks.requestReassignment,
  getDepartmentProfile: mocks.getProfile,
  updateDepartmentProfile: mocks.updateProfile,
  listCapabilities: mocks.listCapabilities,
  updateCapabilities: mocks.updateCapabilities,
  listTeamMembers: mocks.listTeamMembers,
  updateTeamMember: mocks.updateTeamMember,
  listServiceAreas: mocks.listServiceAreas,
  createServiceArea: mocks.createServiceArea,
  updateServiceArea: mocks.updateServiceArea,
  deleteServiceArea: mocks.deleteServiceArea,
}))

vi.mock('../lib/api/citizen', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../lib/api/citizen')>()
  return {
    ...actual,
    listNotifications: mocks.listNotifications,
    getUnreadNotificationCount: mocks.getUnreadCount,
    listCategories: mocks.listCategories,
  }
})

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
  subcategoryCode: null,
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
  feedback: null,
  images: [],
  analysis: null,
  routingDecisions: [],
  internalNotes: [],
}

beforeEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  mocks.listNotifications.mockResolvedValue([])
  mocks.getUnreadCount.mockResolvedValue(0)
  mocks.listCategories.mockResolvedValue([])
  mocks.listNotes.mockResolvedValue([])
})

describe('department dashboard', () => {
  it('renders metrics returned by the department API', async () => {
    mocks.getOverview.mockResolvedValue({
      context: {
        departmentId: 'dept-1',
        departmentName: 'Roads Department',
        departmentCode: 'ROADS',
        verificationStatus: 'VERIFIED',
        acceptsComplaints: true,
      },
      analytics: {
        total: 12,
        byStatus: { ASSIGNED: 4, IN_PROGRESS: 3, RESOLVED: 5 },
        resolved: 5,
        overdue: 2,
        unassigned: 0,
        averageResolutionHours: 18.4,
        periodDays: 7,
        receivedInPeriod: 6,
        resolvedInPeriod: 5,
        pending: 0,
        accepted: 4,
        inProgress: 3,
        rejected: 0,
        closed: 0,
        byCategory: [{ code: 'ROAD', name: 'Roads', count: 7 }],
        byWard: [{ ward: 'WARD-12', count: 3 }],
        trend: [{ period: 'Mon', received: 2, resolved: 1 }],
        averageOpenAgeHours: 9.5,
        slaCompliancePercent: 91.2,
      },
      activity: [
        {
          complaintId: 'complaint-1',
          complaintNumber: 'CIV-2026-0001',
          fromStatus: 'SUBMITTED',
          toStatus: 'ASSIGNED',
          actorRole: 'SUPER_ADMIN',
          reason: 'Routed to Roads Department',
          createdAt: '2026-01-01T10:00:00Z',
        },
      ],
    })

    renderPage(<DepartmentDashboard />)

    expect(await screen.findByText('Roads Department · ROADS')).toBeInTheDocument()
    expect(screen.getAllByText('12').length).toBeGreaterThan(0)
    expect(screen.getByText('Overdue')).toBeInTheDocument()
    expect(screen.getByText('Complaint trend')).toBeInTheDocument()
    expect(screen.getByText('CIV-2026-0001')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Last 30 days' })).toBeInTheDocument()
  })

  it('shows a retryable error state when the API fails', async () => {
    mocks.getOverview.mockRejectedValue(new Error('Backend unavailable'))

    renderPage(<DepartmentDashboard />)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Could not load department overview')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})

describe('department complaint queue', () => {
  it('sends the selected status filter to the queue API', async () => {
    const user = userEvent.setup()
    mocks.listComplaints.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })

    renderPage(<DepartmentComplaintsPage />)

    expect(await screen.findByText('The queue is empty')).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Status'), 'IN_PROGRESS')
    await user.click(screen.getByRole('button', { name: 'Apply' }))

    await waitFor(() => {
      expect(mocks.listComplaints).toHaveBeenCalledWith(
        expect.objectContaining({
          status: 'IN_PROGRESS',
          page: 0,
          size: 20,
          sort: 'createdAt,desc',
        }),
      )
    })
  })

  it('applies zone and sort filters and supports pagination', async () => {
    const user = userEvent.setup()
    mocks.listComplaints.mockResolvedValue({
      content: [
        {
          ...complaintFixture,
          description: complaintFixture.description,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 25,
      totalPages: 2,
    })

    const { container } = renderPage(<DepartmentComplaintsPage />)

    expect((await screen.findAllByText('CIV-2026-0001')).length).toBeGreaterThan(0)
    expect(container.querySelector('.hidden.sm\\:block')).toBeTruthy()
    expect(container.querySelector('.grid.sm\\:hidden')).toBeTruthy()

    await user.type(screen.getByLabelText('Zone'), 'ZONE-A')
    await user.selectOptions(screen.getByLabelText(/Sort by/), 'createdAt,asc')
    await user.click(screen.getByRole('button', { name: 'Apply' }))

    await waitFor(() => {
      expect(mocks.listComplaints).toHaveBeenCalledWith(
        expect.objectContaining({
          zone: 'ZONE-A',
          sort: 'createdAt,asc',
        }),
      )
    })

    await user.click(screen.getByRole('button', { name: /Next/ }))
    await waitFor(() => {
      expect(mocks.listComplaints).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 1 }),
      )
    })
  })
})

describe('department complaint detail', () => {
  it('renders complaint evidence, history and department-private notes', async () => {
    mocks.getComplaint.mockResolvedValue({
      ...complaintFixture,
      feedback: {
        rating: 2,
        comment: 'Still not fixed.',
        resolutionConfirmed: false,
        disputeReason: 'The pothole remains outside the school gate.',
        createdAt: '2026-01-02T10:00:00Z',
      },
      timeline: [
        {
          fromStatus: 'SUBMITTED',
          toStatus: 'ASSIGNED',
          actorRole: 'SUPER_ADMIN',
          reason: 'Routed to Roads',
          createdAt: '2026-01-01T09:00:00Z',
        },
      ],
    })
    mocks.listNotes.mockResolvedValue([
      {
        id: 'note-1',
        authorName: 'Roads Admin',
        body: 'Site inspection scheduled for Tuesday.',
        createdAt: '2026-01-01T11:00:00Z',
      },
    ])

    renderPage(<DepartmentComplaintDetailPage />)

    expect(await screen.findByText('CIV-2026-0001')).toBeInTheDocument()
    expect(screen.getByText('Citizen description')).toBeInTheDocument()
    expect(screen.getByText('Status history')).toBeInTheDocument()
    expect(screen.getByText('Department-private; never shown to citizens.')).toBeInTheDocument()
    expect(screen.getByText('Site inspection scheduled for Tuesday.')).toBeInTheDocument()
    expect(screen.getByText('Citizen feedback')).toBeInTheDocument()
    expect(screen.getByText('Disputed')).toBeInTheDocument()
    expect(screen.getByText('The pothole remains outside the school gate.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Accept assignment' })).toBeEnabled()
  })

  it('offers a retry when the complaint cannot be loaded', async () => {
    mocks.getComplaint.mockRejectedValue(new Error('Not available'))

    renderPage(<DepartmentComplaintDetailPage />)

    expect(await screen.findByText('Could not load complaint')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})

describe('department profile', () => {
  it('renders identity, settings, capabilities, team and service areas', async () => {
    mocks.getProfile.mockResolvedValue({
      id: 'dept-1',
      name: 'Roads Department',
      departmentCode: 'ROADS',
      departmentType: 'MUNICIPAL',
      description: 'Maintains roads andfootpaths.',
      officialEmail: 'roads@example.com',
      contactNumber: '022-12345678',
      officeAddress: 'Civic Centre',
      city: 'Springfield',
      state: 'Central',
      country: 'India',
      verificationStatus: 'VERIFIED',
      active: true,
      acceptsComplaints: true,
      autoAssignEnabled: true,
      routingPriority: 10,
      maxOpenComplaints: 500,
    })
    mocks.listCapabilities.mockResolvedValue([
      {
        categoryId: 'category-1',
        categoryCode: 'ROAD',
        categoryName: 'Roads',
        primary: true,
        priority: 1,
      },
    ])
    mocks.listTeamMembers.mockResolvedValue([
      {
        userId: 'user-1',
        fullName: 'Roads Admin',
        email: 'admin@example.com',
        adminLevel: 'OWNER',
        primaryAdmin: true,
        title: 'Chief Engineer',
        accountStatus: 'ACTIVE',
      },
    ])
    mocks.listServiceAreas.mockResolvedValue([])

    renderPage(<DepartmentProfilePage />)

    expect(await screen.findByText('Roads Department')).toBeInTheDocument()
    expect(screen.getByText('Intake settings')).toBeInTheDocument()
    expect(screen.getByText('Category capabilities')).toBeInTheDocument()
    expect(
      screen.getByText((_content, element) => element?.textContent === 'Roads (ROAD)'),
    ).toBeInTheDocument()
    expect(screen.getByText('Team members')).toBeInTheDocument()
    expect(screen.getByText('admin@example.com')).toBeInTheDocument()
    expect(screen.getByText('No service areas are configured.')).toBeInTheDocument()
  })
})
