import type { Role } from '../lib/api/client'
import type {
  AiAnalysisResponse,
  Category,
  ComplaintDetail,
  ComplaintStatus,
  ComplaintSummary,
  LocationSource,
  NotificationEntry,
  Severity,
  StatusEntry,
  SubmitComplaintPayload,
} from '../lib/api/citizen'
import type {
  AdminActivityEntry,
  AdminAuditEntry,
  AdminComplaintSummary,
  AdminCount,
  AdminDepartmentDetail,
  AdminDepartmentSummary,
  AdminGovernance,
  AdminOverview,
  AdminRoutingDecision,
  AdminServiceArea,
  AdminStats,
  AdminUser,
  AdminUserQuery,
  AiAnalysisEntry,
  RoutingCandidate,
} from '../lib/api/admin'
import type {
  ActivityEntry,
  CategoryCount,
  DepartmentAnalytics,
  DepartmentComplaintDetail,
  DepartmentProfile,
  NoteResponse,
  ServiceAreaResponse,
  TeamMemberResponse,
  WardCount,
} from '../lib/api/department'
import type {
  DemoComplaint,
  DemoDepartment,
  DemoNotification,
  DemoState,
  DemoUploadedImage,
  DemoUser,
} from './types'

const HOUR = 60 * 60 * 1000
const DAY = 24 * HOUR

export function nowIso(): string {
  return new Date().toISOString()
}

