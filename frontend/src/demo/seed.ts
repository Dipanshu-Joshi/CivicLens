import type { MeResponse, Role, VerificationStatus } from '../lib/api/client'
import type {
  AiAnalysisResponse,
  AiAnalysisStatus,
  Category,
  CommentEntry,
  ComplaintPhoto,
  ComplaintStatus,
  FeedbackEntry,
  Severity,
  StatusEntry,
} from '../lib/api/citizen'
import type {
  AdminAuditEntry,
  AdminJurisdiction,
  AdminReassignmentRequest,
  AdminRoutingDecision,
  AdminServiceArea,
  AiAnalysisEntry,
  RoutingCandidate,
} from '../lib/api/admin'
import type { NoteResponse, ServiceAreaResponse } from '../lib/api/department'
import type {
  DemoComplaint,
  DemoDepartment,
  DemoNotification,
  DemoState,
  DemoUploadedImage,
  DemoUser,
} from './types'
import { DEMO_STORAGE_VERSION } from './feature'

const HOUR = 60 * 60 * 1000

function hoursAgo(hours: number, from = Date.now()): string {
  return new Date(from - hours * HOUR).toISOString()
}

function hoursFromNow(hours: number, from = Date.now()): string {
  return new Date(from + hours * HOUR).toISOString()
}

function category(
  id: string,
  code: string,
  name: string,
  description: string,
  icon: string,
  color: string,
  displayOrder: number,
  children: Category[] = [],
): Category {
  return {
    id,
    code,
    name,
    description,
    icon,
    color,
    displayOrder,
    active: true,
    children,
  }
}

const CATEGORIES: Category[] = [
  category('cat-road', 'ROAD', 'Roads & footpaths', 'Potholes, damaged roads, footpaths and dividers.', 'road', '#b45309', 1, [
    category('cat-pothole', 'POTHOLE', 'Potholes', 'Open potholes and broken road surfaces.', 'road', '#b45309', 1),
    category('cat-divider', 'DIVIDER', 'Dividers & kerbs', 'Damaged median, kerb or barrier.', 'road', '#b45309', 2),
    category('cat-marking', 'MARKING', 'Road markings', 'Faded or missing road markings.', 'road', '#b45309', 3),
  ]),
  category('cat-water', 'WATER', 'Water & drainage', 'Supply, leakage, drains and flooding.', 'water', '#0369a1', 2, [
    category('cat-leak', 'LEAK', 'Pipeline leakage', 'Burst pipes and leaking supply lines.', 'water', '#0369a1', 1),
    category('cat-drain', 'DRAIN', 'Drainage & flooding', 'Blocked drains and standing water.', 'water', '#0369a1', 2),
  ]),
  category('cat-waste', 'WASTE', 'Waste & sanitation', 'Garbage collection, dumping and public toilets.', 'waste', '#15803d', 3, [
    category('cat-dump', 'DUMP', 'Illegal dumping', 'Unauthorised waste piles.', 'waste', '#15803d', 1),
    category('cat-collection', 'COLLECTION', 'Missed collection', 'Uncollected household or street waste.', 'waste', '#15803d', 2),
  ]),
  category('cat-light', 'LIGHT', 'Street lighting', 'Outages, flicker and damaged poles.', 'light', '#a16207', 4, [
    category('cat-outage', 'OUTAGE', 'Light outage', 'Streetlight not working.', 'light', '#a16207', 1),
    category('cat-pole', 'POLE', 'Damaged pole', 'Leaning or broken lighting pole.', 'light', '#a16207', 2),
  ]),
  category('cat-park', 'PARK', 'Parks & public spaces', 'Playgrounds, trees, fencing and public amenities.', 'park', '#047857', 5, [
    category('cat-tree', 'TREE', 'Trees & foliage', 'Fallen, hazardous or overgrown trees.', 'park', '#047857', 1),
    category('cat-fence', 'FENCE', 'Fencing & furniture', 'Broken park fencing or furniture.', 'park', '#047857', 2),
  ]),
]

const DEPARTMENT_SEEDS: Array<Omit<DemoDepartment, 'capabilities' | 'serviceAreas'>> = [
  {
    id: 'dept-roads',
    name: 'Road Maintenance & Transport',
    departmentCode: 'RMT-BPL',
    departmentType: 'INFRASTRUCTURE',
    description: 'Road surfaces, footpaths, dividers and traffic calming infrastructure.',
    officialEmail: 'roads@bhopal.demo',
    contactNumber: '0755-2220101',
    officeAddress: 'Public Works Compound, TT Nagar',
    city: 'Bhopal',
    state: 'Madhya Pradesh',
    country: 'India',
    verificationStatus: 'VERIFIED',
    isActive: true,
    acceptsComplaints: true,
    verifiedAt: hoursAgo(24 * 90),
    autoAssignEnabled: true,
    routingPriority: 80,
    maxOpenComplaints: 60,
    createdAt: hoursAgo(24 * 180),
  },
  {
    id: 'dept-water',
    name: 'Water Supply & Drainage',
    departmentCode: 'WSD-BPL',
    departmentType: 'UTILITY',
    description: 'Potable water distribution, storm drainage and flood response.',
    officialEmail: 'water@bhopal.demo',
    contactNumber: '0755-2220102',
    officeAddress: 'Water Works House, MP Nagar',
    city: 'Bhopal',
    state: 'Madhya Pradesh',
    country: 'India',
    verificationStatus: 'VERIFIED',
    isActive: true,
    acceptsComplaints: true,
    verifiedAt: hoursAgo(24 * 80),
    autoAssignEnabled: true,
    routingPriority: 70,
    maxOpenComplaints: 50,
    createdAt: hoursAgo(24 * 170),
  },
  {
    id: 'dept-sanitation',
    name: 'Solid Waste & Sanitation',
    departmentCode: 'SWS-BPL',
    departmentType: 'SANITATION',
    description: 'Door-to-door collection, illegal dumping remediation and public sanitation.',
    officialEmail: 'sanitation@bhopal.demo',
    contactNumber: '0755-2220103',
    officeAddress: 'Sanitation Yard, Govindpuri',
    city: 'Bhopal',
    state: 'Madhya Pradesh',
    country: 'India',
    verificationStatus: 'VERIFIED',
    isActive: true,
    acceptsComplaints: true,
    verifiedAt: hoursAgo(24 * 70),
    autoAssignEnabled: true,
    routingPriority: 60,
    maxOpenComplaints: 45,
    createdAt: hoursAgo(24 * 160),
  },
  {
    id: 'dept-power',
    name: 'Public Lighting & Electrical',
    departmentCode: 'PLE-BPL',
    departmentType: 'UTILITY',
    description: 'Street lighting, electrical safety and transformer maintenance.',
    officialEmail: 'lighting@bhopal.demo',
    contactNumber: '0755-2220104',
    officeAddress: 'Electrical Substation, Shahpura',
    city: 'Bhopal',
    state: 'Madhya Pradesh',
    country: 'India',
    verificationStatus: 'SUSPENDED',
    isActive: false,
    acceptsComplaints: false,
    verifiedAt: hoursAgo(24 * 60),
    autoAssignEnabled: false,
    routingPriority: 40,
    maxOpenComplaints: 25,
    createdAt: hoursAgo(24 * 150),
  },
  {
    id: 'dept-parks',
    name: 'Parks, Gardens & Public Spaces',
    departmentCode: 'PGP-BPL',
    departmentType: 'AMENITY',
    description: 'Parks, playgrounds, trees and public-space furniture.',
    officialEmail: 'parks@bhopal.demo',
    contactNumber: '0755-2220105',
    officeAddress: 'Horticulture Office, Arera Hills',
    city: 'Bhopal',
    state: 'Madhya Pradesh',
    country: 'India',
    verificationStatus: 'PENDING_VERIFICATION',
    isActive: true,
    acceptsComplaints: false,
    verifiedAt: null,
    autoAssignEnabled: false,
    routingPriority: 30,
    maxOpenComplaints: 20,
    createdAt: hoursAgo(24 * 12),
  },
]

