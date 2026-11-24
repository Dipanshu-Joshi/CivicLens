import {
  AxiosError,
  type AxiosAdapter,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import type {
  LoginResponse,
  MeResponse,
  Role,
  VerificationStatus,
} from '../lib/api/client'
import type {
  AiAnalysisResponse,
  ComplaintStatus,
  FeedbackPayload,
  Severity,
  SubmitComplaintPayload,
} from '../lib/api/citizen'
import type {
  AdminAuditEntry,
  AdminDepartmentStatus,
  AdminRole,
  AdminUserStatus,
  AiAnalysisEntry,
  ClassificationOverridePayload,
  CreateCategoryPayload,
  CreateJurisdictionPayload,
  ReassignmentReviewPayload,
  UpdateCategoryPayload,
} from '../lib/api/admin'
import type {
  CapabilityUpdate,
  ComplaintStatusUpdate,
  DepartmentProfileUpdate,
  ServiceAreaCreate,
  ServiceAreaUpdate,
  TeamMemberUpdate,
} from '../lib/api/department'
import type { PresignUploadRequest } from '../lib/api/uploads'
import { DEMO_ENABLED } from './feature'
import {
  clearDemoSession,
  mutateDemoState,
  readDemoState,
} from './storage'
import { authenticateDemo, currentDemoUser } from './session'
import {
  addAudit,
  addComment,
  addNotification,
  addNote,
  addStatus,
  adminActivityFrom,
  adminOverview,
  categoryName,
  createComplaint,
  departmentAnalytics,
  filterAudit,
  filterComplaints,
  filterDepartments,
  filterUsers,
  nowIso,
  paginate,
  routingCandidates,
  toAdminComplaintSummary,
  toAdminDepartmentDetail,
  toAdminUser,
  toAnalysisEntry,
  toComplaintDetail,
  toComplaintPhoto,
  toComplaintSummary,
  toDepartmentComplaintDetail,
  toDepartmentProfile,
  toDepartmentServiceArea,
  toDepartmentSummary,
  toMe,
  toNotification,
  toRoutingDecision,
  toServiceArea,
  toTeamMember,
  toUploadedImage,
  toUserInfo,
  toViewableImage,
  uid,
} from './domain'
import type {
  DemoComplaint,
  DemoDepartment,
  DemoState,
  DemoUploadedImage,
  DemoUser,
} from './types'

function response<T>(data: T, config: InternalAxiosRequestConfig, status = 200): AxiosResponse<T> {
  return {
    data,
    status,
    statusText: status === 201 ? 'Created' : 'OK',
    headers: { 'content-type': 'application/json' },
    config,
  }
}

function fail(
  status: number,
  title: string,
  detail: string,
  config: InternalAxiosRequestConfig,
): Promise<never> {
  const axiosResponse = response({ title, status, detail }, config, status)
  return Promise.reject(
    new AxiosError(detail, undefined, config, undefined, axiosResponse),
  )
}

function requestBody<T>(config: InternalAxiosRequestConfig): T {
  const raw = config.data
  if (!raw) return {} as T
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw) as T
    } catch {
      return {} as T
    }
  }
  if (raw instanceof URLSearchParams) {
    return Object.fromEntries(raw.entries()) as T
  }
  return raw as T
}

function routePath(config: InternalAxiosRequestConfig): string {
  const raw = config.url ?? ''
  let pathname = raw
  if (/^https?:\/\//i.test(raw)) {
    try {
      pathname = new URL(raw).pathname
    } catch {
      pathname = raw
    }
  }
  return pathname.replace(/^\/api\/v1/, '').replace(/^\/+/, '')
}

function queryValue(config: InternalAxiosRequestConfig, name: string): string | undefined {
  try {
    const url = new URL(config.url ?? '', 'http://demo.local')
    const fromUrl = url.searchParams.get(name)
    if (fromUrl !== null) return fromUrl
  } catch {
    // Fall through to the axios params object.
  }
  const value = config.params?.[name]
  if (value === undefined || value === null || Array.isArray(value)) return undefined
  return String(value)
}

function queryValues(config: InternalAxiosRequestConfig, name: string): string[] {
  try {
    const url = new URL(config.url ?? '', 'http://demo.local')
    const values = url.searchParams.getAll(name)
    if (values.length) return values
  } catch {
    // Fall through to the axios params object.
  }
  const value = config.params?.[name]
  if (Array.isArray(value)) return value.map(String)
  return value === undefined || value === null ? [] : [String(value)]
}

function numeric(value: string | undefined, fallback: number): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function booleanParam(value: string | undefined): boolean | undefined {
  if (value === undefined) return undefined
  return value === 'true' || value === '1'
}

function httpError(status: number, message: string): Error & { status: number } {
  const error = new Error(message) as Error & { status: number }
  error.status = status
  return error
}

function requireUser(_config: InternalAxiosRequestConfig, roles?: Role[]): DemoUser {
  const user = currentDemoUser()
  if (!user) throw httpError(403, 'Sign in with a demo account to continue.')
  if (user.accountStatus !== 'ACTIVE') throw httpError(403, 'This demo account is not active.')
  if (roles && !roles.includes(user.role)) {
    throw httpError(403, 'This demo account cannot access that area.')
  }
  return user
}

function findComplaint(state: DemoState, id: string): DemoComplaint {
  const complaint = state.complaints.find((item) => item.id === id)
  if (!complaint) {
    const error = new Error('Complaint not found') as Error & { status: number }
    error.status = 404
    throw error
  }
  return complaint
}

function findDepartment(state: DemoState, id: string): DemoDepartment {
  const department = state.departments.find((item) => item.id === id)
  if (!department) {
    const error = new Error('Department not found') as Error & { status: number }
    error.status = 404
    throw error
  }
  return department
}

function readFileAsDataUrl(file: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(new Error('Could not read the selected image'))
    reader.readAsDataURL(file)
  })
}

async function createPreviewDataUrl(file: Blob): Promise<string> {
  // Keep the demo upload fully local and deterministic: a data URL stored in
  // browser state is enough for previews and cannot hang on image decoding.
  return readFileAsDataUrl(file)
}

