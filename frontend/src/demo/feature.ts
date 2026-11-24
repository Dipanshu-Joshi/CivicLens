/**
 * Development-only demo feature gate.
 *
 * `import.meta.env.DEV` is statically replaced by Vite. In a production build
 * this constant becomes `false`, the adapter is never installed, and no demo
 * credential or seed data can activate the demo surface. Development builds
 * are demo-enabled by default; `VITE_DEMO_MODE=false` opts out (used by the
 * stubbed browser test suite).
 */
export const DEMO_ENABLED =
  import.meta.env.DEV === true && import.meta.env.VITE_DEMO_MODE !== 'false'

export function demoEnabledFor(dev: boolean): boolean {
  return dev === true
}

export const DEMO_STORAGE_VERSION = 1

export const DEMO_CREDENTIALS = {
  citizen: { email: 'citizen@demo.com', password: 'Demo@123' },
  department: { email: 'department@demo.com', password: 'Demo@123' },
  admin: { email: 'admin@demo.com', password: 'Demo@123' },
} as const

export type DemoAccountKey = keyof typeof DEMO_CREDENTIALS

export interface DemoAccountOption {
  key: DemoAccountKey
  label: string
  email: string
  password: string
  home: string
  description: string
}

/** The three account buttons shown on the development-only login screen. */
export const DEMO_ACCOUNTS: DemoAccountOption[] = [
  {
    key: 'citizen',
    label: 'Citizen',
    email: DEMO_CREDENTIALS.citizen.email,
    password: DEMO_CREDENTIALS.citizen.password,
    home: '/dashboard',
    description: 'Report issues, follow updates and give feedback.',
  },
  {
    key: 'department',
    label: 'Department',
    email: DEMO_CREDENTIALS.department.email,
    password: DEMO_CREDENTIALS.department.password,
    home: '/department',
    description: 'Work the Road Maintenance queue and resolve reports.',
  },
  {
    key: 'admin',
    label: 'Super admin',
    email: DEMO_CREDENTIALS.admin.email,
    password: DEMO_CREDENTIALS.admin.password,
    home: '/admin',
    description: 'Review platform analytics, AI, routing and governance.',
  },
]