function departmentCapabilities(departmentId: string): DemoDepartment['capabilities'] {
  const codesByDepartment: Record<string, string[]> = {
    'dept-roads': ['cat-pothole', 'cat-divider', 'cat-marking'],
    'dept-water': ['cat-leak', 'cat-drain'],
    'dept-sanitation': ['cat-dump', 'cat-collection'],
    'dept-power': ['cat-outage', 'cat-pole'],
    'dept-parks': ['cat-tree', 'cat-fence'],
  }
  const flat = CATEGORIES.flatMap((parent) => parent.children)
  return (codesByDepartment[departmentId] ?? []).map((code, index) => {
    const match = flat.find((item) => item.id === code)!
    return {
      categoryId: match.id,
      categoryCode: match.code,
      categoryName: match.name,
      primary: index === 0,
      priority: 10 - index,
    }
  })
}

function departmentServiceAreas(department: Omit<DemoDepartment, 'capabilities' | 'serviceAreas'>, index: number): ServiceAreaResponse[] {
  return [
    {
      id: `area-${department.id}-a`,
      departmentId: department.id,
      name: `${department.name} · Central`,
      coversWard: 'WARD-12',
      coversZone: 'ZONE-A',
      jurisdictionCode: 'BHOPAL-CITY',
      active: true,
      createdAt: hoursAgo(24 * (100 - index * 5)),
    },
    {
      id: `area-${department.id}-b`,
      departmentId: department.id,
      name: `${department.name} · South`,
      coversWard: 'WARD-18',
      coversZone: 'ZONE-B',
      jurisdictionCode: 'BHOPAL-CITY',
      active: department.isActive,
      createdAt: hoursAgo(24 * (90 - index * 5)),
    },
  ]
}

const USER_SEEDS: DemoUser[] = [
  {
    id: 'user-citizen-1',
    email: 'citizen@demo.com',
    fullName: 'Aarav Sharma',
    password: 'Demo@123',
    phone: '9876500001',
    role: 'CITIZEN',
    accountStatus: 'ACTIVE',
    verificationStatus: null,
    departmentId: null,
    permissions: ['complaint:create', 'complaint:read:own', 'feedback:submit'],
    createdAt: hoursAgo(24 * 120),
    adminLevel: 'OPERATOR',
    primaryAdmin: false,
    title: null,
  },
  {
    id: 'user-citizen-2',
    email: 'priya@example.com',
    fullName: 'Priya Nair',
    password: 'Demo@123',
    phone: '9876500002',
    role: 'CITIZEN',
    accountStatus: 'ACTIVE',
    verificationStatus: null,
    departmentId: null,
    permissions: ['complaint:create', 'complaint:read:own'],
    createdAt: hoursAgo(24 * 100),
    adminLevel: 'OPERATOR',
    primaryAdmin: false,
    title: null,
  },
  {
    id: 'user-citizen-3',
    email: 'rohan@example.com',
    fullName: 'Rohan Verma',
    password: 'Demo@123',
    phone: '9876500003',
    role: 'CITIZEN',
    accountStatus: 'ACTIVE',
    verificationStatus: null,
    departmentId: null,
    permissions: ['complaint:create', 'complaint:read:own'],
    createdAt: hoursAgo(24 * 90),
    adminLevel: 'OPERATOR',
    primaryAdmin: false,
    title: null,
  },
  {
    id: 'user-dept-roads',
    email: 'department@demo.com',
    fullName: 'Meera Iyer',
    password: 'Demo@123',
    phone: '9876500010',
    role: 'DEPARTMENT_ADMIN',
    accountStatus: 'ACTIVE',
    verificationStatus: 'VERIFIED',
    departmentId: 'dept-roads',
    permissions: ['department:queue', 'department:update', 'department:profile'],
    createdAt: hoursAgo(24 * 110),
    adminLevel: 'OWNER',
    primaryAdmin: true,
    title: 'Executive Engineer',
  },
  {
    id: 'user-dept-water',
    email: 'water.admin@example.com',
    fullName: 'Sanjay Patel',
    password: 'Demo@123',
    phone: '9876500011',
    role: 'DEPARTMENT_ADMIN',
    accountStatus: 'ACTIVE',
    verificationStatus: 'VERIFIED',
    departmentId: 'dept-water',
    permissions: ['department:queue', 'department:update'],
    createdAt: hoursAgo(24 * 105),
    adminLevel: 'ADMIN',
    primaryAdmin: true,
    title: 'Water Works Officer',
  },
  {
    id: 'user-dept-sanitation',
    email: 'sanitation.admin@example.com',
    fullName: 'Kavita Joshi',
    password: 'Demo@123',
    phone: '9876500012',
    role: 'DEPARTMENT_ADMIN',
    accountStatus: 'ACTIVE',
    verificationStatus: 'VERIFIED',
    departmentId: 'dept-sanitation',
    permissions: ['department:queue', 'department:update'],
    createdAt: hoursAgo(24 * 95),
    adminLevel: 'ADMIN',
    primaryAdmin: true,
    title: 'Sanitation Officer',
  },
  {
    id: 'user-dept-power',
    email: 'power.admin@example.com',
    fullName: 'Arjun Rao',
    password: 'Demo@123',
    phone: '9876500013',
    role: 'DEPARTMENT_ADMIN',
    accountStatus: 'SUSPENDED',
    verificationStatus: 'SUSPENDED',
    departmentId: 'dept-power',
    permissions: [],
    createdAt: hoursAgo(24 * 88),
    adminLevel: 'OWNER',
    primaryAdmin: true,
    title: 'Electrical Engineer',
  },
  {
    id: 'user-dept-parks',
    email: 'parks.admin@example.com',
    fullName: 'Neha Bhatt',
    password: 'Demo@123',
    phone: '9876500014',
    role: 'DEPARTMENT_ADMIN',
    accountStatus: 'PENDING_VERIFICATION',
    verificationStatus: 'PENDING_VERIFICATION',
    departmentId: 'dept-parks',
    permissions: [],
    createdAt: hoursAgo(24 * 11),
    adminLevel: 'OWNER',
    primaryAdmin: true,
    title: 'Horticulture Officer',
  },
  {
    id: 'user-admin-1',
    email: 'admin@demo.com',
    fullName: 'Vikram Sinha',
    password: 'Demo@123',
    phone: '9876500020',
    role: 'SUPER_ADMIN',
    accountStatus: 'ACTIVE',
    verificationStatus: null,
    departmentId: null,
    permissions: ['admin:all', 'audit:read', 'reference:manage'],
    createdAt: hoursAgo(24 * 200),
    adminLevel: 'OWNER',
    primaryAdmin: false,
    title: 'Platform Administrator',
  },
  {
    id: 'user-admin-2',
    email: 'admin.secondary@example.com',
    fullName: 'Anita Desai',
    password: 'Demo@123',
    phone: '9876500021',
    role: 'SUPER_ADMIN',
    accountStatus: 'ACTIVE',
    verificationStatus: null,
    departmentId: null,
    permissions: ['admin:all', 'audit:read'],
    createdAt: hoursAgo(24 * 190),
    adminLevel: 'OWNER',
    primaryAdmin: false,
    title: 'Deputy Platform Administrator',
  },
]

