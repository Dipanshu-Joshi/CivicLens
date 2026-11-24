/**
 * Production build stub.
 *
 * Vite aliases every app-level `demo/*` import to this module during production
 * builds, so the demo adapter, seeded credentials and demo UI are never part of
 * the shipped bundle. Development and tests keep using the real implementations.
 */
import type { AxiosInstance } from 'axios'

export const DEMO_ENABLED = false

export function demoEnabledFor(_dev: boolean): boolean {
  return false
}

export type DemoAccountKey = 'citizen' | 'department' | 'admin'

export interface DemoAccountOption {
  key: DemoAccountKey
  label: string
  email: string
  password: string
  home: string
  description: string
}

export const DEMO_CREDENTIALS = {} as Record<DemoAccountKey, { email: string; password: string }>
export const DEMO_ACCOUNTS: DemoAccountOption[] = []

export function installDemoAdapter(_api: AxiosInstance): void {
  // Intentionally empty: production always uses the real Axios transport.
}

export function demoImageIdFromUploadUrl(_uploadUrl: string): string | null {
  return null
}

export function isDemoUploadUrl(_uploadUrl: string): boolean {
  return false
}

export async function storeDemoUploadPreview(_imageId: string, _file: Blob): Promise<void> {
  // Intentionally empty: production uploads use the real storage provider.
}

export function DemoBadge() {
  return null
}

export function DemoBridge() {
  return null
}

export function DemoAccountSwitcher() {
  return null
}

export function DemoResetButton() {
  return null
}
