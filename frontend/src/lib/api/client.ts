import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { installDemoAdapter } from '../../demo/adapter'
import { DEMO_ENABLED } from '../../demo/feature'

const baseURL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

/** Access tokens are kept in memory only; the refresh token is an httpOnly cookie. */
let accessToken: string | null = null
let refreshInFlight: Promise<void> | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

const api = axios.create({
  baseURL,
  timeout: 10_000,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

// In development, the demo adapter replaces the network transport on the shared
// instance. Production builds statically drop this branch and keep real Axios.
if (DEMO_ENABLED) installDemoAdapter(api)

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

type RetryableConfig = InternalAxiosRequestConfig & { _retry?: boolean }

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetryableConfig | undefined
    const isRefreshCall = config?.url?.includes('/auth/refresh')
    // Demo sessions are local; never call the real refresh endpoint from demo mode.
    if (DEMO_ENABLED) return Promise.reject(error)
    if (error.response?.status !== 401 || !config || config._retry || isRefreshCall) {
      return Promise.reject(error)
    }

    config._retry = true
    try {
      await refreshAccessToken()
      return api(config)
    } catch (refreshError) {
      setAccessToken(null)
      return Promise.reject(refreshError)
    }
  },
)

/** Uses a bare instance so the interceptor cannot recurse. */
async function refreshAccessToken(): Promise<void> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const response = await axios.post<LoginResponse>(
        `${baseURL}/auth/refresh`,
        undefined,
        { withCredentials: true },
      )
      setAccessToken(response.data.accessToken)
    })().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

export { api }

// ---------------------------------------------------------------------------
// Types (generated OpenAPI types replace these in Phase 3+)
// ---------------------------------------------------------------------------

export type Role = 'CITIZEN' | 'DEPARTMENT_ADMIN' | 'SUPER_ADMIN'
export type VerificationStatus =
  | 'PENDING_VERIFICATION'
  | 'VERIFIED'
  | 'REJECTED'
  | 'SUSPENDED'

export interface MeResponse {
  id: string
  email: string
  fullName: string
  role: Role
  accountStatus: string
  verificationStatus: VerificationStatus | null
  departmentId: string | null
  permissions: string[]
  /** Only present in the local demo; the real account API omits it. */
  phone?: string | null
}

export interface UserInfo {
  id: string
  email: string
  fullName: string
  role: Role
  departmentId: string | null
  verificationStatus: VerificationStatus | null
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}

export interface HealthResponse {
  status: string
  version: string
  time: string
  database: string
  postgis: string
  objectStorage: string
}

// ---------------------------------------------------------------------------
// API calls
// ---------------------------------------------------------------------------

export async function getHealth(): Promise<HealthResponse> {
  const { data } = await api.get<HealthResponse>('/meta/health')
  return data
}

export async function getMe(): Promise<MeResponse> {
  const { data } = await api.get<MeResponse>('/auth/me')
  return data
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  const { data } = await api.post<LoginResponse>('/auth/login', { email, password })
  setAccessToken(data.accessToken)
  return data
}

export async function logout(): Promise<void> {
  try {
    await api.post('/auth/logout')
  } finally {
    setAccessToken(null)
  }
}

/** Demo-only profile update; the real account API currently exposes /auth/me as read-only. */
export async function updateDemoProfile(payload: {
  fullName?: string
  phone?: string
}): Promise<MeResponse> {
  if (!DEMO_ENABLED) {
    throw new Error('Profile updates are only available in local demo mode')
  }
  const { data } = await api.patch<MeResponse>('/auth/profile', payload)
  return data
}

export async function forgotPassword(email: string): Promise<string> {
  const { data } = await api.post<{ message: string }>('/auth/forgot-password', {
    email,
  })
  return data.message
}

/** The one-time token is passed only in the request body, never stored locally. */
export async function resetPassword(token: string, password: string): Promise<void> {
  await api.post('/auth/reset-password', { token, password })
}

export interface RegisterCitizenPayload {
  fullName: string
  email: string
  password: string
  phone?: string
  locale?: string
}

export async function registerCitizen(payload: RegisterCitizenPayload): Promise<void> {
  await api.post('/auth/register/citizen', payload)
}

export interface RegisterDepartmentPayload {
  department: {
    name: string
    departmentType: string
    officialEmail: string
    contactNumber?: string
    description?: string
    officeAddress?: string
    city?: string
    state?: string
    country?: string
  }
  administrator: {
    fullName: string
    email: string
    password: string
    phone?: string
  }
}

export async function registerDepartment(
  payload: RegisterDepartmentPayload,
): Promise<{ departmentId: string; verificationStatus: VerificationStatus }> {
  const { data } = await api.post<{
    departmentId: string
    verificationStatus: VerificationStatus
    message: string
  }>('/auth/register/department', payload)
  return data
}

export interface DepartmentSummary {
  id: string
  name: string
  departmentCode: string
  departmentType: string
  officialEmail: string
  city: string | null
  verificationStatus: VerificationStatus
  isActive: boolean
  acceptsComplaints: boolean
  verifiedAt: string | null
}

export async function listDepartments(): Promise<DepartmentSummary[]> {
  const { data } = await api.get<DepartmentSummary[]>('/admin/departments')
  return data
}

export async function verifyDepartment(
  id: string,
  decision: 'VERIFIED' | 'REJECTED',
  reason?: string,
): Promise<DepartmentSummary> {
  const { data } = await api.post<DepartmentSummary>(
    `/admin/departments/${id}/verify`,
    { decision, reason },
  )
  return data
}