function simulatedAnalysis(
  id: string,
  createdAt: string,
  status: AiAnalysisStatus,
  categoryCode: string | null,
  severity: Severity | null,
  confidence: number | null,
  extra: Partial<AiAnalysisResponse> = {},
): AiAnalysisResponse {
  const simulated: AiAnalysisResponse = {
    id,
    status,
    isCivicIssue: status === 'FAILED' ? null : true,
    categoryCode,
    subcategoryCode: categoryCode === 'ROAD' ? 'POTHOLE' : null,
    severity,
    description: status === 'FAILED' ? null : `Simulated AI: likely ${categoryCode ?? 'civic'} issue with visible civic impact.`,
    safetyRisk: severity === 'CRITICAL' || severity === 'HIGH',
    visibleDamage: severity === 'CRITICAL' || severity === 'HIGH' ? 'Visible damage reported' : 'Minor visible damage',
    isBlurry: false,
    requiresAdditionalInfo: confidence !== null && confidence < 0.55,
    confidence,
    recommendedDepartmentHint:
      categoryCode === 'ROAD'
        ? 'Road Maintenance & Transport (advisory)'
        : categoryCode === 'WATER'
          ? 'Water Supply & Drainage (advisory)'
          : categoryCode === 'WASTE'
            ? 'Solid Waste & Sanitation (advisory)'
            : categoryCode === 'LIGHT'
              ? 'Public Lighting & Electrical (advisory)'
              : 'Parks, Gardens & Public Spaces (advisory)',
    requiresHumanReview: status === 'FAILED' || (confidence !== null && confidence < 0.6),
    errorMessage: status === 'FAILED' ? 'Simulated analysis timeout' : null,
    createdAt,
  }
  return { ...simulated, ...extra }
}

function analysisEntry(analysis: AiAnalysisResponse): AiAnalysisEntry {
  return {
    id: `ai-${analysis.id}`,
    status: analysis.status,
    source: 'AI',
    isCivicIssue: analysis.isCivicIssue,
    categoryCode: analysis.categoryCode,
    subcategoryCode: analysis.subcategoryCode,
    severity: analysis.severity,
    description: analysis.description,
    confidence: analysis.confidence,
    requiresHumanReview: analysis.requiresHumanReview,
    adminOverrideReason: null,
    actorName: 'Simulated classifier',
    actorRole: 'AI',
    createdAt: analysis.createdAt,
  }
}

function candidates(selectedDepartmentId: string | null, primary: string, secondary: string): RoutingCandidate[] {
  return [
    {
      departmentId: primary.startsWith('dept-') ? primary : `dept-${primary.toLowerCase()}`,
      departmentName: DEPARTMENT_SEEDS.find((department) => department.id === primary)?.name ?? primary,
      geoScore: 0.9,
      geoMatch: 'WARD',
      catScore: 0.85,
      catMatch: 'PRIMARY',
      priority: 80,
      total: 0.88,
      atCapacity: false,
      reasons: ['Ward boundary match', 'Primary category capability'],
    },
    {
      departmentId: secondary.startsWith('dept-') ? secondary : `dept-${secondary.toLowerCase()}`,
      departmentName: DEPARTMENT_SEEDS.find((department) => department.id === secondary)?.name ?? secondary,
      geoScore: 0.6,
      geoMatch: 'ZONE',
      catScore: 0.4,
      catMatch: 'NONE',
      priority: 60,
      total: 0.52,
      atCapacity: false,
      reasons: ['Zone overlap'],
    },
    {
      departmentId: selectedDepartmentId ?? 'none',
      departmentName: selectedDepartmentId
        ? DEPARTMENT_SEEDS.find((department) => department.id === selectedDepartmentId)?.name ?? selectedDepartmentId
        : 'No eligible department',
      geoScore: selectedDepartmentId ? 0.95 : 0,
      geoMatch: selectedDepartmentId ? 'WARD' : 'NONE',
      catScore: selectedDepartmentId ? 0.92 : 0,
      catMatch: selectedDepartmentId ? 'PRIMARY' : 'NONE',
      priority: selectedDepartmentId ? 80 : 0,
      total: selectedDepartmentId ? 0.94 : 0,
      atCapacity: false,
      reasons: selectedDepartmentId ? ['Selected by deterministic routing'] : ['No verified, accepting department matched'],
    },
  ]
}

