import { api } from './client'
import type {
  Category,
  ComplaintDetail,
  ComplaintStatus,
  ComplaintSummary,
  Severity,
} from './citizen'

/**
 * Phase 8 super-admin API surface. All DTOs are local to the admin UI so a
 * change to a citizen/department shape cannot silently change this contract.
 * Every mutation here is accompanied by a mandatory reason in the UI; the
 * server remains responsible for validation, auditing and role enforcement.
 */

export type AdminRole = 'CITIZEN' | 'DEPARTMENT_ADMIN' | 'SUPER_ADMIN'
export type AdminVerificationStatus =
  | 'PENDING_VERIFICATION'
  | 'VERIFIED'
  | 'REJECTED'
  | 'SUSPENDED'
export type AdminDepartmentStatus = Extract<
  AdminVerificationStatus,
  'VERIFIED' | 'REJECTED' | 'SUSPENDED'
>
export type AdminUserStatus = 'ACTIVE' | 'PENDING_VERIFICATION' | 'SUSPENDED' | 'DISABLED'
export type AiAnalysisSource = 'AI' | 'ADMIN_OVERRIDE'
export type ReassignmentDecision = 'APPROVED' | 'REJECTED'

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AdminCount {
  key: string
  label: string
  count: number
}

export interface AdminStats {
  total: number
  resolved: number
  pending: number
  unassigned: number
  overdue: number
  failedAi: number
  averageResolutionHours: number | null
  slaCompliancePercent: number | null
}

export interface AdminGovernance {
  pendingDepartments: number
  suspendedDepartments: number
  ambiguous: number
  lowConfidence: number
  failedAi: number
  reassignmentRequests: number
}

export interface AdminActivityEntry {
  complaintId: string
  complaintNumber: string
  fromStatus: ComplaintStatus | string
  toStatus: ComplaintStatus | string
  actorRole: string | null
  reason: string | null
  createdAt: string
}

export interface AdminAuditEntry {
  id: string
  actorId?: string | null
  actorName: string | null
  actorRole: string | null
  action: string
  entityType: string
  entityId: string | null
  reason: string | null
  correlationId: string | null
  createdAt: string
}

export interface AdminOverview {
  periodDays: number
  stats: AdminStats
  governance: AdminGovernance
  byStatus: AdminCount[]
  byCategory: AdminCount[]
  byDepartment: AdminCount[]
  byJurisdiction: AdminCount[]
  activity: AdminActivityEntry[]
  audit: AdminAuditEntry[]
}

export type AdminAnalytics = Omit<AdminOverview, 'activity' | 'audit'>

export interface AdminDepartmentSummary {
  id: string
  name: string
  departmentCode: string
  departmentType: string
  officialEmail: string
  city: string | null
  verificationStatus: AdminVerificationStatus
  isActive: boolean
  acceptsComplaints: boolean
  verifiedAt: string | null
}

export interface AdminDepartmentDetail extends AdminDepartmentSummary {
  description?: string | null
  contactNumber?: string | null
  officeAddress?: string | null
  state?: string | null
  country?: string | null
  autoAssignEnabled?: boolean
  routingPriority?: number
  maxOpenComplaints?: number | null
  createdAt?: string
}

export interface AdminUser {
  id: string
  email: string
  fullName: string
  role: AdminRole
  accountStatus: AdminUserStatus
  departmentId: string | null
  departmentName?: string | null
  verificationStatus?: AdminVerificationStatus | null
  createdAt?: string
}

export type AdminComplaintSummary = ComplaintSummary & {
  departmentName?: string | null
  routingOutcome?: string | null
  aiStatus?: string | null
}

export type AdminComplaintDetail = ComplaintDetail

export interface AiAnalysisEntry {
  id: string
  status: string
  source: AiAnalysisSource
  isCivicIssue: boolean | null
  categoryCode: string | null
  subcategoryCode: string | null
  severity: Severity | null
  description: string | null
  confidence: number | null
  requiresHumanReview: boolean
  adminOverrideReason: string | null
  actorName?: string | null
  actorRole?: string | null
  createdAt: string
}

