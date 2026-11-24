import { api } from './client'

export type ComplaintStatus =
  | 'SUBMITTED'
  | 'AI_ANALYSIS_PENDING'
  | 'AI_ANALYSIS_COMPLETED'
  | 'AI_ANALYSIS_FAILED'
  | 'PENDING_ASSIGNMENT'
  | 'UNASSIGNED'
  | 'ASSIGNED'
  | 'ACKNOWLEDGED'
  | 'IN_PROGRESS'
  | 'ON_HOLD'
  | 'RESOLVED'
  | 'REOPENED'
  | 'REJECTED'
  | 'CLOSED'

export type LocationSource = 'GPS' | 'MANUAL' | 'GEOCODED'
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type ImageKind = 'CITIZEN' | 'BEFORE' | 'AFTER' | 'EVIDENCE'
export type UploadStatus = 'PENDING' | 'VERIFIED' | 'REJECTED'
export type AiAnalysisStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'COMPLETED' | 'FAILED' | 'TIMEOUT' | 'INVALID_SCHEMA'

export interface Category {
  id: string
  code: string
  name: string
  description: string | null
  icon: string | null
  color: string | null
  displayOrder: number
  active: boolean
  children: Category[]
}

export interface ComplaintPhoto {
  id: string
  storageKey: string
  contentType: string
  sizeBytes: number | null
  imageKind: ImageKind
  uploadStatus: UploadStatus
  createdAt: string
}

export interface AiAnalysisResponse {
  id: string
  status: AiAnalysisStatus
  isCivicIssue: boolean | null
  categoryCode: string | null
  subcategoryCode: string | null
  severity: Severity | null
  description: string | null
  safetyRisk: boolean | null
  visibleDamage: string | null
  isBlurry: boolean | null
  requiresAdditionalInfo: boolean | null
  confidence: number | null
  recommendedDepartmentHint: string | null
  requiresHumanReview: boolean
  errorMessage: string | null
  createdAt: string
}

export interface ComplaintSummary {
  id: string
  complaintNumber: string
  description: string
  status: ComplaintStatus
  severity: Severity
  categoryCode: string | null
  ward: string | null
  city: string | null
  departmentId: string | null
  createdAt: string
}

export interface StatusEntry {
  fromStatus: ComplaintStatus | null
  toStatus: ComplaintStatus
  actorRole: string | null
  reason: string | null
  createdAt: string
}

export interface CommentEntry {
  id: string
  authorName: string
  authorRole: string
  body: string
  createdAt: string
}

export interface AssignmentEntry {
  departmentId: string
  departmentName: string
  assignmentType: string
  active: boolean
  assignedAt: string
  unassignedAt: string | null
}

export interface FeedbackEntry {
  rating: number
  comment: string | null
  resolutionConfirmed: boolean
  disputeReason: string | null
  createdAt: string
}

export interface ComplaintDetail extends ComplaintSummary {
  additionalInfo: string | null
  subcategoryCode: string | null
  addressLine: string | null
  state: string | null
  postalCode: string | null
  country: string | null
  zone: string | null
  locationSource: LocationSource
  latitude: number
  longitude: number
  departmentId: string | null
  departmentName: string | null
  assignedAt: string | null
  resolvedAt: string | null
  resolutionDueAt: string | null
  timeline: StatusEntry[]
  comments: CommentEntry[]
  assignments: AssignmentEntry[]
  feedback: FeedbackEntry | null
  /** Backend-confirmed images only. Never populated from local file selections. */
  images: ComplaintPhoto[]
  analysis: AiAnalysisResponse | null
}

export interface SubmitComplaintPayload {
  description: string
  additionalInfo?: string
  latitude: number
  longitude: number
  locationSource: LocationSource
  addressLine?: string
  city?: string
  state?: string
  postalCode?: string
  country?: string
  ward?: string
  zone?: string
  categoryCode?: string
  subcategoryCode?: string
  imageIds?: string[]
}

export interface ComplaintFilters {
  status?: ComplaintStatus
  categoryId?: string
  from?: string
  to?: string
  search?: string
}

export interface NotificationEntry {
  id: string
  type: string
  title: string
  body: string
  channel: string
  deliveryStatus?: 'PENDING' | 'SENT' | 'FAILED' | 'READ'
  complaintNumber?: string | null
  readAt: string | null
  createdAt: string
  sentAt?: string | null
  complaintId: string | null
}

export async function listCategories(includeInactive = false): Promise<Category[]> {
  const { data } = await api.get<Category[]>('/meta/categories', {
    params: { includeInactive },
  })
  return data
}

export async function submitComplaint(payload: SubmitComplaintPayload): Promise<ComplaintDetail> {
  const { data } = await api.post<ComplaintDetail>('/citizen/complaints', payload)
  return data
}

export async function listMyComplaints(filters: ComplaintFilters = {}): Promise<ComplaintSummary[]> {
  const { data } = await api.get<ComplaintSummary[]>('/citizen/complaints', {
    params: {
      status: filters.status,
      categoryId: filters.categoryId,
      from: filters.from,
      to: filters.to,
      search: filters.search,
    },
  })
  return data
}

export async function getMyComplaint(id: string): Promise<ComplaintDetail> {
  const { data } = await api.get<ComplaintDetail>(`/citizen/complaints/${id}`)
  return data
}

export async function getTimeline(id: string): Promise<StatusEntry[]> {
  const { data } = await api.get<StatusEntry[]>(`/citizen/complaints/${id}/timeline`)
  return data
}

export async function listComments(id: string): Promise<CommentEntry[]> {
  const { data } = await api.get<CommentEntry[]>(`/citizen/complaints/${id}/comments`)
  return data
}

export async function addComment(id: string, body: string): Promise<CommentEntry> {
  const { data } = await api.post<CommentEntry>(`/citizen/complaints/${id}/comments`, { body })
  return data
}

export async function respondToInfoRequest(id: string, body: string): Promise<void> {
  await api.post(`/citizen/complaints/${id}/respond-info`, { body })
}

export async function reopenComplaint(id: string, reason: string): Promise<void> {
  await api.post(`/citizen/complaints/${id}/reopen`, { reason })
}

export interface FeedbackPayload {
  rating: number
  comment?: string
  resolutionConfirmed: boolean
  disputeReason?: string
}

export async function submitFeedback(id: string, payload: FeedbackPayload): Promise<void> {
  await api.post(`/citizen/complaints/${id}/feedback`, payload)
}

export async function listNotifications(): Promise<NotificationEntry[]> {
  const { data } = await api.get<NotificationEntry[]>('/notifications')
  return data
}

export async function getUnreadNotificationCount(): Promise<number> {
  const { data } = await api.get<{ count: number }>('/notifications/unread-count')
  return data.count
}

export async function markNotificationRead(id: string): Promise<void> {
  await api.patch(`/notifications/${id}/read`)
}

export async function markAllNotificationsRead(): Promise<void> {
  await api.post('/notifications/read-all')
}