function routingDecision(
  id: string,
  createdAt: string,
  departmentId: string | null,
  outcome: string,
  reason: string,
  categoryCode: string | null,
  confidence: number | null,
  primary = 'dept-roads',
  secondary = 'dept-water',
): AdminRoutingDecision {
  return {
    id,
    rulesetVersion: 'DEMO-RULES-2026.1',
    aiCategoryCode: categoryCode,
    confidence,
    locationPointWkt: 'POINT (77.4126 23.2599)',
    matchedJurisdictionId: 'jur-bhopal-city',
    candidates: candidates(departmentId, primary, secondary),
    selectedDepartmentId: departmentId,
    outcome,
    reason,
    current: true,
    createdAt,
  }
}

function photo(id: string, imageKind: ComplaintPhoto['imageKind'], createdAt: string): ComplaintPhoto {
  return {
    id,
    storageKey: `demo/${id}.jpg`,
    contentType: 'image/jpeg',
    sizeBytes: 180_000,
    imageKind,
    uploadStatus: 'VERIFIED',
    createdAt,
  }
}

function note(id: string, author: string, body: string, createdAt: string): NoteResponse {
  return { id, authorName: author, body, createdAt }
}

function comment(
  id: string,
  authorName: string,
  authorRole: string,
  body: string,
  createdAt: string,
): CommentEntry {
  return { id, authorName, authorRole, body, createdAt }
}

interface ComplaintSeed {
  id: string
  ownerId: string
  description: string
  additionalInfo?: string | null
  categoryCode: string | null
  subcategoryCode?: string | null
  severity: Severity
  status: ComplaintStatus
  departmentId: string | null
  ward: string
  zone: string
  addressLine: string
  latitude: number
  longitude: number
  hoursAgo: number
  dueInHours: number
  resolvedHoursAgo?: number
  analysis?: AiAnalysisResponse
  comments?: CommentEntry[]
  feedback?: FeedbackEntry | null
  images?: ComplaintPhoto[]
  notes?: NoteResponse[]
  routingOutcome?: string
  resolutionSummary?: string | null
}

function timelineFor(seed: ComplaintSeed, resolvedAt: string | null): StatusEntry[] {
  const created = hoursAgo(seed.hoursAgo)
  const entries: StatusEntry[] = [
    { fromStatus: null, toStatus: 'SUBMITTED', actorRole: 'CITIZEN', reason: 'Simulated citizen submission', createdAt: created },
  ]
  if (seed.analysis) {
    entries.push({
      fromStatus: 'SUBMITTED',
      toStatus: seed.analysis.status === 'FAILED' ? 'AI_ANALYSIS_FAILED' : 'AI_ANALYSIS_COMPLETED',
      actorRole: 'AI',
      reason: 'Simulated AI classification completed',
      createdAt: hoursAgo(seed.hoursAgo - 0.2),
    })
  }
  entries.push({
    fromStatus: entries.at(-1)!.toStatus,
    toStatus: 'PENDING_ASSIGNMENT',
    actorRole: 'SYSTEM',
    reason: 'Routing evaluation started',
    createdAt: hoursAgo(seed.hoursAgo - 0.5),
  })
  if (seed.departmentId) {
    entries.push({
      fromStatus: 'PENDING_ASSIGNMENT',
      toStatus: 'ASSIGNED',
      actorRole: 'SYSTEM',
      reason: 'Deterministic routing selected the owning department',
      createdAt: hoursAgo(seed.hoursAgo - 1),
    })
    if (seed.status === 'ACKNOWLEDGED' || seed.status === 'IN_PROGRESS' || seed.status === 'ON_HOLD' || seed.status === 'RESOLVED' || seed.status === 'CLOSED' || seed.status === 'REOPENED') {
      entries.push({
        fromStatus: 'ASSIGNED',
        toStatus: 'ACKNOWLEDGED',
        actorRole: 'DEPARTMENT_ADMIN',
        reason: 'Complaint accepted by the department',
        createdAt: hoursAgo(Math.max(1, seed.hoursAgo - 3)),
      })
    }
    if (seed.status === 'IN_PROGRESS' || seed.status === 'ON_HOLD' || seed.status === 'RESOLVED' || seed.status === 'CLOSED' || seed.status === 'REOPENED') {
      entries.push({
        fromStatus: 'ACKNOWLEDGED',
        toStatus: 'IN_PROGRESS',
        actorRole: 'DEPARTMENT_ADMIN',
        reason: 'Field work started',
        createdAt: hoursAgo(Math.max(2, seed.hoursAgo - 8)),
      })
    }
    if (seed.status === 'ON_HOLD') {
      entries.push({
        fromStatus: 'IN_PROGRESS',
        toStatus: 'ON_HOLD',
        actorRole: 'DEPARTMENT_ADMIN',
        reason: 'Awaiting material availability',
        createdAt: hoursAgo(Math.max(3, seed.hoursAgo - 20)),
      })
    }
    if (seed.status === 'REOPENED') {
      entries.push({
        fromStatus: 'RESOLVED',
        toStatus: 'REOPENED',
        actorRole: 'CITIZEN',
        reason: 'Citizen reported that the issue remained unresolved',
        createdAt: hoursAgo(Math.max(2, seed.hoursAgo - 36)),
      })
    }
  }
  if ((seed.status === 'RESOLVED' || seed.status === 'CLOSED') && resolvedAt) {
    entries.push({
      fromStatus: entries.at(-1)!.toStatus,
      toStatus: seed.status === 'CLOSED' ? 'CLOSED' : 'RESOLVED',
      actorRole: 'DEPARTMENT_ADMIN',
      reason: seed.resolutionSummary ?? 'Simulated resolution recorded',
      createdAt: resolvedAt,
    })
  }
  return entries
}