export interface AdminAiAnalysesResponse {
  analyses: AiAnalysisEntry[]
}

/** Routing candidates are persisted as JSON by the backend. */
export interface RoutingCandidate {
  departmentId?: string
  departmentName?: string
  geoScore?: number
  geoMatch?: string
  catScore?: number
  catMatch?: string
  priority?: number
  total?: number
  atCapacity?: boolean
  reasons?: string[]
}

export interface AdminRoutingDecision {
  id?: string
  rulesetVersion?: string | null
  aiCategoryCode?: string | null
  confidence?: number | null
  locationPointWkt?: string | null
  matchedJurisdictionId?: string | null
  candidates?: unknown
  selectedDepartmentId?: string | null
  outcome?: string | null
  reason?: string | null
  current?: boolean
  createdAt?: string
}

export interface AdminReassignmentRequest {
  id: string
  complaintId: string
  fromDepartmentId: string | null
  toDepartmentId: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  reason: string
  reviewReason?: string | null
  createdAt: string
  reviewedAt?: string | null
}

export interface AdminCategory extends Category {}

export interface AdminJurisdiction {
  id: string
  code: string
  name: string
  description?: string | null
  active: boolean
}

export interface AdminServiceArea {
  id: string
  departmentId: string
  departmentName?: string | null
  name: string
  coversWard: string | null
  coversZone: string | null
  jurisdictionCode: string | null
  active: boolean
  createdAt: string
}

export interface AdminDepartmentQuery {
  verificationStatus?: AdminVerificationStatus
  isActive?: boolean
  departmentType?: string
  search?: string
}

export interface AdminUserQuery {
  role?: AdminRole
  status?: AdminUserStatus
  departmentId?: string
  search?: string
  page?: number
  size?: number
}

export interface AdminComplaintQuery {
  page?: number
  size?: number
  sort?: string
  search?: string
  status?: ComplaintStatus
  statuses?: ComplaintStatus[]
  categoryCode?: string
  ward?: string
  zone?: string
  severity?: Severity
  overdue?: boolean
  from?: string
  to?: string
  departmentId?: string
  routingOutcome?: string
  aiStatus?: string
}

