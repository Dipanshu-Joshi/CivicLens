import { DEMO_STORAGE_VERSION } from './feature'
import { seedDemoState } from './seed'
import type { DemoSession, DemoState } from './types'

export const DEMO_STATE_KEY = 'civicsense.demo.state.v1'
export const DEMO_SESSION_KEY = 'civicsense.demo.session.v1'
export const DEMO_CHANGE_EVENT = 'civicsense:demo-change'

function browserStorage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage
  } catch {
    return null
  }
}

export function notifyDemoChange(): void {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new Event(DEMO_CHANGE_EVENT))
}

export function subscribeDemoChanges(listener: () => void): () => void {
  if (typeof window === 'undefined') return () => undefined
  window.addEventListener(DEMO_CHANGE_EVENT, listener)
  return () => window.removeEventListener(DEMO_CHANGE_EVENT, listener)
}

export function readDemoState(): DemoState {
  const storage = browserStorage()
  const raw = storage?.getItem(DEMO_STATE_KEY)
  if (raw) {
    try {
      const parsed = JSON.parse(raw) as DemoState
      if (
        parsed.version === DEMO_STORAGE_VERSION &&
        Array.isArray(parsed.complaints) &&
        Array.isArray(parsed.users) &&
        Array.isArray(parsed.departments)
      ) {
        return parsed
      }
    } catch {
      // A corrupt demo snapshot is replaced by a clean seed below.
    }
  }
  const seeded = seedDemoState()
  writeDemoState(seeded, false)
  return seeded
}

export function writeDemoState(state: DemoState, notify = true): void {
  const storage = browserStorage()
  try {
    storage?.setItem(DEMO_STATE_KEY, JSON.stringify({ ...state, updatedAt: new Date().toISOString() }))
  } catch {
    // Browser storage can be unavailable or full; the demo still works in memory.
  }
  if (notify) notifyDemoChange()
}

export function mutateDemoState<T>(mutate: (state: DemoState) => T): T {
  const state = readDemoState()
  const result = mutate(state)
  writeDemoState(state)
  return result
}

export function resetDemoState(): DemoState {
  const seeded = seedDemoState()
  writeDemoState(seeded)
  return seeded
}

export function readDemoSession(): DemoSession | null {
  const raw = browserStorage()?.getItem(DEMO_SESSION_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as DemoSession
    return parsed && typeof parsed.userId === 'string' && typeof parsed.role === 'string'
      ? parsed
      : null
  } catch {
    return null
  }
}

export function writeDemoSession(session: DemoSession | null): void {
  const storage = browserStorage()
  try {
    if (session) storage?.setItem(DEMO_SESSION_KEY, JSON.stringify(session))
    else storage?.removeItem(DEMO_SESSION_KEY)
  } catch {
    // Ignore storage failures; the in-memory adapter still sees this session.
  }
  notifyDemoChange()
}

export function clearDemoSession(): void {
  writeDemoSession(null)
}
