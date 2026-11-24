import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'
import { getUnreadNotificationCount } from '../lib/api/citizen'
import { useAuth } from '../lib/auth/AuthContext'
import {
  DemoAccountSwitcher,
  DemoBadge,
  DemoResetButton,
} from '../demo/DemoControls'
import { Icon } from './Icon'
import { Button, Spinner } from './ui'

interface NavItem {
  to: string
  label: string
  icon: 'home' | 'plus' | 'clipboard' | 'bell' | 'user' | 'shield'
  roles?: string[]
  end?: boolean
}

const navItems: NavItem[] = [
  { to: '/dashboard', label: 'Overview', icon: 'home', roles: ['CITIZEN'] },
  { to: '/complaints/new', label: 'Report an issue', icon: 'plus', roles: ['CITIZEN'] },
  { to: '/complaints', label: 'My complaints', icon: 'clipboard', roles: ['CITIZEN'] },
  {
    to: '/department',
    label: 'Department overview',
    icon: 'home',
    roles: ['DEPARTMENT_ADMIN'],
    end: true,
  },
  {
    to: '/department/complaints',
    label: 'Complaint queue',
    icon: 'clipboard',
    roles: ['DEPARTMENT_ADMIN'],
  },
  {
    to: '/department/profile',
    label: 'Department profile',
    icon: 'shield',
    roles: ['DEPARTMENT_ADMIN'],
  },
  { to: '/notifications', label: 'Notifications', icon: 'bell' },
  { to: '/profile', label: 'Profile', icon: 'user' },
  { to: '/admin', label: 'Administration', icon: 'shield', roles: ['SUPER_ADMIN'] },
]

const adminNavItems = [
  { to: '/admin', label: 'Overview', end: true },
  { to: '/admin/departments', label: 'Departments', end: false },
  { to: '/admin/users', label: 'Users', end: false },
  { to: '/admin/complaints', label: 'Complaints', end: false },
  { to: '/admin/platform', label: 'Platform', end: false },
  { to: '/admin/audit', label: 'Audit', end: false },
]

function Logo() {
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-700 text-paper">
        <Icon name="shield" className="h-5 w-5" />
      </div>
      <div>
        <p className="font-display text-base leading-none font-semibold tracking-tight text-stone-900">
          CivicSense
        </p>
        <p className="mt-1 text-[10px] font-medium tracking-[0.14em] text-stone-500 uppercase">
          Civic reporting
        </p>
      </div>
    </div>
  )
}

function NavButtons({
  role,
  unread,
  unreadUnavailable,
  onNavigate,
}: {
  role: string | undefined
  unread: number
  unreadUnavailable: boolean
  onNavigate?: () => void
}) {
  return (
    <nav className="space-y-1">
      {navItems
        .filter((item) => !item.roles || (role && item.roles.includes(role)))
        .map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            onClick={onNavigate}
            className={({ isActive }) =>
              `group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition ${
                isActive
                  ? 'bg-stone-900 text-stone-50 shadow-sm'
                  : 'text-stone-600 hover:bg-stone-200/60 hover:text-stone-900'
              }`
            }
          >
            <Icon name={item.icon} className="h-[18px] w-[18px]" />
            <span className="flex-1">{item.label}</span>
            {item.to === '/notifications' && unreadUnavailable && (
              <span
                aria-label="Unread notification count unavailable"
                title="Unread notification count unavailable"
                className="rounded-full bg-amber-500 px-2 py-0.5 text-[10px] font-bold text-white"
              >
                !
              </span>
            )}
            {item.to === '/notifications' && !unreadUnavailable && unread > 0 && (
              <span
                aria-label={`${unread} unread notifications`}
                className="rounded-full bg-clay-500 px-2 py-0.5 text-[10px] font-bold text-white"
              >
                {unread > 99 ? '99+' : unread}
              </span>
            )}
          </NavLink>
        ))}
    </nav>
  )
}

export function AppLayout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)

  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: getUnreadNotificationCount,
    refetchInterval: 30_000,
    retry: false,
  })
  const unreadCount = unread.data ?? 0
  const unreadUnavailable = unread.isError

  async function signOut() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-paper text-stone-900">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r border-stone-200 bg-paper px-4 py-6 lg:flex">
        <div className="px-2">
          <Logo />
          <div className="mt-3">
            <DemoBadge />
          </div>
        </div>
        <div className="mt-8 flex-1">
          <NavButtons
            role={user?.role}
            unread={unreadCount}
            unreadUnavailable={unreadUnavailable}
          />
        </div>
        <div className="rounded-xl border border-stone-200 bg-white p-3">
          <div className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-clay-100 text-clay-700">
              <Icon name="user" className="h-4 w-4" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-semibold text-stone-900">
                {user?.fullName ?? 'Citizen'}
              </p>
              <p className="truncate text-[11px] text-stone-500">{user?.email}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => void signOut()}
            className="mt-3 w-full rounded-lg border border-stone-200 bg-white px-3 py-1.5 text-xs font-semibold text-stone-600 transition hover:border-stone-900 hover:text-stone-900"
          >
            Sign out
          </button>
          <div className="mt-3 border-t border-stone-200 pt-3">
            <DemoAccountSwitcher />
            <div className="mt-2">
              <DemoResetButton />
            </div>
          </div>
        </div>
      </aside>

      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 border-b border-stone-200 bg-paper/90 backdrop-blur lg:hidden">
          <div className="flex items-center justify-between px-4 py-3">
            <div className="flex items-center gap-2">
              <Logo />
              <DemoBadge compact />
            </div>
            <button
              type="button"
              aria-label="Open navigation"
              onClick={() => setMenuOpen((open) => !open)}
              className="rounded-lg border border-stone-300 bg-white p-2 text-stone-700"
            >
              <Icon name={menuOpen ? 'x' : 'menu'} className="h-5 w-5" />
            </button>
          </div>
          {menuOpen && (
            <div className="border-t border-stone-200 bg-paper px-4 py-4 lg:hidden">
              <NavButtons
                role={user?.role}
                unread={unreadCount}
                unreadUnavailable={unreadUnavailable}
                onNavigate={() => setMenuOpen(false)}
              />
              <div className="mt-4 rounded-xl border border-stone-200 bg-white p-3">
                <DemoAccountSwitcher compact />
                <div className="mt-3">
                  <DemoResetButton compact />
                </div>
              </div>
              <Button
                variant="secondary"
                size="sm"
                fullWidth
                className="mt-3"
                onClick={() => void signOut()}
              >
                Sign out
              </Button>
            </div>
          )}
        </header>

        <main key={location.pathname} className="mx-auto w-full max-w-7xl px-4 py-7 sm:px-6 lg:px-10 lg:py-10">
          {user?.role === 'SUPER_ADMIN' && location.pathname.startsWith('/admin') && (
            <nav
              aria-label="Administration sections"
              className="mb-6 -mx-1 flex gap-1 overflow-x-auto border-b border-stone-200 px-1 pb-2"
            >
              {adminNavItems.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `shrink-0 rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                      isActive
                        ? 'bg-stone-900 text-stone-50'
                        : 'text-stone-600 hover:bg-stone-200/60 hover:text-stone-900'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          )}
          {children}
        </main>
      </div>
    </div>
  )
}

export function AppLoading() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-paper text-stone-500">
      <Spinner className="h-6 w-6 text-brand-700" />
      <span className="ml-3 text-sm">Loading CivicSense…</span>
    </div>
  )
}
