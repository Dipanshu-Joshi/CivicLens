import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios, { AxiosError } from 'axios'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import {
  DEMO_ACCOUNTS,
  DEMO_CREDENTIALS,
  demoEnabledFor,
  type DemoAccountKey,
} from '../demo/feature'
import {
  DEMO_SESSION_KEY,
  DEMO_STATE_KEY,
  clearDemoSession,
  readDemoState,
  resetDemoState,
} from '../demo/storage'
import { switchDemoAccount } from '../demo/session'
import {
  getMe,
  login,
  logout,
  setAccessToken,
} from '../lib/api/client'
import {
  addComment,
  getUnreadNotificationCount,
  listCategories,
  listMyComplaints,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  reopenComplaint,
  submitComplaint,
  submitFeedback,
} from '../lib/api/citizen'
import {
  acceptDepartmentComplaint,
  addDepartmentNote,
  attachDepartmentEvidence,
  getDepartmentComplaint,
  listDepartmentComplaints,
  requestDepartmentComplaintInfo,
  requestDepartmentReassignment,
  resolveDepartmentComplaint,
  updateDepartmentComplaintStatus,
} from '../lib/api/department'
import {
  assignAdminComplaint,
  getAdminComplaint,
  getAdminComplaintQueue,
  getAdminOverview,
  listAdminAuditLogs,
  overrideAdminClassification,
  rerouteAdminComplaint,
  reviewAdminReassignment,
  unassignAdminComplaint,
  updateAdminDepartmentStatus,
  updateAdminUserRole,
  updateAdminUserStatus,
} from '../lib/api/admin'
import { confirmUpload, presignUpload, putObject } from '../lib/api/uploads'
import { DemoAccountSwitcher, DemoResetButton } from '../demo/DemoControls'
import { AuthProvider } from '../lib/auth/AuthContext'
import LoginPage from '../pages/LoginPage'

async function expectApiError(promise: Promise<unknown>, status?: number): Promise<void> {
  try {
    await promise
  } catch (error) {
    expect(axios.isAxiosError(error)).toBe(true)
    if (status !== undefined) {
      expect((error as AxiosError).response?.status).toBe(status)
    }
    return
  }
  throw new Error('Expected the demo request to fail')
}

async function signIn(key: DemoAccountKey) {
  const account = DEMO_ACCOUNTS.find((item) => item.key === key)!
  const result = await login(account.email, account.password)
  expect(result.user.email).toBe(account.email)
  return result
}