export function demoImageIdFromUploadUrl(uploadUrl: string): string | null {
  const match = /^demo:\/\/upload\/([^/?#]+)/.exec(uploadUrl)
  return match?.[1] ?? null
}

export function isDemoUploadUrl(uploadUrl: string): boolean {
  return DEMO_ENABLED && demoImageIdFromUploadUrl(uploadUrl) !== null
}

export async function storeDemoUploadPreview(imageId: string, file: Blob): Promise<void> {
  if (!DEMO_ENABLED) return
  const preview = await createPreviewDataUrl(file)
  mutateDemoState((state) => {
    const image = state.images.find((item) => item.imageId === imageId)
    if (image) image.previewDataUrl = preview
  })
}

async function handleAuth(
  path: string,
  method: string,
  config: InternalAxiosRequestConfig,
): Promise<AxiosResponse<unknown> | null> {
  if (path === 'auth/login' && method === 'POST') {
    const { email, password } = requestBody<{ email: string; password: string }>(config)
    const session = authenticateDemo(email ?? '', password ?? '')
    const user = currentDemoUser()!
    const result: LoginResponse = {
      accessToken: session.token,
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: toUserInfo(user),
    }
    return response(result, config)
  }

  if (path === 'auth/refresh' && method === 'POST') {
    const session = currentDemoUser()
    if (!session) return fail(401, 'Unauthorized', 'No active demo session.', config)
    const result: LoginResponse = {
      accessToken: `demo.${session.id}.${Date.now().toString(36)}`,
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: toUserInfo(session),
    }
    return response(result, config)
  }

  if (path === 'auth/me' && method === 'GET') {
    const user = currentDemoUser()
    if (!user) return fail(403, 'Forbidden', 'No active demo session.', config)
    return response(toMe(user), config)
  }

  if (path === 'auth/logout' && method === 'POST') {
    clearDemoSession()
    return response({}, config)
  }

  if (path === 'auth/forgot-password' && method === 'POST') {
    return response(
      { message: 'If the account exists, a password reset link has been sent.' },
      config,
    )
  }

  if (path === 'auth/reset-password' && method === 'POST') {
    return response({}, config)
  }

  if (path === 'auth/profile' && method === 'PATCH') {
    const user = requireUser(config)
    const payload = requestBody<{ fullName?: string; phone?: string }>(config)
    return response(
      mutateDemoState((state) => {
        const record = state.users.find((item) => item.id === user.id)!
        if (payload.fullName?.trim()) record.fullName = payload.fullName.trim()
        if (payload.phone !== undefined) record.phone = payload.phone.trim() || null
        return toMe(record)
      }) as MeResponse,
      config,
    )
  }

  if (path === 'auth/register/citizen' && method === 'POST') {
    const payload = requestBody<{
      fullName: string
      email: string
      password: string
      phone?: string
    }>(config)
    return response(
      mutateDemoState((state) => {
        if (state.users.some((user) => user.email.toLowerCase() === payload.email?.toLowerCase())) {
          const error = new Error('An account with that email already exists') as Error & { status: number }
          error.status = 409
          throw error
        }
        state.users.push({
          id: uid('user'),
          email: payload.email,
          fullName: payload.fullName,
          password: payload.password,
          phone: payload.phone ?? null,
          role: 'CITIZEN',
          accountStatus: 'ACTIVE',
          verificationStatus: null,
          departmentId: null,
          permissions: ['complaint:create', 'complaint:read:own'],
          createdAt: nowIso(),
          adminLevel: 'OPERATOR',
          primaryAdmin: false,
          title: null,
        })
        return {}
      }),
      config,
      201,
    )
  }

  if (path === 'auth/register/department' && method === 'POST') {
    const payload = requestBody<{
      department: { name: string; departmentType: string; officialEmail: string; city?: string }
      administrator: { fullName: string; email: string; password: string }
    }>(config)
    return response(
      mutateDemoState((state) => {
        const departmentId = uid('dept')
        state.departments.push({
          id: departmentId,
          name: payload.department.name,
          departmentCode: `NEW-${String(state.counters.complaint).padStart(3, '0')}`,
          departmentType: payload.department.departmentType,
          description: null,
          officialEmail: payload.department.officialEmail,
          contactNumber: null,
          officeAddress: null,
          city: payload.department.city ?? 'Bhopal',
          state: 'Madhya Pradesh',
          country: 'India',
          verificationStatus: 'PENDING_VERIFICATION',
          isActive: true,
          acceptsComplaints: false,
          verifiedAt: null,
          autoAssignEnabled: false,
          routingPriority: 20,
          maxOpenComplaints: 20,
          createdAt: nowIso(),
          capabilities: [],
          serviceAreas: [],
        })
        state.users.push({
          id: uid('user'),
          email: payload.administrator.email,
          fullName: payload.administrator.fullName,
          password: payload.administrator.password,
          phone: null,
          role: 'DEPARTMENT_ADMIN',
          accountStatus: 'PENDING_VERIFICATION',
          verificationStatus: 'PENDING_VERIFICATION',
          departmentId,
          permissions: [],
          createdAt: nowIso(),
          adminLevel: 'OWNER',
          primaryAdmin: true,
          title: null,
        })
        return {
          departmentId,
          verificationStatus: 'PENDING_VERIFICATION' as VerificationStatus,
          message: 'Department application saved locally for admin review.',
        }
      }),
      config,
      201,
    )
  }

  return null
}

async function handleMeta(path: string, method: string, config: InternalAxiosRequestConfig): Promise<AxiosResponse<unknown> | null> {
  if (path === 'meta/health' && method === 'GET') {
    return response(
      {
        status: 'DEMO',
        version: 'demo-local',
        time: nowIso(),
        database: 'simulated',
        postgis: 'simulated',
        objectStorage: 'simulated',
      },
      config,
    )
  }
  if (path === 'meta/categories' && method === 'GET') {
    const includeInactive = queryValue(config, 'includeInactive') === 'true'
    const categories = readDemoState().categories
    return response(
      includeInactive ? categories : categories.filter((category) => category.active),
      config,
    )
  }
  return null
}

async function handleNotifications(path: string, method: string, config: InternalAxiosRequestConfig): Promise<AxiosResponse<unknown> | null> {
  const user = requireUser(config)
  if (path === 'notifications' && method === 'GET') {
    const notifications = readDemoState().notifications
      .filter((notification) => notification.userId === user.id)
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .map(toNotification)
    return response(notifications, config)
  }
  if (path === 'notifications/unread-count' && method === 'GET') {
    const count = readDemoState().notifications.filter(
      (notification) => notification.userId === user.id && notification.readAt === null,
    ).length
    return response({ count }, config)
  }
  if (path === 'notifications/read-all' && method === 'POST') {
    return response(
      mutateDemoState((state) => {
        state.notifications.forEach((notification) => {
          if (notification.userId === user.id && notification.readAt === null) {
            notification.readAt = nowIso()
            notification.deliveryStatus = 'READ'
          }
        })
        return {}
      }),
      config,
    )
  }
  const markRead = /^notifications\/([^/]+)\/read$/.exec(path)
  if (markRead && method === 'PATCH') {
    return response(
      mutateDemoState((state) => {
        const notification = state.notifications.find((item) => item.id === markRead[1] && item.userId === user.id)
        if (!notification) {
          const error = new Error('Notification not found') as Error & { status: number }
          error.status = 404
          throw error
        }
        notification.readAt = nowIso()
        notification.deliveryStatus = 'READ'
        return {}
      }),
      config,
    )
  }
  return null
}

async function handleCitizen(
  path: string,
  method: string,
  config: InternalAxiosRequestConfig,
  user: DemoUser,
): Promise<AxiosResponse<unknown> | null> {
  if (path === 'citizen/complaints' && method === 'POST') {
    const payload = requestBody<SubmitComplaintPayload>(config)
    const complaint = mutateDemoState((state) => createComplaint(state, user, payload))
    return response(toComplaintDetail(complaint) as unknown, config, 201)
  }

  if (path === 'citizen/complaints' && method === 'GET') {
    const complaints = readDemoState().complaints.filter((complaint) => complaint.ownerId === user.id)
    const statusValues = queryValues(config, 'status') as ComplaintStatus[]
    const filtered = filterComplaints(complaints, {
      status: statusValues,
      categoryCode: queryValue(config, 'categoryId'),
      search: queryValue(config, 'search'),
      from: queryValue(config, 'from'),
      to: queryValue(config, 'to'),
    })
    return response(filtered.map(toComplaintSummary), config)
  }

  const detailMatch = /^citizen\/complaints\/([^/]+)$/.exec(path)
  if (detailMatch && method === 'GET') {
    const complaint = readDemoState().complaints.find(
      (item) => item.id === detailMatch[1] && item.ownerId === user.id,
    )
    if (!complaint) return fail(404, 'Not found', 'Complaint not found.', config)
    return response(toComplaintDetail(complaint), config)
  }

  const subRoute = /^citizen\/complaints\/([^/]+)\/(timeline|comments|respond-info|reopen|feedback)$/.exec(path)
  if (subRoute) {
    const [, id, action] = subRoute
    if (action === 'timeline' && method === 'GET') {
      const complaint = readDemoState().complaints.find((item) => item.id === id && item.ownerId === user.id)
      if (!complaint) return fail(404, 'Not found', 'Complaint not found.', config)
      return response(complaint.timeline, config)
    }
    if (action === 'comments' && method === 'GET') {
      const complaint = readDemoState().complaints.find((item) => item.id === id && item.ownerId === user.id)
      if (!complaint) return fail(404, 'Not found', 'Complaint not found.', config)
      return response(complaint.comments, config)
    }
    if (action === 'comments' && method === 'POST') {
      const { body } = requestBody<{ body: string }>(config)
      return response(
        mutateDemoState((state) => {
          const complaint = findComplaint(state, id)
          if (complaint.ownerId !== user.id) {
            const error = new Error('Forbidden') as Error & { status: number }
            error.status = 403
            throw error
          }
          const comment = {
            id: uid('comment'),
            authorName: user.fullName,
            authorRole: user.role,
            body,
            createdAt: nowIso(),
          }
          complaint.comments.push(comment)
          return comment
        }),
        config,
        201,
      )
    }
    if (action === 'respond-info' && method === 'POST') {
      return response(
        mutateDemoState((state) => {
          const complaint = findComplaint(state, id)
          if (complaint.ownerId !== user.id) {
            const error = new Error('Forbidden') as Error & { status: number }
            error.status = 403
            throw error
          }
          addComment(complaint, user.fullName, user.role, requestBody<{ body: string }>(config).body)
          state.users
            .filter((member) => member.departmentId === complaint.departmentId && member.role === 'DEPARTMENT_ADMIN')
            .forEach((member) =>
              addNotification(state, member.id, 'INFO_RESPONSE', 'Citizen replied in demo mode', `${complaint.complaintNumber} has a new reply.`, complaint),
            )
          return {}
        }),
        config,
      )
    }
    if (action === 'reopen' && method === 'POST') {
      return response(
        mutateDemoState((state) => {
          const complaint = findComplaint(state, id)
          if (complaint.ownerId !== user.id) {
            const error = new Error('Forbidden') as Error & { status: number }
            error.status = 403
            throw error
          }
          addStatus(complaint, complaint.status, 'REOPENED', 'CITIZEN', requestBody<{ reason: string }>(config).reason)
          state.users
            .filter((member) => member.departmentId === complaint.departmentId && member.role === 'DEPARTMENT_ADMIN')
            .forEach((member) =>
              addNotification(state, member.id, 'REOPENED', 'Complaint reopened in demo mode', `${complaint.complaintNumber} was reopened by the citizen.`, complaint),
            )
          return {}
        }),
        config,
      )
    }
    if (action === 'feedback' && method === 'POST') {
      return response(
        mutateDemoState((state) => {
          const complaint = findComplaint(state, id)
          if (complaint.ownerId !== user.id) {
            const error = new Error('Forbidden') as Error & { status: number }
            error.status = 403
            throw error
          }
          const payload = requestBody<FeedbackPayload>(config)
          complaint.feedback = {
            rating: payload.rating,
            comment: payload.comment ?? null,
            resolutionConfirmed: payload.resolutionConfirmed,
            disputeReason: payload.disputeReason ?? null,
            createdAt: nowIso(),
          }
          if (!payload.resolutionConfirmed) {
            addStatus(complaint, complaint.status, 'REOPENED', 'CITIZEN', payload.disputeReason ?? 'Resolution disputed')
          }
          addAudit(state, user, 'FEEDBACK_SUBMITTED', 'COMPLAINT', complaint.id, payload.comment ?? payload.disputeReason ?? 'Demo feedback submitted')
          return {}
        }),
        config,
      )
    }
  }
  return null
}

async function handleUploads(path: string, method: string, config: InternalAxiosRequestConfig): Promise<AxiosResponse<unknown> | null> {
  requireUser(config)
  if (path === 'uploads/presign' && method === 'POST') {
    const payload = requestBody<PresignUploadRequest>(config)
    const imageId = uid('image')
    return response(
      mutateDemoState((state) => {
        const image: DemoUploadedImage = {
          imageId,
          storageKey: `demo/uploads/${imageId}`,
          contentType: payload.contentType,
          sizeBytes: payload.sizeBytes,
          imageKind: payload.imageKind,
          complaintId: null,
          createdAt: nowIso(),
          uploadStatus: 'PENDING',
          previewDataUrl: null,
        }
        state.images.push(image)
        return {
          imageId,
          uploadUrl: `demo://upload/${imageId}`,
          storageKey: image.storageKey,
          contentType: image.contentType,
          expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
          headers: {},
        }
      }),
      config,
      201,
    )
  }
  const confirm = /^uploads\/([^/]+)\/confirm$/.exec(path)
  if (confirm && method === 'POST') {
    return response(
      mutateDemoState((state) => {
        const image = state.images.find((item) => item.imageId === confirm[1])
        if (!image) {
          const error = new Error('Image not found') as Error & { status: number }
          error.status = 404
          throw error
        }
        image.uploadStatus = 'VERIFIED'
        return toUploadedImage(image)
      }),
      config,
    )
  }
  const url = /^uploads\/([^/]+)\/url$/.exec(path)
  if (url && method === 'GET') {
    const image = readDemoState().images.find((item) => item.imageId === url[1])
    if (!image) return fail(404, 'Not found', 'Image not found.', config)
    return response(toViewableImage(image), config)
  }
  const discard = /^uploads\/([^/]+)$/.exec(path)
  if (discard && method === 'DELETE') {
    return response(
      mutateDemoState((state) => {
        state.images = state.images.filter((image) => image.imageId !== discard[1])
        return {}
      }),
      config,
    )
  }
  return null
}

async function handleDepartment(
  path: string,
  method: string,
  config: InternalAxiosRequestConfig,
  user: DemoUser,
): Promise<AxiosResponse<unknown> | null> {
  if (!user.departmentId) return fail(403, 'Forbidden', 'No demo department is linked to this account.', config)
  const departmentId = user.departmentId

  if (path === 'department/me' && method === 'GET') {
    const department = findDepartment(readDemoState(), departmentId)
    return response(
      {
        departmentId: department.id,
        departmentName: department.name,
        departmentCode: department.departmentCode,
        verificationStatus: department.verificationStatus,
        acceptsComplaints: department.acceptsComplaints,
      },
      config,
    )
  }

  if (path === 'department/overview' && method === 'GET') {
    const state = readDemoState()
    const periodDays = numeric(queryValue(config, 'periodDays'), 30)
    const department = findDepartment(state, departmentId)
    return response(
      {
        context: {
          departmentId: department.id,
          departmentName: department.name,
          departmentCode: department.departmentCode,
          verificationStatus: department.verificationStatus,
          acceptsComplaints: department.acceptsComplaints,
        },
        analytics: departmentAnalytics(state, departmentId, periodDays),
        activity: adminActivityFrom(
          state.complaints.filter((complaint) => complaint.departmentId === departmentId).slice().sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()),
          20,
        ),
      },
      config,
    )
  }

  if (path === 'department/analytics' && method === 'GET') {
    return response(
      departmentAnalytics(readDemoState(), departmentId, numeric(queryValue(config, 'periodDays'), 30)),
      config,
    )
  }

  if (path === 'department/activity' && method === 'GET') {
    const complaints = readDemoState().complaints.filter((complaint) => complaint.departmentId === departmentId)
    return response(adminActivityFrom(complaints, numeric(queryValue(config, 'limit'), 20)), config)
  }

  if (path === 'department/complaints/queue' && method === 'GET') {
    const complaints = readDemoState().complaints.filter((complaint) => complaint.departmentId === departmentId)
    const statuses = queryValues(config, 'status') as ComplaintStatus[]
    const filtered = filterComplaints(complaints, {
      status: statuses,
      categoryCode: queryValue(config, 'categoryCode'),
      ward: queryValue(config, 'ward'),
      zone: queryValue(config, 'zone'),
      severity: queryValue(config, 'severity') as Severity | undefined,
      search: queryValue(config, 'search'),
      overdue: booleanParam(queryValue(config, 'overdue')),
      from: queryValue(config, 'from'),
      to: queryValue(config, 'to'),
      sort: queryValue(config, 'sort'),
    })
    return response(
      paginate(filtered.map(toComplaintSummary), numeric(queryValue(config, 'page'), 0), numeric(queryValue(config, 'size'), 20)),
      config,
    )
  }

  const detailMatch = /^department\/complaints\/([^/]+)$/.exec(path)
  if (detailMatch && method === 'GET') {
    const complaint = readDemoState().complaints.find(
      (item) => item.id === detailMatch[1] && item.departmentId === departmentId,
    )
    if (!complaint) return fail(404, 'Not found', 'Complaint not found in this department queue.', config)
    return response(toDepartmentComplaintDetail(complaint), config)
  }

  const actionMatch = /^department\/complaints\/([^/]+)\/(accept|reject|status|request-info|notes|evidence|resolve|reassign-request)$/.exec(path)
  if (actionMatch) {
    const [, id, action] = actionMatch
    if (action === 'notes' && method === 'GET') {
      const complaint = readDemoState().complaints.find((item) => item.id === id && item.departmentId === departmentId)
      if (!complaint) return fail(404, 'Not found', 'Complaint not found.', config)
      return response(complaint.internalNotes, config)
    }
    return response(
      mutateDemoState((state) => {
        const complaint = findComplaint(state, id)
        if (complaint.departmentId !== departmentId) {
          const error = new Error('Forbidden') as Error & { status: number }
          error.status = 403
          throw error
        }
        switch (action) {
          case 'accept': {
            addStatus(complaint, complaint.status, 'ACKNOWLEDGED', 'DEPARTMENT_ADMIN', 'Accepted in demo mode')
            addNotification(state, complaint.ownerId, 'STATUS_CHANGED', 'Report accepted', `${complaint.complaintNumber} was accepted by ${findDepartment(state, departmentId).name}.`, complaint)
            addAudit(state, user, 'COMPLAINT_ACCEPTED', 'COMPLAINT', complaint.id, 'Accepted in demo mode')
            return {}
          }
          case 'reject': {
            const reason = requestBody<{ reason: string }>(config).reason
            addStatus(complaint, complaint.status, 'REJECTED', 'DEPARTMENT_ADMIN', reason)
            addNotification(state, complaint.ownerId, 'STATUS_CHANGED', 'Report rejected', `${complaint.complaintNumber} was rejected by the department.`, complaint)
            addAudit(state, user, 'COMPLAINT_REJECTED', 'COMPLAINT', complaint.id, reason)
            return {}
          }
          case 'status': {
            const payload = requestBody<ComplaintStatusUpdate>(config)
            addStatus(complaint, complaint.status, payload.status, 'DEPARTMENT_ADMIN', payload.reason ?? 'Status updated in demo mode')
            addNotification(state, complaint.ownerId, 'STATUS_CHANGED', 'Report status updated', `${complaint.complaintNumber} is now ${payload.status}.`, complaint)
            addAudit(state, user, 'STATUS_CHANGED', 'COMPLAINT', complaint.id, payload.reason ?? 'Status updated in demo mode')
            return {}
          }
          case 'request-info': {
            const body = requestBody<{ body: string }>(config).body
            addComment(complaint, findDepartment(state, departmentId).name, 'DEPARTMENT_ADMIN', body)
            addNotification(state, complaint.ownerId, 'INFO_REQUESTED', 'Information requested', `${complaint.complaintNumber} has a new information request.`, complaint)
            return {}
          }
          case 'notes':
            return addNote(complaint, user.fullName, requestBody<{ body: string }>(config).body)
          case 'evidence': {
            const imageId = requestBody<{ imageId: string }>(config).imageId
            const image = state.images.find((item) => item.imageId === imageId)
            if (!image) {
              const error = new Error('Image not found') as Error & { status: number }
              error.status = 404
              throw error
            }
            image.complaintId = complaint.id
            image.uploadStatus = 'VERIFIED'
            if (!complaint.images.some((photo) => photo.id === imageId)) {
              complaint.images.push(toComplaintPhoto(image))
            }
            addAudit(state, user, 'EVIDENCE_ATTACHED', 'COMPLAINT', complaint.id, 'Resolution evidence attached in demo mode')
            return { status: 'ATTACHED' as const, imageId }
          }
          case 'resolve': {
            const summary = requestBody<{ summary: string }>(config).summary
            complaint.resolutionSummary = summary
            addStatus(complaint, complaint.status, 'RESOLVED', 'DEPARTMENT_ADMIN', summary)
            addNotification(state, complaint.ownerId, 'RESOLVED', 'Report resolved', `${complaint.complaintNumber} was resolved in demo mode.`, complaint)
            addAudit(state, user, 'COMPLAINT_RESOLVED', 'COMPLAINT', complaint.id, summary)
            return {}
          }
          case 'reassign-request': {
            const payload = requestBody<{ toDepartmentId: string; reason: string }>(config)
            state.reassignmentRequests.unshift({
              id: uid('reassign'),
              complaintId: complaint.id,
              fromDepartmentId: departmentId,
              toDepartmentId: payload.toDepartmentId,
              status: 'PENDING',
              reason: payload.reason,
              createdAt: nowIso(),
            })
            state.users
              .filter((member) => member.role === 'SUPER_ADMIN')
              .forEach((member) =>
                addNotification(state, member.id, 'REASSIGNMENT_REQUESTED', 'Reassignment requested', `${complaint.complaintNumber} needs a routing review.`, complaint),
              )
            addAudit(state, user, 'REASSIGNMENT_REQUESTED', 'COMPLAINT', complaint.id, payload.reason)
            return {}
          }
          default:
            return {}
        }
      }),
      config,
      action === 'notes' || action === 'evidence' ? 201 : 200,
    )
  }

  if (path === 'department/profile' && method === 'GET') {
    return response(toDepartmentProfile(findDepartment(readDemoState(), departmentId)), config)
  }
  if (path === 'department/profile' && method === 'PATCH') {
    const payload = requestBody<DepartmentProfileUpdate>(config)
    return response(
      mutateDemoState((state) => {
        const department = findDepartment(state, departmentId)
        if (payload.description !== undefined) department.description = payload.description
        if (payload.contactNumber !== undefined) department.contactNumber = payload.contactNumber
        if (payload.officeAddress !== undefined) department.officeAddress = payload.officeAddress
        department.acceptsComplaints = payload.acceptsComplaints
        department.autoAssignEnabled = payload.autoAssignEnabled
        addAudit(state, user, 'DEPARTMENT_PROFILE_UPDATED', 'DEPARTMENT', departmentId, 'Profile settings updated in demo mode')
        return toDepartmentProfile(department)
      }),
      config,
    )
  }
  if (path === 'department/capabilities' && method === 'GET') {
    return response(findDepartment(readDemoState(), departmentId).capabilities, config)
  }
  if (path === 'department/capabilities' && method === 'PUT') {
    const payload = requestBody<{ capabilities: CapabilityUpdate[] }>(config)
    return response(
      mutateDemoState((state) => {
        const department = findDepartment(state, departmentId)
        const flat = state.categories.flatMap((category) => [category, ...category.children])
        department.capabilities = payload.capabilities.map((item) => {
          const category = flat.find((entry) => entry.id === item.categoryId)
          return {
            categoryId: item.categoryId,
            categoryCode: category?.code ?? 'UNKNOWN',
            categoryName: category?.name ?? 'Unknown',
            primary: item.primary,
            priority: item.priority,
          }
        })
        addAudit(state, user, 'CAPABILITIES_UPDATED', 'DEPARTMENT', departmentId, 'Category capabilities updated in demo mode')
        return department.capabilities
      }),
      config,
    )
  }
  if (path === 'department/team' && method === 'GET') {
    return response(
      readDemoState().users.filter((member) => member.departmentId === departmentId).map(toTeamMember),
      config,
    )
  }
  const teamMatch = /^department\/team\/([^/]+)$/.exec(path)
  if (teamMatch && method === 'PATCH') {
    const payload = requestBody<TeamMemberUpdate>(config)
    return response(
      mutateDemoState((state) => {
        const member = state.users.find((item) => item.id === teamMatch[1] && item.departmentId === departmentId)
        if (!member) {
          const error = new Error('Team member not found') as Error & { status: number }
          error.status = 404
          throw error
        }
        member.adminLevel = payload.adminLevel
        member.primaryAdmin = payload.primaryAdmin
        return state.users.filter((item) => item.departmentId === departmentId).map(toTeamMember)
      }),
      config,
    )
  }
  if (path === 'department/service-areas' && method === 'GET') {
    return response(
      findDepartment(readDemoState(), departmentId).serviceAreas.map(toDepartmentServiceArea),
      config,
    )
  }
  if (path === 'department/service-areas' && method === 'POST') {
    const payload = requestBody<ServiceAreaCreate>(config)
    return response(
      mutateDemoState((state) => {
        const department = findDepartment(state, departmentId)
        const area = {
          id: uid('area'),
          departmentId,
          name: payload.name,
          coversWard: payload.coversWard ?? null,
          coversZone: payload.coversZone ?? null,
          jurisdictionCode: 'BHOPAL-CITY',
          active: true,
          createdAt: nowIso(),
        }
        department.serviceAreas.push(area)
        return toDepartmentServiceArea(area)
      }),
      config,
      201,
    )
  }
  const areaMatch = /^department\/service-areas\/([^/]+)$/.exec(path)
  if (areaMatch && method === 'PATCH') {
    const payload = requestBody<ServiceAreaUpdate>(config)
    return response(
      mutateDemoState((state) => {
        const department = findDepartment(state, departmentId)
        const area = department.serviceAreas.find((item) => item.id === areaMatch[1])
        if (!area) {
          const error = new Error('Service area not found') as Error & { status: number }
          error.status = 404
          throw error
        }
        area.name = payload.name
        area.coversWard = payload.coversWard ?? null
        area.coversZone = payload.coversZone ?? null
        return toDepartmentServiceArea(area)
      }),
      config,
    )
  }
  if (areaMatch && method === 'DELETE') {
    return response(
      mutateDemoState((state) => {
        const department = findDepartment(state, departmentId)
        department.serviceAreas = department.serviceAreas.filter((area) => area.id !== areaMatch[1])
        return {}
      }),
      config,
    )
  }

  return null
}

async function handleAdmin(
  path: string,
  method: string,
  config: InternalAxiosRequestConfig,
  user: DemoUser,
): Promise<AxiosResponse<unknown> | null> {
  if (path === 'admin/overview' && method === 'GET') {
    return response(adminOverview(readDemoState(), numeric(queryValue(config, 'periodDays'), 30)), config)
  }
  if (path === 'admin/analytics' && method === 'GET') {
    const overview = adminOverview(readDemoState(), numeric(queryValue(config, 'periodDays'), 30))
    const { activity: _activity, audit: _audit, ...analytics } = overview
    void _activity
    void _audit
    return response(analytics, config)
  }
  if (path === 'admin/activity' && method === 'GET') {
    return response(adminActivityFrom(readDemoState().complaints, numeric(queryValue(config, 'limit'), 20)), config)
  }

  if (path === 'admin/departments' && method === 'GET') {
    const departments = filterDepartments(readDemoState().departments, {
      verificationStatus: queryValue(config, 'verificationStatus'),
      isActive: booleanParam(queryValue(config, 'isActive')),
      departmentType: queryValue(config, 'departmentType'),
      search: queryValue(config, 'search'),
    })
    return response(departments.map(toDepartmentSummary), config)
  }
  const departmentDetail = /^admin\/departments\/([^/]+)$/.exec(path)
  if (departmentDetail && method === 'GET') {
    return response(toAdminDepartmentDetail(findDepartment(readDemoState(), departmentDetail[1])), config)
  }
  const departmentVerify = /^admin\/departments\/([^/]+)\/verify$/.exec(path)
  if (departmentVerify && method === 'POST') {
    const payload = requestBody<{ decision: 'VERIFIED' | 'REJECTED'; reason: string }>(config)
    return response(
      mutateDemoState((state) => {
        const department = findDepartment(state, departmentVerify[1])
        department.verificationStatus = payload.decision
        department.isActive = payload.decision === 'VERIFIED'
        department.acceptsComplaints = payload.decision === 'VERIFIED'
        department.verifiedAt = payload.decision === 'VERIFIED' ? nowIso() : null
        addAudit(state, user, `DEPARTMENT_${payload.decision}`, 'DEPARTMENT', department.id, payload.reason)
        return toDepartmentSummary(department)
      }),
      config,
    )
  }
  const departmentStatus = /^admin\/departments\/([^/]+)\/status$/.exec(path)
  if (departmentStatus && method === 'PATCH') {
    const payload = requestBody<{ status: AdminDepartmentStatus; reason: string }>(config)
    return response(
      mutateDemoState((state) => {
        const department = findDepartment(state, departmentStatus[1])
        department.verificationStatus = payload.status
        department.isActive = payload.status === 'VERIFIED'
        department.acceptsComplaints = payload.status === 'VERIFIED'
        department.verifiedAt = payload.status === 'VERIFIED' ? nowIso() : null
        addAudit(state, user, `DEPARTMENT_${payload.status}`, 'DEPARTMENT', department.id, payload.reason)
        return toAdminDepartmentDetail(department)
      }),
      config,
    )
  }
  const departmentOps = /^admin\/departments\/([^/]+)\/operations$/.exec(path)
  if (departmentOps && method === 'PATCH') {
    const payload = requestBody<{ routingPriority: number; maxOpenComplaints: number | null; autoAssignEnabled: boolean; reason: string }>(config)
    return response(
      mutateDemoState((state) => {
        const department = findDepartment(state, departmentOps[1])
        department.routingPriority = payload.routingPriority
        department.maxOpenComplaints = payload.maxOpenComplaints
        department.autoAssignEnabled = payload.autoAssignEnabled
        addAudit(state, user, 'DEPARTMENT_OPERATIONS_UPDATED', 'DEPARTMENT', department.id, payload.reason)
        return toAdminDepartmentDetail(department)
      }),
      config,
    )
  }

  if (path === 'admin/users' && method === 'GET') {
    const users = filterUsers(readDemoState().users, {
      role: queryValue(config, 'role') as AdminRole | undefined,
      status: queryValue(config, 'status') as AdminUserStatus | undefined,
      departmentId: queryValue(config, 'departmentId'),
      search: queryValue(config, 'search'),
    })
    return response(
      paginate(users.map((item) => toAdminUser(item, readDemoState())), numeric(queryValue(config, 'page'), 0), numeric(queryValue(config, 'size'), 20)),
      config,
    )
  }
  const userStatus = /^admin\/users\/([^/]+)\/status$/.exec(path)
  if (userStatus && method === 'PATCH') {
    const payload = requestBody<{ status: Extract<AdminUserStatus, 'ACTIVE' | 'SUSPENDED' | 'DISABLED'>; reason: string }>(config)
    return response(
      mutateDemoState((state) => {
        if (userStatus[1] === user.id) {
          const error = new Error('You cannot change your own account in demo mode') as Error & { status: number }
          error.status = 400
          throw error
        }
        const target = state.users.find((item) => item.id === userStatus[1])
        if (!target) {
          const error = new Error('User not found') as Error & { status: number }
          error.status = 404
          throw error
        }
        target.accountStatus = payload.status
        if (payload.status !== 'ACTIVE' && target.role === 'DEPARTMENT_ADMIN') {
          target.verificationStatus = 'SUSPENDED'
        }
        addAudit(state, user, 'USER_STATUS_CHANGED', 'USER', target.id, payload.reason)
        return toAdminUser(target, state)
      }),
      config,
    )
  }
  const userRole = /^admin\/users\/([^/]+)\/role$/.exec(path)
  if (userRole && method === 'PATCH') {
    const payload = requestBody<{ role: AdminRole; reason: string }>(config)
    return response(
      mutateDemoState((state) => {
        if (userRole[1] === user.id) {
          const error = new Error('You cannot change your own role in demo mode') as Error & { status: number }
          error.status = 400
          throw error
        }
        const target = state.users.find((item) => item.id === userRole[1])
        if (!target) {
          const error = new Error('User not found') as Error & { status: number }
          error.status = 404
          throw error
        }
        target.role = payload.role
        if (payload.role !== 'DEPARTMENT_ADMIN') target.departmentId = null
        addAudit(state, user, 'USER_ROLE_CHANGED', 'USER', target.id, payload.reason)
        return toAdminUser(target, state)
      }),
      config,
    )
  }

  const complaintQuery = {
    status: queryValues(config, 'status') as ComplaintStatus[],
    categoryCode: queryValue(config, 'categoryCode'),
    ward: queryValue(config, 'ward'),
    zone: queryValue(config, 'zone'),
    severity: queryValue(config, 'severity') as Severity | undefined,
    departmentId: queryValue(config, 'departmentId'),
    routingOutcome: queryValue(config, 'routingOutcome'),
    aiStatus: queryValue(config, 'aiStatus'),
    search: queryValue(config, 'search'),
    overdue: booleanParam(queryValue(config, 'overdue')),
    from: queryValue(config, 'from'),
    to: queryValue(config, 'to'),
    sort: queryValue(config, 'sort'),
  }
  if (path === 'admin/complaints' && method === 'GET') {
    return response(
      filterComplaints(readDemoState().complaints, complaintQuery).map(toAdminComplaintSummary),
      config,
    )
  }
  if (path === 'admin/complaints/unassigned' && method === 'GET') {
    return response(
      filterComplaints(readDemoState().complaints, { ...complaintQuery, status: ['UNASSIGNED', 'PENDING_ASSIGNMENT'] }).map(toAdminComplaintSummary),
      config,
    )
  }
  if (path === 'admin/complaints/queue' && method === 'GET') {
    return response(
      paginate(
        filterComplaints(readDemoState().complaints, complaintQuery).map(toAdminComplaintSummary),
        numeric(queryValue(config, 'page'), 0),
        numeric(queryValue(config, 'size'), 20),
      ),
      config,
    )
  }
  const adminComplaint = /^admin\/complaints\/([^/]+)$/.exec(path)
  if (adminComplaint && method === 'GET') {
    const complaint = readDemoState().complaints.find((item) => item.id === adminComplaint[1])
    if (!complaint) return fail(404, 'Not found', 'Complaint not found.', config)
    return response(toComplaintDetail(complaint), config)
  }
  const routingDecisions = /^admin\/complaints\/([^/]+)\/routing-decisions$/.exec(path)
  if (routingDecisions && method === 'GET') {
    const complaint = readDemoState().complaints.find((item) => item.id === routingDecisions[1])
    if (!complaint) return fail(404, 'Not found', 'Complaint not found.', config)
    return response(complaint.routingDecisions, config)
  }
  const analyses = /^admin\/complaints\/([^/]+)\/analyses$/.exec(path)
  if (analyses && method === 'GET') {
    const complaint = readDemoState().complaints.find((item) => item.id === analyses[1])
    if (!complaint) return fail(404, 'Not found', 'Complaint not found.', config)
    return response({ analyses: complaint.aiHistory }, config)
  }
  const complaintAction = /^admin\/complaints\/([^/]+)\/(reroute|classification|assign|unassign)$/.exec(path)
  if (complaintAction) {
    const [, id, action] = complaintAction
    if (action === 'reroute' && method === 'POST') {
      const payload = requestBody<{ reason: string }>(config)
      return response(
        mutateDemoState((state) => {
          const complaint = findComplaint(state, id)
          complaint.routingDecisions.forEach((decision) => { decision.current = false })
          const eligible = routingCandidates(state, complaint.categoryCode, complaint.departmentId)
          const selected = eligible.find((candidate) => candidate.atCapacity !== true && candidate.departmentId && candidate.departmentId !== 'none')?.departmentId ?? null
          const decision = toRoutingDecision(
            uid('route'),
            complaint.categoryCode,
            complaint.analysis?.confidence ?? null,
            selected,
            selected ? 'ASSIGNED' : 'UNASSIGNED',
            payload.reason,
            eligible,
          )
          complaint.routingDecisions.push(decision)
          addAudit(state, user, 'ROUTING_OVERRIDE', 'COMPLAINT', complaint.id, payload.reason)
          return decision
        }),
        config,
      )
    }
    if (action === 'classification' && method === 'PATCH') {
      const payload = requestBody<ClassificationOverridePayload>(config)
      return response(
        mutateDemoState((state) => {
          const complaint = findComplaint(state, id)
          complaint.categoryCode = payload.categoryCode
          complaint.subcategoryCode = payload.subcategoryCode
          if (payload.severity) complaint.severity = payload.severity
          const analysis: AiAnalysisResponse = {
            id: uid('analysis'),
            status: 'SUCCESS',
            isCivicIssue: true,
            categoryCode: payload.categoryCode,
            subcategoryCode: payload.subcategoryCode,
            severity: payload.severity ?? complaint.severity,
            description: payload.description ?? 'Manual classification override in demo mode.',
            safetyRisk: complaint.severity === 'CRITICAL' || complaint.severity === 'HIGH',
            visibleDamage: 'Reviewed by an administrator',
            isBlurry: false,
            requiresAdditionalInfo: false,
            confidence: 1,
            recommendedDepartmentHint: `${categoryName(state, payload.categoryCode)} (advisory)`,
            requiresHumanReview: false,
            errorMessage: null,
            createdAt: nowIso(),
          }
          complaint.analysis = analysis
          const entry: AiAnalysisEntry = toAnalysisEntry(analysis, 'ADMIN_OVERRIDE', payload.reason, user.fullName)
          complaint.aiHistory.push(entry)
          complaint.routingDecisions.forEach((decision) => { decision.current = false })
          const decision = toRoutingDecision(
            uid('route'),
            payload.categoryCode,
            1,
            complaint.departmentId,
            'CLASSIFICATION_OVERRIDE',
            payload.reason,
            routingCandidates(state, payload.categoryCode, complaint.departmentId),
          )
          complaint.routingDecisions.push(decision)
          addAudit(state, user, 'CLASSIFICATION_OVERRIDE', 'COMPLAINT', complaint.id, payload.reason)
          return decision
        }),
        config,
      )
    }
    if (action === 'assign' && method === 'POST') {
      const payload = requestBody<{ departmentId: string; reason: string }>(config)
      return response(
        mutateDemoState((state) => {
          const complaint = findComplaint(state, id)
          const department = findDepartment(state, payload.departmentId)
          complaint.departmentId = department.id
          complaint.departmentName = department.name
          complaint.assignments.forEach((assignment) => { assignment.active = false; assignment.unassignedAt = nowIso() })
          complaint.assignments.push({
            departmentId: department.id,
            departmentName: department.name,
            assignmentType: 'MANUAL',
            active: true,
            assignedAt: nowIso(),
            unassignedAt: null,
          })
          addStatus(complaint, complaint.status, 'ASSIGNED', 'SUPER_ADMIN', payload.reason)
          complaint.routingDecisions.forEach((decision) => { decision.current = false })
          complaint.routingDecisions.push(
            toRoutingDecision(uid('route'), complaint.categoryCode, complaint.analysis?.confidence ?? null, department.id, 'MANUAL', payload.reason, routingCandidates(state, complaint.categoryCode, department.id)),
          )
          addNotification(state, complaint.ownerId, 'ASSIGNED', 'Report assigned', `${complaint.complaintNumber} was assigned to ${department.name} by an administrator.`, complaint)
          addAudit(state, user, 'MANUAL_ASSIGNMENT', 'COMPLAINT', complaint.id, payload.reason)
          return {}
        }),
        config,
      )
    }
    if (action === 'unassign' && method === 'POST') {
      const payload = requestBody<{ reason: string }>(config)
      return response(
        mutateDemoState((state) => {
          const complaint = findComplaint(state, id)
          complaint.departmentId = null
          complaint.departmentName = null
          complaint.assignments.forEach((assignment) => { assignment.active = false; assignment.unassignedAt = nowIso() })
          addStatus(complaint, complaint.status, 'UNASSIGNED', 'SUPER_ADMIN', payload.reason)
          addAudit(state, user, 'COMPLAINT_UNASSIGNED', 'COMPLAINT', complaint.id, payload.reason)
          return {}
        }),
        config,
      )
    }
  }

  if (path === 'admin/reassignment-requests' && method === 'GET') {
    return response(readDemoState().reassignmentRequests, config)
  }
  const review = /^admin\/reassignment-requests\/([^/]+)\/review$/.exec(path)
  if (review && method === 'POST') {
    const payload = requestBody<ReassignmentReviewPayload>(config)
    return response(
      mutateDemoState((state) => {
        const request = state.reassignmentRequests.find((item) => item.id === review[1])
        if (!request) {
          const error = new Error('Request not found') as Error & { status: number }
          error.status = 404
          throw error
        }
        request.status = payload.decision
        request.reviewReason = payload.reason
        request.reviewedAt = nowIso()
        if (payload.decision === 'APPROVED') {
          const complaint = findComplaint(state, request.complaintId)
          const department = findDepartment(state, request.toDepartmentId)
          complaint.departmentId = department.id
          complaint.departmentName = department.name
          complaint.assignments.forEach((assignment) => { assignment.active = false; assignment.unassignedAt = nowIso() })
          complaint.assignments.push({
            departmentId: department.id,
            departmentName: department.name,
            assignmentType: 'MANUAL',
            active: true,
            assignedAt: nowIso(),
            unassignedAt: null,
          })
          addStatus(complaint, complaint.status, 'ASSIGNED', 'SUPER_ADMIN', payload.reason)
          addNotification(state, complaint.ownerId, 'ASSIGNED', 'Report reassigned', `${complaint.complaintNumber} was reassigned to ${department.name}.`, complaint)
        }
        addAudit(state, user, 'REASSIGNMENT_REVIEWED', 'COMPLAINT', request.complaintId, `${payload.decision}: ${payload.reason}`)
        return {}
      }),
      config,
    )
  }

  if (path === 'admin/categories' && method === 'GET') {
    return response(
      queryValue(config, 'includeInactive') === 'false'
        ? readDemoState().categories.filter((category) => category.active)
        : readDemoState().categories,
      config,
    )
  }
  if (path === 'admin/reference/categories' && method === 'POST') {
    const payload = requestBody<CreateCategoryPayload>(config)
    return response(
      mutateDemoState((state) => {
        const category = {
          id: uid('cat'),
          code: payload.code.toUpperCase(),
          name: payload.name,
          description: payload.description ?? null,
          icon: payload.icon ?? null,
          color: payload.color ?? null,
          displayOrder: payload.displayOrder ?? state.categories.length + 1,
          active: true,
          children: [],
        }
        const parent = payload.parentId ? state.categories.flatMap((item) => [item, ...item.children]).find((item) => item.id === payload.parentId) : undefined
        if (parent) parent.children.push(category)
        else state.categories.push(category)
        addAudit(state, user, 'CATEGORY_CREATED', 'CATEGORY', category.id, `Created ${category.name} in demo mode`)
        return category
      }),
      config,
      201,
    )
  }
  const categoryUpdate = /^admin\/reference\/categories\/([^/]+)$/.exec(path)
  if (categoryUpdate && method === 'PATCH') {
    const payload = requestBody<UpdateCategoryPayload>(config)
    return response(
      mutateDemoState((state) => {
        const category = state.categories.flatMap((item) => [item, ...item.children]).find((item) => item.id === categoryUpdate[1])
        if (!category) {
          const error = new Error('Category not found') as Error & { status: number }
          error.status = 404
          throw error
        }
        if (payload.name !== undefined) category.name = payload.name
        if (payload.description !== undefined) category.description = payload.description
        if (payload.active !== undefined) category.active = payload.active
        if (payload.displayOrder !== undefined) category.displayOrder = payload.displayOrder
        addAudit(state, user, 'CATEGORY_UPDATED', 'CATEGORY', category.id, 'Reference category updated in demo mode')
        return category
      }),
      config,
    )
  }
  if (path === 'admin/jurisdictions' && method === 'GET') {
    const jurisdictions = readDemoState().jurisdictions
    return response(
      queryValue(config, 'includeInactive') === 'false' ? jurisdictions.filter((item) => item.active) : jurisdictions,
      config,
    )
  }
  if (path === 'admin/reference/jurisdictions' && method === 'POST') {
    const payload = requestBody<CreateJurisdictionPayload>(config)
    return response(
      mutateDemoState((state) => {
        const jurisdiction = {
          id: uid('jur'),
          code: payload.code.toUpperCase(),
          name: payload.name,
          description: payload.description ?? null,
          active: true,
        }
        state.jurisdictions.push(jurisdiction)
        addAudit(state, user, 'JURISDICTION_CREATED', 'JURISDICTION', jurisdiction.id, `Created ${jurisdiction.name} in demo mode`)
        return jurisdiction
      }),
      config,
      201,
    )
  }
  if (path === 'admin/service-areas' && method === 'GET') {
    const departmentId = queryValue(config, 'departmentId')
    const active = booleanParam(queryValue(config, 'active'))
    const areas = readDemoState().departments
      .flatMap((department) =>
        department.serviceAreas.map((area) => toServiceArea(area, department.name)),
      )
      .filter((area) => {
        if (departmentId && area.departmentId !== departmentId) return false
        if (active !== undefined && area.active !== active) return false
        return true
      })
    return response(areas, config)
  }

  if (path === 'admin/audit-logs' && method === 'GET') {
    return response(
      paginate(
        filterAudit(readDemoState().audit, {
          action: queryValue(config, 'action'),
          entityType: queryValue(config, 'entityType'),
          entityId: queryValue(config, 'entityId'),
          actorId: queryValue(config, 'actorId'),
          from: queryValue(config, 'from'),
          to: queryValue(config, 'to'),
        }) as AdminAuditEntry[],
        numeric(queryValue(config, 'page'), 0),
        numeric(queryValue(config, 'size'), 20),
      ),
      config,
    )
  }

  return null
}

const demoAdapter: AxiosAdapter = async (config) => {
  const path = routePath(config)
  const method = (config.method ?? 'get').toUpperCase()

  try {
  const authResult = await handleAuth(path, method, config)
  if (authResult) return authResult
  const metaResult = await handleMeta(path, method, config)
  if (metaResult) return metaResult

  requireUser(config)

  const notificationResult = await handleNotifications(path, method, config)
  if (notificationResult) return notificationResult

  if (path.startsWith('uploads/')) {
    const uploadResult = await handleUploads(path, method, config)
    if (uploadResult) return uploadResult
  }
  if (path.startsWith('citizen/')) {
    const user = requireUser(config, ['CITIZEN'])
    const citizenResult = await handleCitizen(path, method, config, user)
    if (citizenResult) return citizenResult
  }
  if (path.startsWith('department/')) {
    const user = requireUser(config, ['DEPARTMENT_ADMIN'])
    const departmentResult = await handleDepartment(path, method, config, user)
    if (departmentResult) return departmentResult
  }
  if (path.startsWith('admin/')) {
    const user = requireUser(config, ['SUPER_ADMIN'])
    const adminResult = await handleAdmin(path, method, config, user)
    if (adminResult) return adminResult
  }

  return fail(404, 'Not found', `Demo mode has no handler for ${method} ${path}.`, config)
  } catch (error) {
    if (error instanceof AxiosError) throw error
    const status = (error as { status?: number }).status ?? 500
    const title = status === 404 ? 'Not found' : status === 403 ? 'Forbidden' : status === 401 ? 'Unauthorized' : 'Demo error'
    const detail = error instanceof Error ? error.message : 'The local demo request failed.'
    return fail(status, title, detail, config)
  }
}

/** Installs the local adapter on the shared API instance. Production builds are a no-op. */
export function installDemoAdapter(api: AxiosInstance): void {
  if (!DEMO_ENABLED) return
  api.defaults.adapter = demoAdapter
}
