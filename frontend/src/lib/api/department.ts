import { api, type VerificationStatus } from './client'
import type {
  ComplaintDetail,
  ComplaintStatus,
  ComplaintSummary,
  ImageKind,
  Severity,
} from './citizen'
import { confirmUpload, discardUpload, presignUpload, putObject } from './uploads'

/** Reporting window supported by the department analytics endpoints. */
export type PeriodDays = 7 | 30 | 90

export interface DepartmentContext {
  departmentId: string
  departmentName: string
  departmentCode: string
  verificationStatus: VerificationStatus
  acceptsComplaints: boolean
}

export interface CategoryCount {
  code: string
  name: string
  count: number
}

export interface WardCount {
  ward: string
  count: number
}

export interface TrendPoint {
  period: string
  received: number
  resolved: number
}

export interface DepartmentAnalytics {
  total: number
  byStatus: Record<string, number>
  resolved: number
  overdue: number
  unassigned: number
  averageResolutionHours: number | null
  periodDays: PeriodDays
  receivedInPeriod: number
  resolvedInPeriod: number
  pending: number
  accepted: number
  inProgress: number
  rejected: number
  closed: number
  byCategory: CategoryCount[]
  byWard: WardCount[]
  trend: TrendPoint[]
  averageOpenAgeHours: number | null
  slaCompliancePercent: number | null
}

export interface ActivityEntry {
  complaintId: string
  complaintNumber: string
  fromStatus: ComplaintStatus
  toStatus: ComplaintStatus
  actorRole: string | null
  reason: string | null
  createdAt: string
}

export interface DepartmentOverview {
  context: DepartmentContext
  analytics: DepartmentAnalytics
  activity: ActivityEntry[]
}

export interface NoteResponse {
  id: string
  authorName: string
  body: string
  createdAt: string
}

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** Queue rows use the citizen summary shape plus persistence metadata. */
export type DepartmentComplaintSummary = ComplaintSummary