export function uid(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 9)}`
}

function humanize(value: string): string {
  return value
    .toLowerCase()
    .replace(/[_-]+/g, ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase())
}

export function toMe(user: DemoUser) {
  return {
    id: user.id,
    email: user.email,
    fullName: user.fullName,
    role: user.role,
    accountStatus: user.accountStatus,
    verificationStatus: user.verificationStatus,
    departmentId: user.departmentId,
    permissions: user.permissions,
    phone: user.phone,
  }
}

export function toUserInfo(user: DemoUser) {
  return {
    id: user.id,
    email: user.email,
    fullName: user.fullName,
    role: user.role,
    departmentId: user.departmentId,
    verificationStatus: user.verificationStatus,
  }
}

export function toAdminUser(user: DemoUser, state: DemoState): AdminUser {
  return {
    id: user.id,
    email: user.email,
    fullName: user.fullName,
    role: user.role,
    accountStatus: user.accountStatus,
    departmentId: user.departmentId,
    departmentName: state.departments.find((department) => department.id === user.departmentId)?.name ?? null,
    verificationStatus: user.verificationStatus,
    createdAt: user.createdAt,
  }
}

export function toDepartmentSummary(department: DemoDepartment): AdminDepartmentSummary {
  return {
    id: department.id,
    name: department.name,
    departmentCode: department.departmentCode,
    departmentType: department.departmentType,
    officialEmail: department.officialEmail,
    city: department.city,
    verificationStatus: department.verificationStatus,
    isActive: department.isActive,
    acceptsComplaints: department.acceptsComplaints,
    verifiedAt: department.verifiedAt,
  }
}

export function toAdminDepartmentDetail(department: DemoDepartment): AdminDepartmentDetail {
  return {
    ...toDepartmentSummary(department),
    description: department.description,
    contactNumber: department.contactNumber,
    officeAddress: department.officeAddress,
    state: department.state,
    country: department.country,
    autoAssignEnabled: department.autoAssignEnabled,
    routingPriority: department.routingPriority,
    maxOpenComplaints: department.maxOpenComplaints,
    createdAt: department.createdAt,
  }
}

export function toDepartmentProfile(department: DemoDepartment): DepartmentProfile {
  return {
    id: department.id,
    name: department.name,
    departmentCode: department.departmentCode,
    departmentType: department.departmentType,
    description: department.description,
    officialEmail: department.officialEmail,
    contactNumber: department.contactNumber,
    officeAddress: department.officeAddress,
    city: department.city,
    state: department.state,
    country: department.country,
    verificationStatus: department.verificationStatus,
    active: department.isActive,
    acceptsComplaints: department.acceptsComplaints,
    autoAssignEnabled: department.autoAssignEnabled,
    routingPriority: department.routingPriority,
    maxOpenComplaints: department.maxOpenComplaints,
  }
}

export function toTeamMember(user: DemoUser): TeamMemberResponse {
  return {
    userId: user.id,
    fullName: user.fullName,
    email: user.email,
    adminLevel: user.adminLevel,
    primaryAdmin: user.primaryAdmin,
    title: user.title,
    accountStatus: user.accountStatus,
  }
}

export function toServiceArea(
  area: DemoDepartment['serviceAreas'][number],
  departmentName?: string,
): AdminServiceArea {
  return {
    id: area.id,
    departmentId: area.departmentId,
    departmentName,
    name: area.name,
    coversWard: area.coversWard,
    coversZone: area.coversZone,
    jurisdictionCode: area.jurisdictionCode,
    active: area.active,
    createdAt: area.createdAt,
  }
}

export function toDepartmentServiceArea(area: DemoDepartment['serviceAreas'][number]): ServiceAreaResponse {
  return { ...area }
}

export function toComplaintSummary(complaint: DemoComplaint): ComplaintSummary {
  return {
    id: complaint.id,
    complaintNumber: complaint.complaintNumber,
    createdAt: complaint.createdAt,
    description: complaint.description,
    status: complaint.status,
    severity: complaint.severity,
    categoryCode: complaint.categoryCode,
    ward: complaint.ward,
    city: complaint.city,
    departmentId: complaint.departmentId,
  }
}

export function toComplaintDetail(complaint: DemoComplaint): ComplaintDetail {
  return {
    ...toComplaintSummary(complaint),
    additionalInfo: complaint.additionalInfo,
    subcategoryCode: complaint.subcategoryCode,
    addressLine: complaint.addressLine,
    state: complaint.state,
    postalCode: complaint.postalCode,
    country: complaint.country,
    zone: complaint.zone,
    locationSource: complaint.locationSource,
    latitude: complaint.latitude,
    longitude: complaint.longitude,
    departmentId: complaint.departmentId,
    departmentName: complaint.departmentName,
    assignedAt: complaint.assignedAt,
    resolvedAt: complaint.resolvedAt,
    resolutionDueAt: complaint.resolutionDueAt,
    timeline: complaint.timeline,
    comments: complaint.comments,
    assignments: complaint.assignments,
    feedback: complaint.feedback,
    images: complaint.images,
    analysis: complaint.analysis,
  }
}

export function toDepartmentComplaintDetail(complaint: DemoComplaint): DepartmentComplaintDetail {
  return {
    ...toComplaintDetail(complaint),
    resolutionSummary: complaint.resolutionSummary,
    internalNotes: complaint.internalNotes,
    routingDecisions: complaint.routingDecisions,
  }
}

export function toAdminComplaintSummary(complaint: DemoComplaint): AdminComplaintSummary {
  return {
    ...toComplaintSummary(complaint),
    departmentName: complaint.departmentName,
    routingOutcome: complaint.routingDecisions.find((decision) => decision.current)?.outcome ?? null,
    aiStatus: complaint.analysis?.status ?? null,
  }
}

export function toNotification(notification: DemoNotification): NotificationEntry {
  return {
    id: notification.id,
    type: notification.type,
    title: notification.title,
    body: notification.body,
    channel: notification.channel,
    deliveryStatus: notification.deliveryStatus,
    complaintNumber: notification.complaintNumber,
    readAt: notification.readAt,
    createdAt: notification.createdAt,
    sentAt: notification.sentAt,
    complaintId: notification.complaintId,
  }
}

export function toUploadedImage(image: DemoUploadedImage) {
  return {
    imageId: image.imageId,
    complaintId: image.complaintId,
    storageKey: image.storageKey,
    contentType: image.contentType,
    sizeBytes: image.sizeBytes,
    width: null,
    height: null,
    uploadStatus: image.uploadStatus,
    createdAt: image.createdAt,
  }
}

const PLACEHOLDER_IMAGE =
  'data:image/svg+xml;charset=utf-8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="360" viewBox="0 0 640 360">' +
      '<rect width="640" height="360" fill="#efeae0"/>' +
      '<circle cx="320" cy="150" r="54" fill="#c9d9b5"/>' +
      '<text x="320" y="250" text-anchor="middle" font-family="sans-serif" font-size="22" fill="#476333">DEMO IMAGE</text>' +
      '<text x="320" y="282" text-anchor="middle" font-family="sans-serif" font-size="14" fill="#7c7">Simulated attachment</text>' +
      '</svg>',
  )

export function toViewableImage(image: DemoUploadedImage) {
  return {
    imageId: image.imageId,
    url: image.previewDataUrl ?? PLACEHOLDER_IMAGE,
    expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
  }
}

export function toComplaintPhoto(image: DemoUploadedImage) {
  return {
    id: image.imageId,
    storageKey: image.storageKey,
    contentType: image.contentType,
    sizeBytes: image.sizeBytes,
    imageKind: image.imageKind,
    uploadStatus: image.uploadStatus,
    createdAt: image.createdAt,
  }
}

export function toAnalysisEntry(
  analysis: AiAnalysisResponse,
  source: 'AI' | 'ADMIN_OVERRIDE' = 'AI',
  adminOverrideReason: string | null = null,
  actorName = 'Simulated classifier',
): AiAnalysisEntry {
  return {
    id: analysis.id,
    status: analysis.status,
    source,
    isCivicIssue: analysis.isCivicIssue,
    categoryCode: analysis.categoryCode,
    subcategoryCode: analysis.subcategoryCode,
    severity: analysis.severity,
    description: analysis.description,
    confidence: analysis.confidence,
    requiresHumanReview: analysis.requiresHumanReview,
    adminOverrideReason,
    actorName,
    actorRole: source === 'AI' ? 'AI' : 'SUPER_ADMIN',
    createdAt: analysis.createdAt,
  }
}

export function toRoutingDecision(
  id: string,
  categoryCode: string | null,
  confidence: number | null,
  departmentId: string | null,
  outcome: string,
  reason: string,
  candidates: RoutingCandidate[],
  createdAt = nowIso(),
): AdminRoutingDecision {
  return {
    id,
    rulesetVersion: 'DEMO-RULES-2026.1',
    aiCategoryCode: categoryCode,
    confidence,
    locationPointWkt: null,
    matchedJurisdictionId: 'jur-bhopal-city',
    candidates,
    selectedDepartmentId: departmentId,
    outcome,
    reason,
    current: true,
    createdAt,
  }
}

export function addStatus(
  complaint: DemoComplaint,
  fromStatus: ComplaintStatus | null,
  toStatus: ComplaintStatus,
  actorRole: string | null,
  reason: string | null,
): void {
  const entry: StatusEntry = { fromStatus, toStatus, actorRole, reason, createdAt: nowIso() }
  complaint.timeline.push(entry)
  complaint.status = toStatus
  if (toStatus === 'RESOLVED' || toStatus === 'CLOSED') complaint.resolvedAt = entry.createdAt
  if (toStatus === 'REOPENED') complaint.resolvedAt = null
}

export function addComment(
  complaint: DemoComplaint,
  authorName: string,
  authorRole: string,
  body: string,
): void {
  complaint.comments.push({
    id: uid('comment'),
    authorName,
    authorRole,
    body,
    createdAt: nowIso(),
  })
}

export function addNote(
  complaint: DemoComplaint,
  authorName: string,
  body: string,
): NoteResponse {
  const note: NoteResponse = { id: uid('note'), authorName, body, createdAt: nowIso() }
  complaint.internalNotes.push(note)
  return note
}

export function addNotification(
  state: DemoState,
  userId: string,
  type: string,
  title: string,
  body: string,
  complaint: DemoComplaint | null = null,
): void {
  state.notifications.unshift({
    id: `notif-${state.counters.notification++}`,
    userId,
    type,
    title,
    body,
    channel: 'IN_APP',
    deliveryStatus: 'SENT',
    complaintNumber: complaint?.complaintNumber ?? null,
    complaintId: complaint?.id ?? null,
    readAt: null,
    createdAt: nowIso(),
    sentAt: nowIso(),
  })
}

export function addAudit(
  state: DemoState,
  actor: DemoUser,
  action: string,
  entityType: string,
  entityId: string,
  reason: string,
): void {
  state.audit.unshift({
    id: `audit-${state.counters.audit++}`,
    actorId: actor.id,
    actorName: actor.fullName,
    actorRole: actor.role,
    action,
    entityType,
    entityId,
    reason,
    correlationId: `demo-correlation-${state.counters.audit}`,
    createdAt: nowIso(),
  })
}

function flattenCategories(categories: Category[]): Category[] {
  return categories.flatMap((category) => [category, ...flattenCategories(category.children)])
}

export function categoryName(state: DemoState, code: string | null): string {
  if (!code) return 'Uncategorised'
  return flattenCategories(state.categories).find((category) => category.code === code)?.name ?? humanize(code)
}

function inferCategory(description: string): string | null {
  const text = description.toLowerCase()
  if (/pothole|road|divider|footpath|marking|manhole|kerb/.test(text)) return 'ROAD'
  if (/water|drain|leak|sewage|pipeline|flood/.test(text)) return 'WATER'
  if (/garbage|waste|dump|sanitation|rubbish|debris/.test(text)) return 'WASTE'
  if (/light|lamp|transformer|spark|electric|pole/.test(text)) return 'LIGHT'
  if (/tree|park|fence|playground|garden/.test(text)) return 'PARK'
  return null
}

function inferSeverity(description: string): Severity {
  const text = description.toLowerCase()
  if (/critical|sparking|collapse|serious hazard|manhole|fire|electrocution/.test(text)) return 'CRITICAL'
  if (/high|pothole|flood|overflow|leak|blocked|safety/.test(text)) return 'HIGH'
  if (/faded|minor|small|cosmetic/.test(text)) return 'LOW'
  return 'MEDIUM'
}

function dueDateFor(severity: Severity): string {
  const hours = severity === 'CRITICAL' ? 8 : severity === 'HIGH' ? 24 : severity === 'MEDIUM' ? 72 : 120
  return new Date(Date.now() + hours * HOUR).toISOString()
}

export function eligibleDepartments(state: DemoState, categoryCode: string | null): DemoDepartment[] {
  const categories = flattenCategories(state.categories)
  const selected = categories.find((category) => category.code === categoryCode)
  const codes = new Set<string>(
    [selected?.code, ...(selected?.children.map((child) => child.code) ?? [])].filter(
      (code): code is string => Boolean(code),
    ),
  )
  const capable = state.departments.filter(
    (department) =>
      department.verificationStatus === 'VERIFIED' &&
      department.isActive &&
      department.acceptsComplaints &&
      department.capabilities.some((capability) => codes.has(capability.categoryCode)),
  )
  return capable.length
    ? capable.sort((a, b) => b.routingPriority - a.routingPriority)
    : state.departments.filter(
        (department) =>
          department.verificationStatus === 'VERIFIED' &&
          department.isActive &&
          department.acceptsComplaints,
      )
}

export function routingCandidates(state: DemoState, categoryCode: string | null, selectedId: string | null): RoutingCandidate[] {
  const eligible = eligibleDepartments(state, categoryCode).slice(0, 3)
  const candidates: RoutingCandidate[] = eligible.map((department, index) => ({
    departmentId: department.id,
    departmentName: department.name,
    geoScore: Number((0.94 - index * 0.12).toFixed(2)),
    geoMatch: index === 0 ? 'WARD' : 'ZONE',
    catScore: Number((0.92 - index * 0.18).toFixed(2)),
    catMatch: index === 0 ? 'PRIMARY' : 'SECONDARY',
    priority: department.routingPriority,
    total: Number((0.93 - index * 0.14).toFixed(2)),
    atCapacity:
      department.maxOpenComplaints !== null &&
      state.complaints.filter(
        (complaint) => complaint.departmentId === department.id && !['RESOLVED', 'CLOSED', 'REJECTED'].includes(complaint.status),
      ).length >= department.maxOpenComplaints,
    reasons: [
      index === 0 ? 'Primary category capability' : 'Zone overlap',
      `Routing priority ${department.routingPriority}`,
    ],
  }))
  if (!candidates.some((candidate) => candidate.departmentId === selectedId)) {
    candidates.push({
      departmentId: selectedId ?? 'none',
      departmentName: state.departments.find((department) => department.id === selectedId)?.name ?? 'No eligible department',
      geoScore: selectedId ? 0.9 : 0,
      geoMatch: selectedId ? 'WARD' : 'NONE',
      catScore: selectedId ? 0.9 : 0,
      catMatch: selectedId ? 'PRIMARY' : 'NONE',
      priority: 0,
      total: selectedId ? 0.9 : 0,
      atCapacity: false,
      reasons: [selectedId ? 'Selected by deterministic routing' : 'No verified and accepting department matched'],
    })
  }
  return candidates
}

function simulatedAnalysis(
  id: string,
  description: string,
  categoryCode: string | null,
  subcategoryCode: string | null,
): AiAnalysisResponse {
  const severity = inferSeverity(description)
  const confidence = categoryCode ? 0.88 : 0.52
  const department = categoryCode ? categoryCode : null
  return {
    id,
    status: 'SUCCESS',
    isCivicIssue: true,
    categoryCode,
    subcategoryCode,
    severity,
    description: `Simulated AI: likely ${categoryCode ? humanize(categoryCode) : 'civic'} issue based on the description. No real model was called.`,
    safetyRisk: severity === 'CRITICAL' || severity === 'HIGH',
    visibleDamage: severity === 'CRITICAL' || severity === 'HIGH' ? 'Potentially significant damage' : 'Limited visible damage',
    isBlurry: false,
    requiresAdditionalInfo: confidence < 0.6,
    confidence,
    recommendedDepartmentHint: department ? `${humanize(department)} (advisory)` : 'Category needs human review (advisory)',
    requiresHumanReview: confidence < 0.6,
    errorMessage: null,
    createdAt: nowIso(),
  }
}

export function createComplaint(
  state: DemoState,
  user: DemoUser,
  payload: SubmitComplaintPayload,
): DemoComplaint {
  const id = uid('complaint')
  const createdAt = nowIso()
  const description = payload.description.trim()
  const categoryCode = payload.categoryCode ?? inferCategory(description)
  const subcategoryCode = payload.subcategoryCode ?? null
  const severity = inferSeverity(description)
  const analysis = simulatedAnalysis(`analysis-${id}`, description, categoryCode, subcategoryCode)
  const department = (analysis.confidence ?? 0) >= 0.6 ? eligibleDepartments(state, categoryCode)[0] ?? null : null
  const candidates = routingCandidates(state, categoryCode, department?.id ?? null)
  const outcome = department ? 'ASSIGNED' : analysis.requiresHumanReview ? 'AMBIGUOUS' : 'UNASSIGNED'
  const routingReason = department
    ? `Simulated deterministic routing matched ${department.name}.`
    : 'No verified and accepting department matched the simulated ruleset.'
  const timeline: StatusEntry[] = [
    { fromStatus: null, toStatus: 'SUBMITTED', actorRole: 'CITIZEN', reason: 'Demo submission saved locally', createdAt },
    {
      fromStatus: 'SUBMITTED',
      toStatus: 'AI_ANALYSIS_COMPLETED',
      actorRole: 'AI',
      reason: 'Simulated AI classification completed',
      createdAt: nowIso(),
    },
    {
      fromStatus: 'AI_ANALYSIS_COMPLETED',
      toStatus: 'PENDING_ASSIGNMENT',
      actorRole: 'SYSTEM',
      reason: 'Simulated routing evaluation started',
      createdAt: nowIso(),
    },
  ]
  if (department) {
    timeline.push({
      fromStatus: 'PENDING_ASSIGNMENT',
      toStatus: 'ASSIGNED',
      actorRole: 'SYSTEM',
      reason: routingReason,
      createdAt: nowIso(),
    })
  } else {
    timeline.push({
      fromStatus: 'PENDING_ASSIGNMENT',
      toStatus: 'UNASSIGNED',
      actorRole: 'SYSTEM',
      reason: routingReason,
      createdAt: nowIso(),
    })
  }

  const imageIds = payload.imageIds ?? []
  const images = imageIds.flatMap((imageId) => {
    const image = state.images.find((item) => item.imageId === imageId)
    if (!image) return []
    image.complaintId = id
    image.uploadStatus = 'VERIFIED'
    return [toComplaintPhoto(image)]
  })

  const complaint: DemoComplaint = {
    id,
    complaintNumber: `CIV-${new Date().getFullYear()}-${String(state.counters.complaint++).padStart(4, '0')}`,
    ownerId: user.id,
    createdAt,
    description,
    additionalInfo: payload.additionalInfo ?? null,
    status: department ? 'ASSIGNED' : 'UNASSIGNED',
    severity,
    categoryCode,
    subcategoryCode,
    ward: payload.ward ?? null,
    city: payload.city ?? 'Bhopal',
    state: payload.state ?? 'Madhya Pradesh',
    postalCode: payload.postalCode ?? null,
    country: payload.country ?? 'India',
    zone: payload.zone ?? null,
    addressLine: payload.addressLine ?? null,
    locationSource: payload.locationSource as LocationSource,
    latitude: payload.latitude,
    longitude: payload.longitude,
    departmentId: department?.id ?? null,
    departmentName: department?.name ?? null,
    assignedAt: department ? nowIso() : null,
    resolvedAt: null,
    resolutionDueAt: dueDateFor(severity),
    timeline,
    comments: [],
    assignments: department
      ? [
          {
            departmentId: department.id,
            departmentName: department.name,
            assignmentType: 'AUTO',
            active: true,
            assignedAt: nowIso(),
            unassignedAt: null,
          },
        ]
      : [],
    feedback: null,
    images,
    analysis,
    resolutionSummary: null,
    internalNotes: [],
    routingDecisions: [
      toRoutingDecision(`route-${id}`, categoryCode, analysis.confidence, department?.id ?? null, outcome, routingReason, candidates, nowIso()),
    ],
    aiHistory: [toAnalysisEntry(analysis)],
  }

  state.complaints.unshift(complaint)
  addNotification(
    state,
    user.id,
    'SUBMITTED',
    'Report received in demo mode',
    `${complaint.complaintNumber} was saved locally with simulated AI classification.`,
    complaint,
  )
  if (department) {
    state.users
      .filter((member) => member.role === 'DEPARTMENT_ADMIN' && member.departmentId === department.id)
      .forEach((member) =>
        addNotification(state, member.id, 'NEW_COMPLAINT', 'New complaint in your demo queue', `${complaint.complaintNumber} was assigned to your department.`, complaint),
      )
  }
  return complaint
}

export interface ComplaintQuery {
  page?: number
  size?: number
  sort?: string
  status?: ComplaintStatus | ComplaintStatus[]
  categoryCode?: string
  ward?: string
  zone?: string
  severity?: Severity
  search?: string
  overdue?: boolean
  from?: string
  to?: string
  departmentId?: string
  routingOutcome?: string
  aiStatus?: string
}

export function filterComplaints(complaints: DemoComplaint[], query: ComplaintQuery): DemoComplaint[] {
  const statuses = Array.isArray(query.status) ? query.status : query.status ? [query.status] : []
  const search = query.search?.trim().toLowerCase()
  const now = Date.now()
  const filtered = complaints.filter((complaint) => {
    if (statuses.length && !statuses.includes(complaint.status)) return false
    if (query.categoryCode && complaint.categoryCode !== query.categoryCode) return false
    if (query.ward && complaint.ward !== query.ward) return false
    if (query.zone && complaint.zone !== query.zone) return false
    if (query.severity && complaint.severity !== query.severity) return false
    if (query.departmentId && complaint.departmentId !== query.departmentId) return false
    if (query.from && new Date(complaint.createdAt) < new Date(query.from)) return false
    if (query.to && new Date(complaint.createdAt) > new Date(query.to)) return false
    if (query.overdue === true && !(complaint.resolutionDueAt && new Date(complaint.resolutionDueAt).getTime() < now && !['RESOLVED', 'CLOSED', 'REJECTED'].includes(complaint.status))) return false
    if (query.routingOutcome && complaint.routingDecisions.find((decision) => decision.current)?.outcome !== query.routingOutcome) return false
    if (query.aiStatus && complaint.analysis?.status !== query.aiStatus) return false
    if (search) {
      const haystack = [complaint.complaintNumber, complaint.description, complaint.addressLine, complaint.ward, complaint.categoryCode]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
      if (!haystack.includes(search)) return false
    }
    return true
  })
  const [field, direction] = (query.sort ?? 'createdAt,desc').split(',')
  const rank: Record<Severity, number> = { LOW: 0, MEDIUM: 1, HIGH: 2, CRITICAL: 3 }
  const multiplier = direction?.toLowerCase() === 'asc' ? 1 : -1
  return [...filtered].sort((a, b) => {
    let left: string | number
    let right: string | number
    switch (field) {
      case 'complaintNumber':
        left = a.complaintNumber
        right = b.complaintNumber
        break
      case 'severity':
        left = rank[a.severity]
        right = rank[b.severity]
        break
      case 'status':
        left = a.status
        right = b.status
        break
      case 'description':
        left = a.description
        right = b.description
        break
      default:
        left = new Date(a.createdAt).getTime()
        right = new Date(b.createdAt).getTime()
    }
    if (left < right) return -1 * multiplier
    if (left > right) return 1 * multiplier
    return a.complaintNumber.localeCompare(b.complaintNumber)
  })
}

export function paginate<T>(items: T[], page = 0, size = 20) {
  const safeSize = Math.max(1, Math.min(100, Number(size) || 20))
  const safePage = Math.max(0, Number(page) || 0)
  const start = safePage * safeSize
  return {
    content: items.slice(start, start + safeSize),
    page: safePage,
    size: safeSize,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / safeSize),
  }
}

export function activityFrom(complaints: DemoComplaint[], limit = 20): ActivityEntry[] {
  return complaints
    .flatMap((complaint) =>
      complaint.timeline
        .filter((entry) => entry.fromStatus !== null)
        .map((entry) => ({
          complaintId: complaint.id,
          complaintNumber: complaint.complaintNumber,
          fromStatus: entry.fromStatus as ComplaintStatus,
          toStatus: entry.toStatus,
          actorRole: entry.actorRole,
          reason: entry.reason,
          createdAt: entry.createdAt,
        })),
    )
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, Math.max(1, Math.min(100, limit)))
}

export function adminActivityFrom(complaints: DemoComplaint[], limit = 20): AdminActivityEntry[] {
  return activityFrom(complaints, limit)
}

function countBy<T>(items: T[], key: (item: T) => string | null): Map<string, number> {
  const counts = new Map<string, number>()
  items.forEach((item) => {
    const value = key(item) ?? 'UNSPECIFIED'
    counts.set(value, (counts.get(value) ?? 0) + 1)
  })
  return counts
}

function toCountList(counts: Map<string, number>, labelFor: (key: string) => string): AdminCount[] {
  return [...counts.entries()]
    .map(([key, count]) => ({ key, label: labelFor(key), count }))
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label))
}

function averageResolutionHours(complaints: DemoComplaint[]): number | null {
  const resolved = complaints.filter((complaint) => complaint.resolvedAt)
  if (!resolved.length) return null
  const total = resolved.reduce((sum, complaint) => {
    const start = new Date(complaint.createdAt).getTime()
    const end = new Date(complaint.resolvedAt as string).getTime()
    return sum + Math.max(0, (end - start) / HOUR)
  }, 0)
  return Math.round((total / resolved.length) * 10) / 10
}

function slaPercent(complaints: DemoComplaint[]): number | null {
  const resolved = complaints.filter((complaint) => complaint.resolvedAt && complaint.resolutionDueAt)
  if (!resolved.length) return null
  const met = resolved.filter(
    (complaint) =>
      new Date(complaint.resolvedAt as string).getTime() <=
      new Date(complaint.resolutionDueAt as string).getTime(),
  ).length
  return Math.round((met / resolved.length) * 1000) / 10
}

function trendFor(complaints: DemoComplaint[], periodDays: number) {
  const bucketCount = 7
  const now = Date.now()
  const bucketMs = (periodDays * DAY) / bucketCount
  return Array.from({ length: bucketCount }, (_, index) => {
    const end = now - (bucketCount - index - 1) * bucketMs
    const start = end - bucketMs
    const inBucket = complaints.filter((complaint) => {
      const created = new Date(complaint.createdAt).getTime()
      return created >= start && created < end
    })
    return {
      period: new Date(start).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }),
      received: inBucket.length,
      resolved: inBucket.filter(
        (complaint) =>
          complaint.resolvedAt &&
          new Date(complaint.resolvedAt).getTime() >= start &&
          new Date(complaint.resolvedAt).getTime() < end,
      ).length,
    }
  })
}

export function departmentAnalytics(state: DemoState, departmentId: string, periodDays: number): DepartmentAnalytics {
  const complaints = state.complaints.filter((complaint) => complaint.departmentId === departmentId)
  const byStatus: Record<string, number> = {}
  complaints.forEach((complaint) => {
    byStatus[complaint.status] = (byStatus[complaint.status] ?? 0) + 1
  })
  const cutoff = Date.now() - periodDays * DAY
  const now = Date.now()
  const open = complaints.filter((complaint) => !['RESOLVED', 'CLOSED', 'REJECTED'].includes(complaint.status))
  const byCategoryCounts = countBy(complaints, (complaint) => complaint.categoryCode)
  const byWardCounts = countBy(complaints, (complaint) => complaint.ward)
  const byCategory: CategoryCount[] = [...byCategoryCounts.entries()]
    .map(([code, count]) => ({ code, name: categoryName(state, code), count }))
    .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name))
  const byWard: WardCount[] = [...byWardCounts.entries()]
    .map(([ward, count]) => ({ ward, count }))
    .sort((a, b) => b.count - a.count || a.ward.localeCompare(b.ward))
  const averageOpenAgeHours = open.length
    ? Math.round(
        (open.reduce((sum, complaint) => sum + Math.max(0, now - new Date(complaint.createdAt).getTime()) / HOUR, 0) /
          open.length) *
          10,
      ) / 10
    : null
  return {
    total: complaints.length,
    byStatus,
    resolved: complaints.filter((complaint) => ['RESOLVED', 'CLOSED'].includes(complaint.status)).length,
    overdue: open.filter((complaint) => complaint.resolutionDueAt && new Date(complaint.resolutionDueAt).getTime() < now).length,
    unassigned: complaints.filter((complaint) => !complaint.departmentId).length,
    averageResolutionHours: averageResolutionHours(complaints),
    periodDays: periodDays as 7 | 30 | 90,
    receivedInPeriod: complaints.filter((complaint) => new Date(complaint.createdAt).getTime() >= cutoff).length,
    resolvedInPeriod: complaints.filter((complaint) => complaint.resolvedAt && new Date(complaint.resolvedAt).getTime() >= cutoff).length,
    pending: (byStatus.SUBMITTED ?? 0) + (byStatus.PENDING_ASSIGNMENT ?? 0) + (byStatus.AI_ANALYSIS_PENDING ?? 0),
    accepted: (byStatus.ASSIGNED ?? 0) + (byStatus.ACKNOWLEDGED ?? 0),
    inProgress: (byStatus.IN_PROGRESS ?? 0) + (byStatus.ON_HOLD ?? 0) + (byStatus.REOPENED ?? 0),
    rejected: byStatus.REJECTED ?? 0,
    closed: byStatus.CLOSED ?? 0,
    byCategory,
    byWard,
    trend: trendFor(complaints, periodDays),
    averageOpenAgeHours,
    slaCompliancePercent: slaPercent(complaints),
  }
}

export function adminOverview(state: DemoState, periodDays: number): AdminOverview {
  const complaints = state.complaints
  const byStatus: Record<string, number> = {}
  complaints.forEach((complaint) => {
    byStatus[complaint.status] = (byStatus[complaint.status] ?? 0) + 1
  })
  const now = Date.now()
  const failedAi = complaints.filter((complaint) => complaint.analysis && ['FAILED', 'TIMEOUT', 'INVALID_SCHEMA'].includes(complaint.analysis.status)).length
  const ambiguous = complaints.filter(
    (complaint) =>
      ['UNASSIGNED', 'PENDING_ASSIGNMENT'].includes(complaint.status) &&
      (complaint.analysis?.requiresHumanReview || (complaint.routingDecisions.find((decision) => decision.current)?.outcome ?? '') === 'AMBIGUOUS'),
  ).length
  const stats: AdminStats = {
    total: complaints.length,
    resolved: complaints.filter((complaint) => ['RESOLVED', 'CLOSED'].includes(complaint.status)).length,
    pending: (byStatus.SUBMITTED ?? 0) + (byStatus.PENDING_ASSIGNMENT ?? 0) + (byStatus.ASSIGNED ?? 0) + (byStatus.ACKNOWLEDGED ?? 0),
    unassigned: complaints.filter((complaint) => !complaint.departmentId).length,
    overdue: complaints.filter(
      (complaint) =>
        complaint.resolutionDueAt &&
        new Date(complaint.resolutionDueAt).getTime() < now &&
        !['RESOLVED', 'CLOSED', 'REJECTED'].includes(complaint.status),
    ).length,
    failedAi,
    averageResolutionHours: averageResolutionHours(complaints),
    slaCompliancePercent: slaPercent(complaints),
  }
  const governance: AdminGovernance = {
    pendingDepartments: state.departments.filter((department) => department.verificationStatus === 'PENDING_VERIFICATION').length,
    suspendedDepartments: state.departments.filter((department) => department.verificationStatus === 'SUSPENDED').length,
    ambiguous,
    lowConfidence: complaints.filter((complaint) => complaint.analysis?.confidence !== null && complaint.analysis?.confidence !== undefined && complaint.analysis.confidence < 0.6).length,
    failedAi,
    reassignmentRequests: state.reassignmentRequests.filter((request) => request.status === 'PENDING').length,
  }
  return {
    periodDays,
    stats,
    governance,
    byStatus: toCountList(countBy(complaints, (complaint) => complaint.status), humanize),
    byCategory: toCountList(countBy(complaints, (complaint) => complaint.categoryCode), (code) => categoryName(state, code)),
    byDepartment: toCountList(countBy(complaints, (complaint) => complaint.departmentName), (name) => name),
    byJurisdiction: toCountList(countBy(complaints, (complaint) => complaint.zone), humanize),
    activity: adminActivityFrom(complaints, 20),
    audit: state.audit.slice(0, 20),
  }
}

export function filterDepartments(
  departments: DemoDepartment[],
  query: { verificationStatus?: string; isActive?: boolean; departmentType?: string; search?: string },
): DemoDepartment[] {
  const search = query.search?.trim().toLowerCase()
  return departments.filter((department) => {
    if (query.verificationStatus && department.verificationStatus !== query.verificationStatus) return false
    if (query.isActive !== undefined && department.isActive !== query.isActive) return false
    if (query.departmentType && department.departmentType !== query.departmentType) return false
    if (search && ![department.name, department.departmentCode, department.officialEmail, department.city].filter(Boolean).join(' ').toLowerCase().includes(search)) return false
    return true
  })
}

export function filterUsers(users: DemoUser[], query: AdminUserQuery): DemoUser[] {
  const search = query.search?.trim().toLowerCase()
  return users.filter((user) => {
    if (query.role && user.role !== query.role) return false
    if (query.status && user.accountStatus !== query.status) return false
    if (query.departmentId && user.departmentId !== query.departmentId) return false
    if (search && ![user.fullName, user.email, user.role, user.accountStatus].join(' ').toLowerCase().includes(search)) return false
    return true
  })
}

export function filterAudit(
  audit: AdminAuditEntry[],
  query: { action?: string; entityType?: string; entityId?: string; actorId?: string; from?: string; to?: string },
): AdminAuditEntry[] {
  return audit.filter((entry) => {
    if (query.action && entry.action !== query.action) return false
    if (query.entityType && entry.entityType !== query.entityType) return false
    if (query.entityId && entry.entityId !== query.entityId) return false
    if (query.actorId && entry.actorId !== query.actorId) return false
    if (query.from && new Date(entry.createdAt) < new Date(query.from)) return false
    if (query.to && new Date(entry.createdAt) > new Date(query.to)) return false
    return true
  })
}

export function requireRole(user: DemoUser, roles: Role[]): void {
  if (!roles.includes(user.role)) {
    const error = new Error('This demo account cannot access that area') as Error & { status?: number }
    error.status = 403
    throw error
  }
}

export function demoUserId(user: DemoUser): string {
  return user.id
}