beforeEach(() => {
  resetDemoState()
  clearDemoSession()
  setAccessToken(null)
  localStorage.removeItem('resetToken')
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('demo feature gate and credentials', () => {
  it('exposes exactly the three documented accounts behind the dev gate', () => {
    expect(demoEnabledFor(true)).toBe(true)
    expect(demoEnabledFor(false)).toBe(false)
    expect(DEMO_ACCOUNTS.map((account) => account.email)).toEqual([
      DEMO_CREDENTIALS.citizen.email,
      DEMO_CREDENTIALS.department.email,
      DEMO_CREDENTIALS.admin.email,
    ])
    expect(DEMO_ACCOUNTS.every((account) => account.password === 'Demo@123')).toBe(true)
    expect(readDemoState().complaints.length).toBeGreaterThanOrEqual(15)
    expect(readDemoState().departments.length).toBeGreaterThanOrEqual(5)
    expect(readDemoState().users.length).toBeGreaterThanOrEqual(10)
    expect(readDemoState().notifications.length).toBeGreaterThanOrEqual(10)
  })

  it('signs in each account and returns role-correct identity', async () => {
    const expected = {
      citizen: 'CITIZEN',
      department: 'DEPARTMENT_ADMIN',
      admin: 'SUPER_ADMIN',
    } as const
    for (const account of DEMO_ACCOUNTS) {
      const result = await signIn(account.key)
      expect(result.user.role).toBe(expected[account.key])
      const me = await getMe()
      expect(me.role).toBe(expected[account.key])
      expect(me.email).toBe(account.email)
      await logout()
    }
  })

  it('rejects invalid demo credentials and unknown accounts', async () => {
    await expectApiError(login(DEMO_CREDENTIALS.citizen.email, 'Wrong@123'), 400)
    await expectApiError(login('nobody@demo.com', 'Demo@123'), 400)
  })

  it('keeps cached identity and session data out of the resetToken key', () => {
    expect(localStorage.getItem('resetToken')).toBeNull()
    expect(localStorage.getItem(DEMO_STATE_KEY)).not.toBeNull()
  })
})

describe('demo session lifecycle', () => {
  it('persists across reads, logs out, and switches accounts', async () => {
    await signIn('citizen')
    const created = await submitComplaint({
      description: 'Deep pothole on MP Nagar road after the rain',
      latitude: 23.2599,
      longitude: 77.4126,
      locationSource: 'MANUAL',
      ward: 'WARD-12',
      zone: 'ZONE-A',
      city: 'Bhopal',
    })
    expect(readDemoState().complaints.some((item) => item.id === created.id)).toBe(true)
    expect(localStorage.getItem(DEMO_STATE_KEY)).toContain(created.id)

    await logout()
    expect(localStorage.getItem(DEMO_SESSION_KEY)).toBeNull()
    await expectApiError(getMe(), 403)

    const session = switchDemoAccount(DEMO_CREDENTIALS.admin.email)
    setAccessToken(session.token)
    expect((await getMe()).role).toBe('SUPER_ADMIN')
    expect(localStorage.getItem(DEMO_STATE_KEY)).toContain(created.id)
  })

  it('denies cross-role API access without weakening the route guards', async () => {
    await signIn('citizen')
    await expectApiError(getAdminOverview(), 403)
    await expectApiError(listAdminAuditLogs(), 403)
    await logout()

    await signIn('department')
    await expectApiError(listMyComplaints(), 403)
    await expectApiError(getAdminOverview(), 403)
    await logout()

    await signIn('admin')
    await expectApiError(listMyComplaints(), 403)
    await expectApiError(listDepartmentComplaints({ page: 0, size: 5 }), 403)
  })
})

describe('demo citizen workflow', () => {
  it('creates, comments on, reopens and reviews a complaint with locally stored notifications', async () => {
    await signIn('citizen')
    const categories = await listCategories()
    expect(categories.length).toBeGreaterThan(0)

    const created = await submitComplaint({
      description: 'Broken footpath slab near a school; children are at risk',
      latitude: 23.2612,
      longitude: 77.4193,
      locationSource: 'GPS',
      categoryCode: 'ROAD',
      subcategoryCode: 'FOOTPATH',
      ward: 'WARD-07',
      zone: 'ZONE-B',
      city: 'Bhopal',
    })
    expect(created.status).not.toBe('SUBMITTED')
    expect(created.analysis?.description).toContain('Simulated')

    const comment = await addComment(created.id, 'The problem is worse near the gate.')
    expect(comment.authorRole).toBe('CITIZEN')
    expect((await getMe())?.role).toBe('CITIZEN')

    await reopenComplaint(created.id, 'The repair was incomplete and the slab is still loose.')
    expect((await listMyComplaints()).some((item) => item.id === created.id)).toBe(true)

    await submitFeedback(created.id, {
      rating: 4,
      comment: 'Demo feedback saved locally',
      resolutionConfirmed: true,
    })
    expect(readDemoState().complaints.find((item) => item.id === created.id)?.feedback?.rating).toBe(4)

    const notifications = await listNotifications()
    expect(notifications.length).toBeGreaterThan(0)
    if (notifications.length > 1) {
      await markNotificationRead(notifications[1]!.id)
    }
    await markAllNotificationsRead()
    expect(await getUnreadNotificationCount()).toBe(0)
  })

  it('can reopen a seeded resolved complaint', async () => {
    await signIn('citizen')
    const resolved = readDemoState().complaints.find(
      (item) => item.ownerId === 'user-citizen-1' && item.status === 'RESOLVED',
    )
    expect(resolved).toBeDefined()
    await reopenComplaint(resolved!.id, 'The pothole returned after the rain and needs another inspection.')
    expect(readDemoState().complaints.find((item) => item.id === resolved!.id)?.status).toBe('REOPENED')
  })
})

describe('demo department workflow', () => {
  it('works the queue, records evidence and resolution, and requests reassignment', async () => {
    await signIn('department')
    const queue = await listDepartmentComplaints({ page: 0, size: 20 })
    expect(queue.totalElements).toBeGreaterThan(0)
    const target = queue.content.find((item) => item.status !== 'CLOSED') ?? queue.content[0]!
    const id = target.id

    await acceptDepartmentComplaint(id)
    await updateDepartmentComplaintStatus(id, {
      status: 'IN_PROGRESS',
      reason: 'Repair crew has been assigned in demo mode.',
    })
    await requestDepartmentComplaintInfo(id, 'Please share a photo of the exact location.')
    const note = await addDepartmentNote(id, 'Material arranged for the next working day.')
    expect(note.body).toContain('Material arranged')

    const presigned = await presignUpload({
      fileName: 'repair-proof.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 2048,
      imageKind: 'EVIDENCE',
    })
    const file = new File(['demo'], 'repair-proof.jpg', { type: 'image/jpeg' })
    await putObject(presigned.uploadUrl, file, presigned.contentType)
    await confirmUpload(presigned.imageId)
    const attached = await attachDepartmentEvidence(id, presigned.imageId)
    expect(attached.status).toBe('ATTACHED')

    await resolveDepartmentComplaint(id, 'Footpath slab replaced and area made safe for pedestrians.')
    expect((await getDepartmentComplaint(id)).status).toBe('RESOLVED')

    await requestDepartmentReassignment(id, {
      toDepartmentId: 'dept-water',
      reason: 'Drainage work is required before the road surface can be restored.',
    })
    expect(
      readDemoState().reassignmentRequests.some((request) => request.complaintId === id),
    ).toBe(true)
  })
})

describe('demo super-admin workflow', () => {
  it('governs analytics, AI overrides, routing, departments, users and audit', async () => {
    await signIn('admin')
    const overview = await getAdminOverview()
    expect(overview.stats.total).toBeGreaterThanOrEqual(15)
    expect(overview.stats.failedAi).toBeGreaterThanOrEqual(0)
    const queue = await getAdminComplaintQueue({ page: 0, size: 25 })
    expect(queue.totalElements).toBeGreaterThanOrEqual(15)

    const complaint = readDemoState().complaints[0]!
    const classification = await overrideAdminClassification(complaint.id, {
      categoryCode: 'WATER',
      subcategoryCode: 'LEAK',
      severity: 'HIGH',
      reason: 'Demo administrator reviewed the classification manually.',
    })
    expect(classification.outcome).toBeTruthy()
    expect((await getAdminComplaint(complaint.id)).categoryCode).toBe('WATER')

    const reroute = await rerouteAdminComplaint(complaint.id, {
      reason: 'Demo routing override after an ambiguous automatic decision.',
    })
    expect(reroute.id).toBeTruthy()

    await assignAdminComplaint(complaint.id, {
      departmentId: 'dept-water',
      reason: 'Manually assigned for demonstration purposes.',
    })
    expect((await getAdminComplaint(complaint.id)).departmentId).toBe('dept-water')
    await unassignAdminComplaint(complaint.id, {
      reason: 'Returned to the unassigned queue for demonstration purposes.',
    })
    expect((await getAdminComplaint(complaint.id)).departmentId).toBeNull()

    const request = readDemoState().reassignmentRequests[0]!
    await reviewAdminReassignment(request.id, {
      decision: 'APPROVED',
      reason: 'Demo approval of the department reassignment request.',
    })
    expect(readDemoState().reassignmentRequests.find((item) => item.id === request.id)?.status).toBe(
      'APPROVED',
    )

    await updateAdminDepartmentStatus('dept-parks', {
      status: 'VERIFIED',
      reason: 'Demo verification of the parks department.',
    })
    expect(readDemoState().departments.find((item) => item.id === 'dept-parks')?.verificationStatus).toBe(
      'VERIFIED',
    )

    const user = readDemoState().users.find((item) => item.email === 'priya@example.com')!
    await updateAdminUserStatus(user.id, {
      status: 'SUSPENDED',
      reason: 'Demo suspension to show user governance.',
    })
    await updateAdminUserRole(user.id, {
      role: 'DEPARTMENT_ADMIN',
      reason: 'Demo role change to show user governance.',
    })
    const updated = readDemoState().users.find((item) => item.id === user.id)!
    expect(updated.accountStatus).toBe('SUSPENDED')
    expect(updated.role).toBe('DEPARTMENT_ADMIN')

    const audit = await listAdminAuditLogs({ page: 0, size: 100 })
    expect(audit.content.some((entry) => entry.action === 'CLASSIFICATION_OVERRIDE')).toBe(true)
    expect(audit.content.some((entry) => entry.action === 'USER_ROLE_CHANGED')).toBe(true)
  })
})

describe('demo transport guarantees', () => {
  it('uses the local adapter and never calls fetch or XHR for uploads', async () => {
    const fetchSpy = vi.fn()
    vi.stubGlobal('fetch', fetchSpy)
    const openSpy = vi.spyOn(XMLHttpRequest.prototype, 'open')
    class FakeImage {
      width = 120
      height = 80
      onload: (() => void) | null = null
      onerror: (() => void) | null = null
      set src(_value: string) {
        this.onload?.()
      }
    }
    vi.stubGlobal('Image', FakeImage)

    await signIn('citizen')
    await getMe()
    await listMyComplaints()
    await submitComplaint({
      description: 'Pothole reported entirely through the local demo adapter',
      latitude: 23.25,
      longitude: 77.41,
      locationSource: 'MANUAL',
    })
    const presigned = await presignUpload({
      fileName: 'pothole.jpg',
      contentType: 'image/jpeg',
      sizeBytes: 1024,
      imageKind: 'CITIZEN',
    })
    await putObject(
      presigned.uploadUrl,
      new File(['demo'], 'pothole.jpg', { type: 'image/jpeg' }),
      presigned.contentType,
    )
    await confirmUpload(presigned.imageId)

    expect(fetchSpy).not.toHaveBeenCalled()
    expect(openSpy).not.toHaveBeenCalled()
  })

  it('resets all local demo data and clears the session', async () => {
    await signIn('citizen')
    const created = await submitComplaint({
      description: 'Large pothole in front of the municipal office',
      latitude: 23.25,
      longitude: 77.41,
      locationSource: 'MANUAL',
    })
    expect(readDemoState().complaints.some((item) => item.id === created.id)).toBe(true)

    resetDemoState()
    clearDemoSession()
    setAccessToken(null)
    expect(readDemoState().complaints.some((item) => item.id === created.id)).toBe(false)
    expect(localStorage.getItem(DEMO_SESSION_KEY)).toBeNull()
    await expectApiError(getMe(), 403)
  })
})

describe('demo login UI', () => {
  it('shows all three one-click demo account options on the sign-in page', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/login']}>
          <AuthProvider>
            <LoginPage />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    const panel = await screen.findByRole('region', { name: /demo accounts/i })
    expect(panel).toBeInTheDocument()
    for (const account of DEMO_ACCOUNTS) {
      expect(within(panel).getByRole('button', { name: new RegExp(`Sign in as ${account.label}`, 'i') })).toBeInTheDocument()
      expect(within(panel).getByText(new RegExp(account.email, 'i'))).toBeInTheDocument()
    }
    await waitFor(() => expect(screen.queryByText('Loading CivicSense…')).not.toBeInTheDocument())
  })

  it('logs in from a demo account card', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/login']}>
          <AuthProvider>
            <LoginPage />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    const adminButton = await screen.findByRole('button', { name: /Sign in as Super admin/i })
    await user.click(adminButton)
    await waitFor(() => {
      expect(JSON.parse(localStorage.getItem(DEMO_SESSION_KEY) ?? '{}').role).toBe('SUPER_ADMIN')
    })
  })
})