export interface AuditLogQuery {
  action?: string
  entityType?: string
  entityId?: string
  actorId?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export interface ServiceAreaQuery {
  departmentId?: string
  active?: boolean
}

export interface ReasonPayload {
  reason: string
}

export interface DepartmentStatusPayload extends ReasonPayload {
  status: AdminDepartmentStatus
}

export interface DepartmentOperationsPayload extends ReasonPayload {
  routingPriority: number
  maxOpenComplaints: number | null
  autoAssignEnabled: boolean
}

export interface UserStatusPayload extends ReasonPayload {
  status: Extract<AdminUserStatus, 'ACTIVE' | 'SUSPENDED' | 'DISABLED'>
}

export interface UserRolePayload extends ReasonPayload {
  role: AdminRole
}

export interface AssignComplaintPayload extends ReasonPayload {
  departmentId: string
}

export interface ClassificationOverridePayload extends ReasonPayload {
  categoryCode: string
  subcategoryCode: string
  severity?: Severity
  description?: string
}

export interface ReassignmentReviewPayload extends ReasonPayload {
  decision: ReassignmentDecision
}

export interface CreateCategoryPayload {
  code: string
  name: string
  description?: string
  parentId?: string
  icon?: string
  color?: string
  displayOrder?: number
}

export interface UpdateCategoryPayload {
  name?: string
  description?: string
  active?: boolean
  displayOrder?: number
}

export interface CreateJurisdictionPayload {
  code: string
  name: string
  description?: string
}

// ---------------------------------------------------------------------------
// Platform insight
// ---------------------------------------------------------------------------

export async function getAdminOverview(periodDays = 30): Promise<AdminOverview> {
  const { data } = await api.get<AdminOverview>('/admin/overview', {
    params: { periodDays },
  })
  return data
}

export async function getAdminAnalytics(periodDays = 30): Promise<AdminAnalytics> {
  const { data } = await api.get<AdminAnalytics>('/admin/analytics', {
    params: { periodDays },
  })
  return data
}

export async function getAdminActivity(limit = 20): Promise<AdminActivityEntry[]> {
  const { data } = await api.get<AdminActivityEntry[]>('/admin/activity', {
    params: { limit },
  })
  return data
}

// ---------------------------------------------------------------------------
// Department governance
// ---------------------------------------------------------------------------

export async function listAdminDepartments(
  query: AdminDepartmentQuery = {},
): Promise<AdminDepartmentSummary[]> {
  const { data } = await api.get<AdminDepartmentSummary[]>('/admin/departments', {
    params: query,
  })
  return data
}

export async function verifyAdminDepartment(
  id: string,
  payload: { decision: 'VERIFIED' | 'REJECTED'; reason: string },
): Promise<AdminDepartmentSummary> {
  const { data } = await api.post<AdminDepartmentSummary>(
    `/admin/departments/${id}/verify`,
    payload,
  )
  return data
}

export async function getAdminDepartment(id: string): Promise<AdminDepartmentDetail> {
  const { data } = await api.get<AdminDepartmentDetail>(`/admin/departments/${id}`)
  return data
}

export async function updateAdminDepartmentStatus(
  id: string,
  payload: DepartmentStatusPayload,
): Promise<AdminDepartmentDetail> {
  const { data } = await api.patch<AdminDepartmentDetail>(
    `/admin/departments/${id}/status`,
    payload,
  )
  return data
}

export async function updateAdminDepartmentOperations(
  id: string,
  payload: DepartmentOperationsPayload,
): Promise<AdminDepartmentDetail> {
  const { data } = await api.patch<AdminDepartmentDetail>(
    `/admin/departments/${id}/operations`,
    payload,
  )
  return data
}

// ---------------------------------------------------------------------------
// User management
// ---------------------------------------------------------------------------

export async function listAdminUsers(
  query: AdminUserQuery = {},
): Promise<PagedResponse<AdminUser>> {
  const { data } = await api.get<PagedResponse<AdminUser>>('/admin/users', {
    params: query,
  })
  return data
}

export async function updateAdminUserStatus(
  id: string,
  payload: UserStatusPayload,
): Promise<AdminUser> {
  const { data } = await api.patch<AdminUser>(`/admin/users/${id}/status`, payload)
  return data
}

export async function updateAdminUserRole(
  id: string,
  payload: UserRolePayload,
): Promise<AdminUser> {
  const { data } = await api.patch<AdminUser>(`/admin/users/${id}/role`, payload)
  return data
}

// ---------------------------------------------------------------------------
// Complaint queue and AI/routing governance
// ---------------------------------------------------------------------------

export async function listAdminComplaints(): Promise<AdminComplaintSummary[]> {
  const { data } = await api.get<AdminComplaintSummary[]>('/admin/complaints')
  return data
}

export async function listUnassignedAdminComplaints(): Promise<AdminComplaintSummary[]> {
  const { data } = await api.get<AdminComplaintSummary[]>('/admin/complaints/unassigned')
  return data
}

export async function getAdminComplaintQueue(
  query: AdminComplaintQuery = {},
): Promise<PagedResponse<AdminComplaintSummary>> {
  const { statuses, status, ...rest } = query
  const { data } = await api.get<PagedResponse<AdminComplaintSummary>>(
    '/admin/complaints/queue',
    {
      params: { ...rest, status: statuses?.length ? statuses : status },
      paramsSerializer: { indexes: null },
    },
  )
  return data
}

export async function getAdminComplaint(id: string): Promise<AdminComplaintDetail> {
  const { data } = await api.get<AdminComplaintDetail>(`/admin/complaints/${id}`)
  return data
}

export async function listAdminRoutingDecisions(
  id: string,
): Promise<AdminRoutingDecision[]> {
  const { data } = await api.get<AdminRoutingDecision[]>(
    `/admin/complaints/${id}/routing-decisions`,
  )
  return data
}

export async function listAdminAiAnalyses(id: string): Promise<AdminAiAnalysesResponse> {
  const { data } = await api.get<AdminAiAnalysesResponse>(`/admin/complaints/${id}/analyses`)
  return data
}

export async function rerouteAdminComplaint(
  id: string,
  payload: ReasonPayload,
): Promise<AdminRoutingDecision> {
  const { data } = await api.post<AdminRoutingDecision>(
    `/admin/complaints/${id}/reroute`,
    payload,
  )
  return data
}

export async function overrideAdminClassification(
  id: string,
  payload: ClassificationOverridePayload,
): Promise<AdminRoutingDecision> {
  const { data } = await api.patch<AdminRoutingDecision>(
    `/admin/complaints/${id}/classification`,
    payload,
  )
  return data
}

export async function assignAdminComplaint(
  id: string,
  payload: AssignComplaintPayload,
): Promise<void> {
  await api.post(`/admin/complaints/${id}/assign`, payload)
}

export async function unassignAdminComplaint(
  id: string,
  payload: ReasonPayload,
): Promise<void> {
  await api.post(`/admin/complaints/${id}/unassign`, payload)
}

export async function listAdminReassignmentRequests(): Promise<AdminReassignmentRequest[]> {
  const { data } = await api.get<AdminReassignmentRequest[]>('/admin/reassignment-requests')
  return data
}

export async function reviewAdminReassignment(
  id: string,
  payload: ReassignmentReviewPayload,
): Promise<void> {
  await api.post(`/admin/reassignment-requests/${id}/review`, payload)
}

// ---------------------------------------------------------------------------
// Reference / service-area oversight
// ---------------------------------------------------------------------------

export async function listAdminCategories(includeInactive = true): Promise<AdminCategory[]> {
  const { data } = await api.get<AdminCategory[]>('/admin/categories', {
    params: { includeInactive },
  })
  return data
}

export async function createAdminCategory(
  payload: CreateCategoryPayload,
): Promise<AdminCategory> {
  const { data } = await api.post<AdminCategory>('/admin/reference/categories', payload)
  return data
}

export async function updateAdminCategory(
  id: string,
  payload: UpdateCategoryPayload,
): Promise<AdminCategory> {
  const { data } = await api.patch<AdminCategory>(`/admin/reference/categories/${id}`, payload)
  return data
}

export async function listAdminJurisdictions(
  includeInactive = true,
): Promise<AdminJurisdiction[]> {
  const { data } = await api.get<AdminJurisdiction[]>('/admin/jurisdictions', {
    params: { includeInactive },
  })
  return data
}

export async function createAdminJurisdiction(
  payload: CreateJurisdictionPayload,
): Promise<AdminJurisdiction> {
  const { data } = await api.post<AdminJurisdiction>('/admin/reference/jurisdictions', payload)
  return data
}

export async function listAdminServiceAreas(
  query: ServiceAreaQuery = {},
): Promise<AdminServiceArea[]> {
  const { data } = await api.get<AdminServiceArea[]>('/admin/service-areas', {
    params: query,
  })
  return data
}

// ---------------------------------------------------------------------------
// Audit exploration
// ---------------------------------------------------------------------------

export async function listAdminAuditLogs(
  query: AuditLogQuery = {},
): Promise<PagedResponse<AdminAuditEntry>> {
  const { data } = await api.get<PagedResponse<AdminAuditEntry>>('/admin/audit-logs', {
    params: query,
  })
  return data
}
