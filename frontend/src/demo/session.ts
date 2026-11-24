import type { Role } from '../lib/api/client'
import { clearDemoSession, readDemoSession, readDemoState, writeDemoSession } from './storage'
import type { DemoSession, DemoUser } from './types'

export class DemoAuthError extends Error {
  readonly status: number

  constructor(message: string, status = 400) {
    super(message)
    this.name = 'DemoAuthError'
    this.status = status
  }
}

function tokenFor(userId: string): string {
  return `demo.${userId}.${Date.now().toString(36)}`
}

export function findDemoUser(email: string): DemoUser | null {
  const normalized = email.trim().toLowerCase()
  return readDemoState().users.find((user) => user.email.toLowerCase() === normalized) ?? null
}

export function authenticateDemo(email: string, password: string): DemoSession {
  const user = findDemoUser(email)
  if (!user || user.password !== password) {
    throw new DemoAuthError('Invalid demo credentials')
  }
  if (user.accountStatus !== 'ACTIVE') {
    throw new DemoAuthError('This demo account is not active', 403)
  }
  const session: DemoSession = {
    userId: user.id,
    email: user.email,
    role: user.role,
    loginAt: new Date().toISOString(),
    token: tokenFor(user.id),
  }
  writeDemoSession(session)
  return session
}

export function currentDemoSession(): DemoSession | null {
  const session = readDemoSession()
  if (!session) return null
  const user = findDemoUser(session.email)
  if (!user || user.id !== session.userId || user.role !== session.role || user.accountStatus !== 'ACTIVE') {
    clearDemoSession()
    return null
  }
  return session
}

export function currentDemoUser(): DemoUser | null {
  const session = currentDemoSession()
  if (!session) return null
  return findDemoUser(session.email)
}

export function switchDemoAccount(email: string): DemoSession {
  const user = findDemoUser(email)
  if (!user) throw new DemoAuthError('Unknown demo account')
  return authenticateDemo(user.email, user.password)
}

export function assertRole(role: Role | Role[]): DemoUser {
  const user = currentDemoUser()
  if (!user) throw new DemoAuthError('A demo session is required', 401)
  const allowed = Array.isArray(role) ? role : [role]
  if (!allowed.includes(user.role)) {
    throw new DemoAuthError('This demo account cannot access that area', 403)
  }
  if (user.accountStatus !== 'ACTIVE') {
    throw new DemoAuthError('This demo account is not active', 403)
  }
  return user
}