function buildComplaint(seed: ComplaintSeed, sequence: number): DemoComplaint {
  const createdAt = hoursAgo(seed.hoursAgo)
  const resolvedAt = seed.resolvedHoursAgo === undefined ? null : hoursAgo(seed.resolvedHoursAgo)
  const department = seed.departmentId
    ? DEPARTMENT_SEEDS.find((item) => item.id === seed.departmentId) ?? null
    : null
  const aiHistory = seed.analysis ? [analysisEntry(seed.analysis)] : []
  const routing = seed.analysis || seed.departmentId
    ? [
        routingDecision(
          `route-${seed.id}`,
          hoursAgo(seed.hoursAgo - 0.6),
          seed.departmentId,
          seed.routingOutcome ?? (seed.departmentId ? 'ASSIGNED' : 'UNASSIGNED'),
          seed.departmentId
            ? 'Simulated deterministic routing matched an active department.'
            : 'No verified and accepting department matched the simulated ruleset.',
          seed.categoryCode,
          seed.analysis?.confidence ?? null,
          seed.categoryCode === 'WATER'
            ? 'dept-water'
            : seed.categoryCode === 'WASTE'
              ? 'dept-sanitation'
              : seed.categoryCode === 'LIGHT'
                ? 'dept-power'
                : seed.categoryCode === 'PARK'
                  ? 'dept-parks'
                  : 'dept-roads',
          seed.categoryCode === 'WATER' ? 'dept-roads' : 'dept-water',
        ),
      ]
    : []
  const images = seed.images ?? []
  return {
    id: seed.id,
    complaintNumber: `CIV-${new Date().getFullYear()}-${String(sequence).padStart(4, '0')}`,
    ownerId: seed.ownerId,
    createdAt,
    description: seed.description,
    additionalInfo: seed.additionalInfo ?? null,
    status: seed.status,
    severity: seed.severity,
    categoryCode: seed.categoryCode,
    subcategoryCode: seed.subcategoryCode ?? null,
    ward: seed.ward,
    city: 'Bhopal',
    state: 'Madhya Pradesh',
    postalCode: '462001',
    country: 'India',
    zone: seed.zone,
    addressLine: seed.addressLine,
    locationSource: 'MANUAL',
    latitude: seed.latitude,
    longitude: seed.longitude,
    departmentId: seed.departmentId,
    departmentName: department?.name ?? null,
    assignedAt: seed.departmentId ? hoursAgo(seed.hoursAgo - 1) : null,
    resolvedAt,
    resolutionDueAt: hoursFromNow(seed.dueInHours),
    timeline: timelineFor(seed, resolvedAt),
    comments: seed.comments ?? [],
    assignments: seed.departmentId
      ? [
          {
            departmentId: seed.departmentId,
            departmentName: department?.name ?? seed.departmentId,
            assignmentType: 'AUTO',
            active: true,
            assignedAt: hoursAgo(seed.hoursAgo - 1),
            unassignedAt: null,
          },
        ]
      : [],
    feedback: seed.feedback ?? null,
    images,
    analysis: seed.analysis ?? null,
    resolutionSummary: seed.resolutionSummary ?? null,
    internalNotes: seed.notes ?? [],
    routingDecisions: routing,
    aiHistory,
  }
}

