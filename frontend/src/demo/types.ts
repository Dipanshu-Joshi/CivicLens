import type {
  Role,
  VerificationStatus,
} from '../lib/api/client'
import type {
  AiAnalysisResponse,
  AssignmentEntry,
  Category,
  CommentEntry,
  ComplaintDetail,
  ComplaintPhoto,
  FeedbackEntry,
  StatusEntry,
  UploadStatus,
} from '../lib/api/citizen'
import type {
  AdminAuditEntry,
  AdminJurisdiction,
  AdminReassignmentRequest,
  AdminRoutingDecision,
  AdminServiceArea,
  AiAnalysisEntry,
} from '../lib/api/admin'
import type {
  AdminLevel,
  CapabilityResponse,
  NoteResponse,
  ServiceAreaResponse,
} from '../lib/api/department'

export type DemoUserStatus = 'ACTIVE' | 'PENDING_VERIFICATION' | 'SUSPENDED' | 'DISABLED'

export interface DemoUser {
  id: string
  email: string
  fullName: string
  password: string
  phone: string | null
  role: Role
  accountStatus: DemoUserStatus
  verificationStatus: VerificationStatus | null
  departmentId: string | null
  permissions: string[]
  createdAt: string
  adminLevel: AdminLevel
  primaryAdmin: boolean
  title: string | null
}

export interface DemoDepartment {
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
  isActive: boolean
  acceptsComplaints: boolean
  verifiedAt: string | null
  autoAssignEnabled: boolean
  routingPriority: number
  maxOpenComplaints: number | null
  createdAt: string
  capabilities: CapabilityResponse[]
  serviceAreas: ServiceAreaResponse[]
}

export interface DemoComplaint extends ComplaintDetail {
  ownerId: string
  resolutionSummary: string | null
  internalNotes: NoteResponse[]
  routingDecisions: AdminRoutingDecision[]
  aiHistory: AiAnalysisEntry[]
}

export interface DemoNotification {
  id: string
  userId: string
  type: string
  title: string
  body: string
  channel: string
  deliveryStatus: 'PENDING' | 'SENT' | 'FAILED' | 'READ'
  complaintNumber: string | null
  complaintId: string | null
  readAt: string | null
  createdAt: string
  sentAt: string | null
}

export interface DemoUploadedImage {
  imageId: string
  storageKey: string
  contentType: string
  sizeBytes: number
  imageKind: ComplaintPhoto['imageKind']
  complaintId: string | null
  createdAt: string
  uploadStatus: UploadStatus
  previewDataUrl: string | null
}

export interface DemoState {
  version: number
  users: DemoUser[]
  departments: DemoDepartment[]
  complaints: DemoComplaint[]
  notifications: DemoNotification[]
  audit: AdminAuditEntry[]
  categories: Category[]
  jurisdictions: AdminJurisdiction[]
  serviceAreas: AdminServiceArea[]
  reassignmentRequests: AdminReassignmentRequest[]
  images: DemoUploadedImage[]
  counters: {
    complaint: number
    notification: number
    audit: number
    image: number
    category: number
    jurisdiction: number
    reassignment: number
  }
  updatedAt: string
}

export interface DemoSession {
  userId: string
  email: string
  role: Role
  loginAt: string
  token: string
}

export type DemoStatusEntry = StatusEntry
export type DemoCommentEntry = CommentEntry
export type DemoAssignmentEntry = AssignmentEntry
export type DemoFeedbackEntry = FeedbackEntry
export type DemoAiAnalysis = AiAnalysisResponse