export interface RoutingDecision {
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

export interface DepartmentComplaintDetail extends ComplaintDetail {
  resolutionSummary?: string | null
  internalNotes?: NoteResponse[]
  routingDecisions?: RoutingDecision[]
}

export interface DepartmentComplaintQuery {
  page?: number
  size?: number
  sort?: string
  status?: ComplaintStatus
  /** Repeated `status` query params are supported by the queue endpoint. */
  statuses?: ComplaintStatus[]
  categoryCode?: string
  ward?: string
  zone?: string
  severity?: Severity
  search?: string
  overdue?: boolean
  from?: string
  to?: string
}

export interface DepartmentProfile {
  id: string
  name: string
  departmentCode: string
  departmentType: string
  description: string | null
  officialEmail: string
  contactNumber: string | null
  officeAddress: string | null
  city: string | null
  state: string | null
  country: string | null
  verificationStatus: VerificationStatus
  active: boolean
  acceptsComplaints: boolean
  autoAssignEnabled: boolean
  routingPriority: number
  maxOpenComplaints: number | null
}

export interface DepartmentProfileUpdate {
  description?: string
  contactNumber?: string
  officeAddress?: string
  acceptsComplaints: boolean
  autoAssignEnabled: boolean
}

export interface CapabilityResponse {
  categoryId: string
  categoryCode: string
  categoryName: string
  primary: boolean
  priority: number
}

export interface CapabilityUpdate {
  categoryId: string
  primary: boolean
  priority: number
}

export type AdminLevel = 'OWNER' | 'ADMIN' | 'OPERATOR'

export interface TeamMemberResponse {
  userId: string
  fullName: string
  email: string
  adminLevel: AdminLevel
  primaryAdmin: boolean
  title: string | null
  accountStatus: string
}

export interface TeamMemberUpdate {
  adminLevel: AdminLevel
  primaryAdmin: boolean
}

export interface ServiceAreaResponse {
  id: string
  departmentId: string
  name: string
  coversWard: string | null
  coversZone: string | null
  jurisdictionCode: string | null
  active: boolean
  createdAt: string
}

export interface ServiceAreaCreate {
  name: string
  boundaryWkt: string
  coversWard?: string
  coversZone?: string
  jurisdictionId?: string
}

export interface ServiceAreaUpdate {
  name: string
  boundaryWkt: string
  coversWard?: string
  coversZone?: string
}

export interface EvidenceAttached {
  status: 'ATTACHED'
  imageId: string
}

/** Image kinds the department evidence gate accepts. */
export type DepartmentEvidenceKind = Extract<ImageKind, 'BEFORE' | 'AFTER' | 'EVIDENCE'>

export async function getDepartmentMe(): Promise<DepartmentContext> {
  const { data } = await api.get<DepartmentContext>('/department/me')
  return data
}

export async function getDepartmentOverview(periodDays: PeriodDays): Promise<DepartmentOverview> {
  const { data } = await api.get<DepartmentOverview>('/department/overview', {
    params: { periodDays },
  })
  return data
}

export async function getDepartmentAnalytics(periodDays: PeriodDays): Promise<DepartmentAnalytics> {
  const { data } = await api.get<DepartmentAnalytics>('/department/analytics', {
    params: { periodDays },
  })
  return data
}

export async function getDepartmentActivity(limit = 20): Promise<ActivityEntry[]> {
  const { data } = await api.get<ActivityEntry[]>('/department/activity', { params: { limit } })
  return data
}

export async function listDepartmentComplaints(
  query: DepartmentComplaintQuery = {},
): Promise<PagedResponse<DepartmentComplaintSummary>> {
  const { status, statuses, ...rest } = query
  const { data } = await api.get<PagedResponse<DepartmentComplaintSummary>>(
    '/department/complaints/queue',
    {
      params: {
        ...rest,
        status: statuses?.length ? statuses : status,
      },
      // Keep repeated status values as status=A&status=B rather than status[]=A.
      paramsSerializer: { indexes: null },
    },
  )
  return data
}

export async function getDepartmentComplaint(id: string): Promise<DepartmentComplaintDetail> {
  const { data } = await api.get<DepartmentComplaintDetail>(`/department/complaints/${id}`)
  return data
}

export async function acceptDepartmentComplaint(id: string): Promise<void> {
  await api.post(`/department/complaints/${id}/accept`)
}

export async function rejectDepartmentComplaint(id: string, reason: string): Promise<void> {
  await api.post(`/department/complaints/${id}/reject`, { reason })
}

export interface ComplaintStatusUpdate {
  status: ComplaintStatus
  reason?: string
}

export async function updateDepartmentComplaintStatus(
  id: string,
  payload: ComplaintStatusUpdate,
): Promise<void> {
  await api.patch(`/department/complaints/${id}/status`, payload)
}

export async function requestDepartmentComplaintInfo(id: string, body: string): Promise<void> {
  await api.post(`/department/complaints/${id}/request-info`, { body })
}

export async function listDepartmentNotes(id: string): Promise<NoteResponse[]> {
  const { data } = await api.get<NoteResponse[]>(`/department/complaints/${id}/notes`)
  return data
}

export async function addDepartmentNote(id: string, body: string): Promise<NoteResponse> {
  const { data } = await api.post<NoteResponse>(`/department/complaints/${id}/notes`, { body })
  return data
}

export async function attachDepartmentEvidence(
  id: string,
  imageId: string,
): Promise<EvidenceAttached> {
  const { data } = await api.post<EvidenceAttached>(`/department/complaints/${id}/evidence`, {
    imageId,
  })
  return data
}

/**
 * Uses the existing secure upload lifecycle: presign → direct PUT → confirm →
 * department-scoped attach. If attach fails after confirmation, the staged
 * image is discarded so an orphan is not left behind.
 */
export async function uploadDepartmentEvidence(
  complaintId: string,
  file: File,
  imageKind: DepartmentEvidenceKind,
): Promise<EvidenceAttached> {
  const presigned = await presignUpload({
    fileName: file.name,
    contentType: file.type,
    sizeBytes: file.size,
    imageKind,
  })
  try {
    await putObject(presigned.uploadUrl, file, presigned.contentType, presigned.headers)
    await confirmUpload(presigned.imageId)
    return await attachDepartmentEvidence(complaintId, presigned.imageId)
  } catch (error) {
    await discardUpload(presigned.imageId).catch(() => undefined)
    throw error
  }
}

export async function resolveDepartmentComplaint(id: string, summary: string): Promise<void> {
  await api.post(`/department/complaints/${id}/resolve`, { summary })
}

export interface ReassignRequestPayload {
  toDepartmentId: string
  reason: string
}

export async function requestDepartmentReassignment(
  id: string,
  payload: ReassignRequestPayload,
): Promise<void> {
  await api.post(`/department/complaints/${id}/reassign-request`, payload)
}

export async function getDepartmentProfile(): Promise<DepartmentProfile> {
  const { data } = await api.get<DepartmentProfile>('/department/profile')
  return data
}

export async function updateDepartmentProfile(
  payload: DepartmentProfileUpdate,
): Promise<DepartmentProfile> {
  const { data } = await api.patch<DepartmentProfile>('/department/profile', payload)
  return data
}

export async function listCapabilities(): Promise<CapabilityResponse[]> {
  const { data } = await api.get<CapabilityResponse[]>('/department/capabilities')
  return data
}

export async function updateCapabilities(
  capabilities: CapabilityUpdate[],
): Promise<CapabilityResponse[]> {
  const { data } = await api.put<CapabilityResponse[]>('/department/capabilities', { capabilities })
  return data
}

export async function listTeamMembers(): Promise<TeamMemberResponse[]> {
  const { data } = await api.get<TeamMemberResponse[]>('/department/team')
  return data
}

export async function updateTeamMember(
  userId: string,
  payload: TeamMemberUpdate,
): Promise<TeamMemberResponse[]> {
  const { data } = await api.patch<TeamMemberResponse[]>(`/department/team/${userId}`, payload)
  return data
}

export async function listServiceAreas(): Promise<ServiceAreaResponse[]> {
  const { data } = await api.get<ServiceAreaResponse[]>('/department/service-areas')
  return data
}

export async function createServiceArea(payload: ServiceAreaCreate): Promise<ServiceAreaResponse> {
  const { data } = await api.post<ServiceAreaResponse>('/department/service-areas', payload)
  return data
}

export async function updateServiceArea(
  id: string,
  payload: ServiceAreaUpdate,
): Promise<ServiceAreaResponse> {
  const { data } = await api.patch<ServiceAreaResponse>(`/department/service-areas/${id}`, payload)
  return data
}

export async function deleteServiceArea(id: string): Promise<void> {
  await api.delete(`/department/service-areas/${id}`)
}