const COMPLAINT_SEEDS: ComplaintSeed[] = [
  {
    id: 'complaint-101',
    ownerId: 'user-citizen-1',
    description: 'A large pothole near the school gate is causing traffic and safety issues.',
    additionalInfo: 'Widening after yesterday\'s rain. Two-wheelers are swerving into oncoming traffic.',
    categoryCode: 'ROAD',
    subcategoryCode: 'POTHOLE',
    severity: 'HIGH',
    status: 'IN_PROGRESS',
    departmentId: 'dept-roads',
    ward: 'WARD-12',
    zone: 'ZONE-A',
    addressLine: 'Shivaji Nagar School Road',
    latitude: 23.2621,
    longitude: 77.4142,
    hoursAgo: 72,
    dueInHours: 48,
    analysis: simulatedAnalysis('analysis-101', hoursAgo(71.8), 'SUCCESS', 'ROAD', 'HIGH', 0.91),
    images: [photo('image-101', 'CITIZEN', hoursAgo(72)), photo('image-101-before', 'BEFORE', hoursAgo(72))],
    comments: [
      comment('comment-101', 'Road Maintenance & Transport', 'DEPARTMENT_ADMIN', 'Our field team has inspected the location. Repair is scheduled for tomorrow morning.', hoursAgo(30)),
      comment('comment-101-b', 'Aarav Sharma', 'CITIZEN', 'Thank you. The school gate is the worst section.', hoursAgo(28)),
    ],
    notes: [note('note-101', 'Meera Iyer', 'Material indented; crew assigned from Ward 12 depot.', hoursAgo(29))],
  },
  {
    id: 'complaint-102',
    ownerId: 'user-citizen-1',
    description: 'Streetlight has been out for a week outside the residential colony.',
    categoryCode: 'LIGHT',
    subcategoryCode: 'OUTAGE',
    severity: 'MEDIUM',
    status: 'ASSIGNED',
    departmentId: 'dept-power',
    ward: 'WARD-09',
    zone: 'ZONE-A',
    addressLine: 'B递 Block, Shahpura',
    latitude: 23.2441,
    longitude: 77.4271,
    hoursAgo: 60,
    dueInHours: 84,
    analysis: simulatedAnalysis('analysis-102', hoursAgo(59.8), 'SUCCESS', 'LIGHT', 'MEDIUM', 0.86),
  },
  {
    id: 'complaint-103',
    ownerId: 'user-citizen-2',
    description: 'Road surface has caved in near the bus stop and is a serious hazard.',
    categoryCode: 'ROAD',
    subcategoryCode: 'POTHOLE',
    severity: 'CRITICAL',
    status: 'RESOLVED',
    departmentId: 'dept-roads',
    ward: 'WARD-14',
    zone: 'ZONE-B',
    addressLine: 'Govindpuri Bus Stand',
    latitude: 23.2365,
    longitude: 77.3941,
    hoursAgo: 210,
    dueInHours: -80,
    resolvedHoursAgo: 92,
    analysis: simulatedAnalysis('analysis-103', hoursAgo(209.8), 'SUCCESS', 'ROAD', 'CRITICAL', 0.96),
    images: [photo('image-103', 'CITIZEN', hoursAgo(210)), photo('image-103-after', 'AFTER', hoursAgo(92))],
    comments: [comment('comment-103', 'Road Maintenance & Transport', 'DEPARTMENT_ADMIN', 'Temporary barricading installed and permanent restoration completed.', hoursAgo(93))],
    resolutionSummary: 'Pothole filled, compacted and barricades removed after inspection.',
  },
  {
    id: 'complaint-104',
    ownerId: 'user-citizen-3',
    description: 'Brown water is coming from the kitchen tap for the last two days.',
    categoryCode: 'WATER',
    subcategoryCode: 'LEAK',
    severity: 'HIGH',
    status: 'ACKNOWLEDGED',
    departmentId: 'dept-water',
    ward: 'WARD-18',
    zone: 'ZONE-B',
    addressLine: 'Chanakya Nagar',
    latitude: 23.2218,
    longitude: 77.4074,
    hoursAgo: 40,
    dueInHours: 32,
    analysis: simulatedAnalysis('analysis-104', hoursAgo(39.8), 'SUCCESS', 'WATER', 'HIGH', 0.89),
  },
  {
    id: 'complaint-105',
    ownerId: 'user-citizen-1',
    description: 'Drain is overflowing onto the lane after every spell of rain.',
    categoryCode: 'WATER',
    subcategoryCode: 'DRAIN',
    severity: 'HIGH',
    status: 'UNASSIGNED',
    departmentId: null,
    ward: 'WARD-12',
    zone: 'ZONE-A',
    addressLine: 'Near Shivaji Nagar Dispensary',
    latitude: 23.2608,
    longitude: 77.4138,
    hoursAgo: 28,
    dueInHours: 44,
    analysis: simulatedAnalysis('analysis-105', hoursAgo(27.6), 'FAILED', 'WATER', 'HIGH', null),
    routingOutcome: 'FAILED_AI',
  },
  {
    id: 'complaint-106',
    ownerId: 'user-citizen-2',
    description: 'Concrete divider has collapsed at the junction and is blocking one lane.',
    categoryCode: 'ROAD',
    subcategoryCode: 'DIVIDER',
    severity: 'HIGH',
    status: 'ASSIGNED',
    departmentId: 'dept-roads',
    ward: 'WARD-07',
    zone: 'ZONE-A',
    addressLine: 'Rani Kamalapati Road Junction',
    latitude: 23.2592,
    longitude: 77.4058,
    hoursAgo: 52,
    dueInHours: 60,
    analysis: simulatedAnalysis('analysis-106', hoursAgo(51.8), 'SUCCESS', 'ROAD', 'HIGH', 0.88),
  },
  {
    id: 'complaint-107',
    ownerId: 'user-citizen-3',
    description: 'Large garbage pile has not been cleared for several days.',
    categoryCode: 'WASTE',
    subcategoryCode: 'DUMP',
    severity: 'MEDIUM',
    status: 'IN_PROGRESS',
    departmentId: 'dept-sanitation',
    ward: 'WARD-21',
    zone: 'ZONE-C',
    addressLine: 'Kaliasot Road Market',
    latitude: 23.2482,
    longitude: 77.3821,
    hoursAgo: 96,
    dueInHours: 24,
    analysis: simulatedAnalysis('analysis-107', hoursAgo(95.8), 'SUCCESS', 'WASTE', 'MEDIUM', 0.82),
  },
  {
    id: 'complaint-108',
    ownerId: 'user-citizen-1',
    description: 'Series of potholes on the approach road to the bus depot.',
    categoryCode: 'ROAD',
    subcategoryCode: 'POTHOLE',
    severity: 'MEDIUM',
    status: 'RESOLVED',
    departmentId: 'dept-roads',
    ward: 'WARD-12',
    zone: 'ZONE-A',
    addressLine: 'Depot Approach Road',
    latitude: 23.2655,
    longitude: 77.4188,
    hoursAgo: 320,
    dueInHours: -120,
    resolvedHoursAgo: 140,
    analysis: simulatedAnalysis('analysis-108', hoursAgo(319.8), 'SUCCESS', 'ROAD', 'MEDIUM', 0.84),
    feedback: {
      rating: 4,
      comment: 'Repair was completed, though it took a little longer than expected.',
      resolutionConfirmed: true,
      disputeReason: null,
      createdAt: hoursAgo(130),
    },
    resolutionSummary: 'Approach road resurfaced across three locations.',
  },
  {
    id: 'complaint-109',
    ownerId: 'user-citizen-1',
    description: 'Fencing around the neighborhood park is broken near the play area.',
    categoryCode: 'PARK',
    subcategoryCode: 'FENCE',
    severity: 'LOW',
    status: 'PENDING_ASSIGNMENT',
    departmentId: null,
    ward: 'WARD-12',
    zone: 'ZONE-A',
    addressLine: 'Neighborhood Park, Shivaji Nagar',
    latitude: 23.2611,
    longitude: 77.4172,
    hoursAgo: 18,
    dueInHours: 96,
    analysis: simulatedAnalysis('analysis-109', hoursAgo(17.8), 'SUCCESS', 'PARK', 'LOW', 0.52),
    routingOutcome: 'AMBIGUOUS',
  },
  {
    id: 'complaint-110',
    ownerId: 'user-citizen-2',
    description: 'Distribution transformer is sparking and smells of burnt insulation.',
    categoryCode: 'LIGHT',
    subcategoryCode: 'POLE',
    severity: 'CRITICAL',
    status: 'ASSIGNED',
    departmentId: 'dept-power',
    ward: 'WARD-16',
    zone: 'ZONE-B',
    addressLine: 'Subhash Nagar Lane 4',
    latitude: 23.2289,
    longitude: 77.3992,
    hoursAgo: 14,
    dueInHours: 6,
    analysis: simulatedAnalysis('analysis-110', hoursAgo(13.8), 'SUCCESS', 'LIGHT', 'CRITICAL', 0.93),
  },
  {
    id: 'complaint-111',
    ownerId: 'user-citizen-3',
    description: 'Pedestrian crossing markings have almost completely faded.',
    categoryCode: 'ROAD',
    subcategoryCode: 'MARKING',
    severity: 'LOW',
    status: 'CLOSED',
    departmentId: 'dept-roads',
    ward: 'WARD-02',
    zone: 'ZONE-A',
    addressLine: 'Court Road Crossing',
    latitude: 23.2582,
    longitude: 77.4031,
    hoursAgo: 500,
    dueInHours: -300,
    resolvedHoursAgo: 360,
    analysis: simulatedAnalysis('analysis-111', hoursAgo(499.8), 'SUCCESS', 'ROAD', 'LOW', 0.79),
    resolutionSummary: 'Zebra crossing repainted.',
  },
  {
    id: 'complaint-112',
    ownerId: 'user-citizen-2',
    description: 'Water pipeline is leaking continuously at the corner of the street.',
    categoryCode: 'WATER',
    subcategoryCode: 'LEAK',
    severity: 'HIGH',
    status: 'REOPENED',
    departmentId: 'dept-water',
    ward: 'WARD-18',
    zone: 'ZONE-B',
    addressLine: 'Chanakya Nagar Extension',
    latitude: 23.2197,
    longitude: 77.4098,
    hoursAgo: 260,
    dueInHours: -20,
    resolvedHoursAgo: 80,
    analysis: simulatedAnalysis('analysis-112', hoursAgo(259.8), 'SUCCESS', 'WATER', 'HIGH', 0.87),
    feedback: {
      rating: 2,
      comment: 'Leaking started again after the repair.',
      resolutionConfirmed: false,
      disputeReason: 'Water is still pooling at the same corner.',
      createdAt: hoursAgo(70),
    },
    resolutionSummary: 'Initial joint repair completed.',
  },
  {
    id: 'complaint-113',
    ownerId: 'user-citizen-1',
    description: 'Construction debris has been dumped on the public footpath.',
    categoryCode: 'WASTE',
    subcategoryCode: 'DUMP',
    severity: 'MEDIUM',
    status: 'SUBMITTED',
    departmentId: null,
    ward: 'WARD-12',
    zone: 'ZONE-A',
    addressLine: 'Near MP Nagar Square',
    latitude: 23.2559,
    longitude: 77.4201,
    hoursAgo: 4,
    dueInHours: 72,
  },
  {
    id: 'complaint-114',
    ownerId: 'user-citizen-3',
    description: 'Manhole cover is missing on a busy stretch of road.',
    categoryCode: 'ROAD',
    subcategoryCode: 'POTHOLE',
    severity: 'CRITICAL',
    status: 'ON_HOLD',
    departmentId: 'dept-roads',
    ward: 'WARD-05',
    zone: 'ZONE-A',
    addressLine: 'NH-12 Service Road',
    latitude: 23.2714,
    longitude: 77.3986,
    hoursAgo: 120,
    dueInHours: -12,
    analysis: simulatedAnalysis('analysis-114', hoursAgo(119.8), 'SUCCESS', 'ROAD', 'CRITICAL', 0.95),
    images: [photo('image-114', 'CITIZEN', hoursAgo(120))],
    comments: [
      comment('comment-114', 'Road Maintenance & Transport', 'DEPARTMENT_ADMIN', 'Could you share a landmark? We are waiting for the ward crew to confirm the exact spot.', hoursAgo(64)),
    ],
  },
  {
    id: 'complaint-115',
    ownerId: 'user-citizen-2',
    description: 'A large tree branch has fallen and is blocking the walking path.',
    categoryCode: 'PARK',
    subcategoryCode: 'TREE',
    severity: 'HIGH',
    status: 'UNASSIGNED',
    departmentId: null,
    ward: 'WARD-22',
    zone: 'ZONE-C',
    addressLine: 'Upper Lake Walking Path',
    latitude: 23.2577,
    longitude: 77.3812,
    hoursAgo: 22,
    dueInHours: 30,
    analysis: simulatedAnalysis('analysis-115', hoursAgo(21.7), 'SUCCESS', 'PARK', 'HIGH', 0.48, {
      subcategoryCode: 'TREE',
      isCivicIssue: true,
      requiresHumanReview: true,
      requiresAdditionalInfo: true,
      description: 'Simulated AI: possible fallen tree; low confidence because the location may be outside the city boundary.',
    }),
    routingOutcome: 'AMBIGUOUS',
  },
]

