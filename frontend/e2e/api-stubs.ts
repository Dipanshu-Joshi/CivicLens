import type { Page, Route } from '@playwright/test'

export type StubRole = 'CITIZEN' | 'DEPARTMENT_ADMIN' | 'SUPER_ADMIN'

export interface StubState {
  forgotEmails: string[]
  resetRequests: Array<{ token: string; password: string }>
  infoReplies: Array<{ complaintId: string; body: string }>
}

interface StubOptions {
  role?: StubRole
  authenticated?: boolean
  genericMessage?: string
}

const CORS_HEADERS = {
  'access-control-allow-origin': '*',
  'access-control-allow-credentials': 'true',
  'access-control-allow-methods': 'GET,POST,PATCH,DELETE,OPTIONS',
  'access-control-allow-headers': 'content-type,authorization',
}

function user(role: StubRole) {
  const names: Record<StubRole, { email: string; fullName: string; departmentId: string | null }> = {
    CITIZEN: { email: 'citizen@example.com', fullName: 'Asha Citizen', departmentId: null },
    DEPARTMENT_ADMIN: {
      email: 'department@example.com',
      fullName: 'Ravi Department',
      departmentId: 'dept-1',
    },
    SUPER_ADMIN: { email: 'admin@example.com', fullName: 'Meera Admin', departmentId: null },
  }
  return {
    id: 'user-1',
    email: names[role].email,
    fullName: names[role].fullName,
    role,
    accountStatus: 'ACTIVE',
    verificationStatus: role === 'DEPARTMENT_ADMIN' ? 'VERIFIED' : null,
    departmentId: names[role].departmentId,
    permissions: [],
  }
}

function tokenResponse(role: StubRole) {
  return {
    accessToken: `stub-access-${role.toLowerCase()}`,
    tokenType: 'Bearer',
    expiresIn: 900,
    user: user(role),
  }
}

function notifications(complaintId: string) {
  return [
    {
      id: 'notification-1',
      type: 'INFO_REQUESTED',
      title: 'More information requested',
      body: 'Public Works asked for one more detail about the pothole.',
      channel: 'IN_APP',
      deliveryStatus: 'SENT',
      complaintNumber: 'CIV-2026-0101',
      readAt: null,
      createdAt: '2026-11-21T09:00:00Z',
      sentAt: '2026-11-21T09:00:00Z',
      complaintId,
    },
  ]
}

function complaintDetail(complaintId: string) {
  return {
    id: complaintId,
    complaintNumber: 'CIV-2026-0101',
    description: 'A deep pothole is blocking the cycle lane near the school.',
    additionalInfo: null,
    subcategoryCode: null,
    addressLine: '12 Market Road',
    state: 'Delhi',
    postalCode: '110001',
    country: 'India',
    ward: 'Ward 7',
    city: 'New Delhi',
    zone: 'Central',
    locationSource: 'GPS',
    latitude: 28.6139,
    longitude: 77.209,
    departmentId: 'dept-1',
    departmentName: 'Public Works',
    assignedAt: '2026-11-20T10:00:00Z',
    resolvedAt: null,
    resolutionDueAt: '2026-11-27T10:00:00Z',
    status: 'ASSIGNED',
    severity: 'HIGH',
    categoryCode: 'ROADS',
    createdAt: '2026-11-19T08:30:00Z',
    timeline: [
      {
        fromStatus: null,
        toStatus: 'SUBMITTED',
        actorRole: 'CITIZEN',
        reason: null,
        createdAt: '2026-11-19T08:30:00Z',
      },
    ],
    comments: [
      {
        id: 'comment-1',
        authorName: 'Public Works',
        authorRole: 'DEPARTMENT_ADMIN',
        body: 'Can you confirm whether the pothole is still present this morning?',
        createdAt: '2026-11-21T09:00:00Z',
      },
    ],
    assignments: [
      {
        departmentId: 'dept-1',
        departmentName: 'Public Works',
        assignmentType: 'RULE',
        active: true,
        assignedAt: '2026-11-20T10:00:00Z',
        unassignedAt: null,
      },
    ],
    feedback: null,
    images: [],
    analysis: null,
  }
}

async function json(route: Route, status: number, body: unknown) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    headers: CORS_HEADERS,
    body: JSON.stringify(body),
  })
}

/**
 * Installs deterministic API responses for browser-level workflow checks.
 * State is local to the test page; no hosted service or real provider is used.
 */
export async function installApiStubs(page: Page, options: StubOptions = {}): Promise<StubState> {
  const role = options.role ?? 'CITIZEN'
  const genericMessage =
    options.genericMessage ?? 'If the account exists, a password reset link has been sent.'
  const complaintId = 'complaint-1'
  let signedIn = options.authenticated ?? false
  const state: StubState = {
    forgotEmails: [],
    resetRequests: [],
    infoReplies: [],
  }

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const method = request.method()
    const path = new URL(request.url()).pathname.replace(/^\/api\/v1/, '')

    if (method === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: CORS_HEADERS })
      return
    }

    if (path === '/auth/me' && method === 'GET') {
      if (!signedIn) {
        await json(route, 401, { title: 'Unauthorized', status: 401 })
        return
      }
      await json(route, 200, user(role))
      return
    }

    if (path === '/auth/refresh' && method === 'POST') {
      if (!signedIn) {
        await json(route, 401, { title: 'Unauthorized', status: 401 })
        return
      }
      await json(route, 200, tokenResponse(role))
      return
    }

    if (path === '/auth/login' && method === 'POST') {
      signedIn = true
      await json(route, 200, tokenResponse(role))
      return
    }

    if (path === '/auth/logout' && method === 'POST') {
      signedIn = false
      await json(route, 200, {})
      return
    }

    if (path === '/auth/forgot-password' && method === 'POST') {
      const body = request.postDataJSON() as { email?: string }
      state.forgotEmails.push(body.email ?? '')
      await json(route, 200, { message: genericMessage })
      return
    }

    if (path === '/auth/reset-password' && method === 'POST') {
      const body = request.postDataJSON() as { token?: string; password?: string }
      state.resetRequests.push({ token: body.token ?? '', password: body.password ?? '' })
      await json(route, 200, {})
      return
    }

    if (path === '/notifications' && method === 'GET') {
      await json(route, 200, notifications(complaintId))
      return
    }

    if (path === '/notifications/unread-count' && method === 'GET') {
      await json(route, 200, { count: 1 })
      return
    }

    if (path.startsWith('/notifications/') && (method === 'PATCH' || method === 'POST')) {
      await json(route, 200, {})
      return
    }

    if (path === `/citizen/complaints/${complaintId}/respond-info` && method === 'POST') {
      const body = request.postDataJSON() as { body?: string }
      state.infoReplies.push({ complaintId, body: body.body ?? '' })
      await json(route, 200, {})
      return
    }

    if (path === `/citizen/complaints/${complaintId}` && method === 'GET') {
      await json(route, 200, complaintDetail(complaintId))
      return
    }

    if (path === '/citizen/complaints' && method === 'GET') {
      await json(route, 200, [complaintDetail(complaintId)])
      return
    }

    if (/^\/department\/complaints\/[^/]+$/.test(path) && method === 'GET') {
      await json(route, 200, complaintDetail(path.split('/').pop() ?? complaintId))
      return
    }

    if (/^\/admin\/complaints\/[^/]+$/.test(path) && method === 'GET') {
      await json(route, 200, complaintDetail(path.split('/').pop() ?? complaintId))
      return
    }

    await json(route, 200, [])
  })

  return state
}