describe('demo controls', () => {
  it('switches accounts and resets local data from the sidebar controls', async () => {
    await signIn('citizen')
    const user = userEvent.setup()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const originalLocation = window.location
    const assignSpy = vi.fn()
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { ...originalLocation, assign: assignSpy },
    })

    try {
      const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

      render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/dashboard']}>
            <AuthProvider>
              <DemoAccountSwitcher />
              <DemoResetButton />
            </AuthProvider>
          </MemoryRouter>
        </QueryClientProvider>,
      )

      const select = await screen.findByLabelText('Switch demo account')
      await user.selectOptions(select, DEMO_CREDENTIALS.admin.email)
      await waitFor(() => {
        expect(JSON.parse(localStorage.getItem(DEMO_SESSION_KEY) ?? '{}').role).toBe('SUPER_ADMIN')
      })

      await user.click(screen.getByRole('button', { name: /RESET DEMO DATA/i }))
      await waitFor(() => expect(localStorage.getItem(DEMO_SESSION_KEY)).toBeNull())
      expect(confirmSpy).toHaveBeenCalled()
      expect(assignSpy).toHaveBeenCalledWith('/login')
      expect(readDemoState().notifications.length).toBeGreaterThanOrEqual(10)
    } finally {
      Object.defineProperty(window, 'location', {
        configurable: true,
        value: originalLocation,
      })
    }
  })
})