function notification(
  id: string,
  userId: string,
  type: string,
  title: string,
  body: string,
  hoursSince: number,
  read: boolean,
  complaintId: string | null = null,
  complaintNumber: string | null = null,
): DemoNotification {
  return {
    id,
    userId,
    type,
    title,
    body,
    channel: 'IN_APP',
    deliveryStatus: read ? 'READ' : 'SENT',
    complaintNumber,
    complaintId,
    readAt: read ? hoursAgo(hoursSince - 1) : null,
    createdAt: hoursAgo(hoursSince),
    sentAt: hoursAgo(hoursSince),
  }
}

const NOTIFICATION_SEEDS: DemoNotification[] = [
  notification('notif-1', 'user-citizen-1', 'STATUS_CHANGED', 'Your report is in progress', 'Road Maintenance & Transport started work on your pothole report.', 28, false, 'complaint-101', 'CIV-2026-0101'),
  notification('notif-2', 'user-citizen-1', 'STATUS_CHANGED', 'Report resolved', 'Your bus depot approach road report was marked resolved.', 138, true, 'complaint-108', 'CIV-2026-0108'),
  notification('notif-3', 'user-citizen-1', 'INFO_REQUESTED', 'Information requested', 'The department asked for more detail about the drain overflow.', 26, false, 'complaint-105', 'CIV-2026-0105'),
  notification('notif-4', 'user-citizen-1', 'SUBMITTED', 'Report received', 'Your broken park fencing report was saved successfully.', 4, false, 'complaint-109', 'CIV-2026-0109'),
  notification('notif-5', 'user-citizen-2', 'ASSIGNED', 'Report assigned', 'Your transformer safety report was assigned to Public Lighting & Electrical.', 13, false, 'complaint-110', 'CIV-2026-0110'),
  notification('notif-6', 'user-citizen-2', 'REOPENED', 'Report reopened', 'The water leakage report was reopened after your feedback.', 69, true, 'complaint-112', 'CIV-2026-0112'),
  notification('notif-7', 'user-dept-roads', 'NEW_COMPLAINT', 'New complaint in your queue', 'A high-severity pothole report was assigned to your department.', 70, false, 'complaint-101', 'CIV-2026-0101'),
  notification('notif-8', 'user-dept-roads', 'INFO_RESPONSE', 'Citizen replied', 'A citizen replied to your information request.', 27, false, 'complaint-114', 'CIV-2026-0114'),
  notification('notif-9', 'user-admin-1', 'ROUTING_AMBIGUOUS', 'Ambiguous routing result', 'A low-confidence report requires manual classification.', 21, false, 'complaint-115', 'CIV-2026-0115'),
  notification('notif-10', 'user-admin-1', 'DEPARTMENT_PENDING', 'Department verification pending', 'Parks, Gardens & Public Spaces has applied for verification.', 11, true, null, null),
]

function audit(
  id: string,
  actorName: string,
  actorRole: string,
  action: string,
  entityType: string,
  entityId: string,
  reason: string,
  hoursSince: number,
): AdminAuditEntry {
  return {
    id,
    actorId: `audit-actor-${id}`,
    actorName,
    actorRole,
    action,
    entityType,
    entityId,
    reason,
    correlationId: `demo-correlation-${id}`,
    createdAt: hoursAgo(hoursSince),
  }
}

const AUDIT_SEEDS: AdminAuditEntry[] = [
  audit('audit-1', 'Meera Iyer', 'DEPARTMENT_ADMIN', 'COMPLAINT_ACCEPTED', 'COMPLAINT', 'complaint-101', 'Field inspection scheduled.', 29),
  audit('audit-2', 'Meera Iyer', 'DEPARTMENT_ADMIN', 'STATUS_CHANGED', 'COMPLAINT', 'complaint-101', 'Work started after material arrival.', 8),
  audit('audit-3', 'Aarav Sharma', 'CITIZEN', 'FEEDBACK_SUBMITTED', 'COMPLAINT', 'complaint-108', 'Citizen confirmed the repair.', 130),
  audit('audit-4', 'Vikram Sinha', 'SUPER_ADMIN', 'DEPARTMENT_SUSPENDED', 'DEPARTMENT', 'dept-power', 'Repeated SLA breaches in the demo dataset.', 60),
  audit('audit-5', 'Neha Bhatt', 'DEPARTMENT_ADMIN', 'DEPARTMENT_REGISTERED', 'DEPARTMENT', 'dept-parks', 'New department application.', 11),
  audit('audit-6', 'Vikram Sinha', 'SUPER_ADMIN', 'CLASSIFICATION_OVERRIDE', 'COMPLAINT', 'complaint-112', 'Corrected category after manual review.', 75),
  audit('audit-7', 'Sanjay Patel', 'DEPARTMENT_ADMIN', 'COMPLAINT_ACCEPTED', 'COMPLAINT', 'complaint-112', 'Accepted for pipeline inspection.', 80),
  audit('audit-8', 'Kavita Joshi', 'DEPARTMENT_ADMIN', 'STATUS_CHANGED', 'COMPLAINT', 'complaint-107', 'Cleanup crew dispatched.', 12),
  audit('audit-9', 'Vikram Sinha', 'SUPER_ADMIN', 'ROUTING_OVERRIDE', 'COMPLAINT', 'complaint-109', 'Manual routing review requested.', 17),
  audit('audit-10', 'Anita Desai', 'SUPER_ADMIN', 'USER_STATUS_CHANGED', 'USER', 'user-dept-power', 'Suspended account linked to suspended department.', 59),
]

const JURISDICTIONS: AdminJurisdiction[] = [
  { id: 'jur-bhopal-city', code: 'BHOPAL-CITY', name: 'Bhopal City', description: 'Municipal boundary used by demo routing.', active: true },
  { id: 'jur-zone-a', code: 'ZONE-A', name: 'Zone A', description: 'Central Bhopal wards.', active: true },
  { id: 'jur-zone-b', code: 'ZONE-B', name: 'Zone B', description: 'South and east Bhopal wards.', active: true },
  { id: 'jur-zone-c', code: 'ZONE-C', name: 'Zone C', description: 'Western Bhopal wards.', active: true },
]

const REASSIGNMENT_SEEDS: AdminReassignmentRequest[] = [
  {
    id: 'reassign-1',
    complaintId: 'complaint-115',
    fromDepartmentId: null,
    toDepartmentId: 'dept-parks',
    status: 'PENDING',
    reason: 'Low-confidence tree report appears to fall under the parks department.',
    createdAt: hoursAgo(20),
  },
]

function serviceArea(id: string, departmentId: string, name: string, ward: string, zone: string): AdminServiceArea {
  const department = DEPARTMENT_SEEDS.find((item) => item.id === departmentId)
  return {
    id,
    departmentId,
    departmentName: department?.name ?? departmentId,
    name,
    coversWard: ward,
    coversZone: zone,
    jurisdictionCode: 'BHOPAL-CITY',
    active: department?.isActive ?? true,
    createdAt: hoursAgo(24 * 60),
  }
}

export function demoUserToMe(user: DemoUser): MeResponse {
  return {
    id: user.id,
    email: user.email,
    fullName: user.fullName,
    role: user.role,
    accountStatus: user.accountStatus,
    verificationStatus: user.verificationStatus,
    departmentId: user.departmentId,
    permissions: user.permissions,
  }
}

export function seedDemoState(): DemoState {
  const departments: DemoDepartment[] = DEPARTMENT_SEEDS.map((department, index) => ({
    ...department,
    capabilities: departmentCapabilities(department.id),
    serviceAreas: departmentServiceAreas(department, index),
  }))
  const complaints = COMPLAINT_SEEDS.map((seed, index) => buildComplaint(seed, 101 + index))
  const images: DemoUploadedImage[] = complaints.flatMap((complaint) =>
    complaint.images.map((image) => ({
      imageId: image.id,
      storageKey: image.storageKey,
      contentType: image.contentType,
      sizeBytes: image.sizeBytes ?? 180_000,
      imageKind: image.imageKind,
      complaintId: complaint.id,
      createdAt: image.createdAt,
      uploadStatus: 'VERIFIED',
      previewDataUrl: null,
    })),
  )
  const serviceAreas = departments.flatMap((department) =>
    department.serviceAreas.map((area) =>
      serviceArea(area.id, department.id, area.name, area.coversWard ?? '', area.coversZone ?? ''),
    ),
  )

  return {
    version: DEMO_STORAGE_VERSION,
    users: USER_SEEDS.map((user) => ({ ...user })),
    departments,
    complaints,
    notifications: NOTIFICATION_SEEDS.map((item) => ({ ...item })),
    audit: AUDIT_SEEDS.map((item) => ({ ...item })),
    categories: CATEGORIES.map((item) => ({ ...item })),
    jurisdictions: JURISDICTIONS.map((item) => ({ ...item })),
    serviceAreas,
    reassignmentRequests: REASSIGNMENT_SEEDS.map((item) => ({ ...item })),
    images,
    counters: {
      complaint: 116,
      notification: 11,
      audit: 11,
      image: 1,
      category: 100,
      jurisdiction: 10,
      reassignment: 2,
    },
    updatedAt: new Date().toISOString(),
  }
}

export function demoRoleHome(role: Role): string {
  if (role === 'SUPER_ADMIN') return '/admin'
  if (role === 'DEPARTMENT_ADMIN') return '/department'
  return '/dashboard'
}

export function isVerified(status: VerificationStatus | null): boolean {
  return status === 'VERIFIED'
}
