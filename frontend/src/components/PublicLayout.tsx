import { Link } from 'react-router-dom'
import type { ReactNode } from 'react'
import { Icon } from './Icon'
import { DemoBadge } from '../demo/DemoControls'

const currentYear = new Date().getFullYear()

export function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <div className="relative isolate flex min-h-screen flex-col bg-paper text-stone-900">
      <div aria-hidden="true" className="bg-grid pointer-events-none absolute inset-x-0 top-0 -z-10 h-[620px]" />
      <header className="sticky top-0 z-20 border-b border-stone-200 bg-paper/85 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-5 py-3.5 sm:px-8">
          <Link to="/" className="flex items-center gap-2.5" aria-label="CivicSense home">
            <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-700 text-paper">
              <Icon name="shield" className="h-5 w-5" />
            </span>
            <span className="font-display text-lg font-semibold tracking-tight">CivicSense</span>
          </Link>
          <nav className="flex items-center gap-2 sm:gap-3">
            <DemoBadge compact />
            <Link
              to="/login"
              className="rounded-lg px-3 py-2 text-sm font-semibold text-stone-600 transition hover:text-stone-900"
            >
              Sign in
            </Link>
            <Link
              to="/register"
              className="rounded-full bg-stone-900 px-4 py-2 text-sm font-semibold text-stone-50 transition hover:bg-brand-800"
            >
              Get started
            </Link>
          </nav>
        </div>
      </header>
      <div className="flex-1">{children}</div>
      <footer className="border-t border-stone-200 bg-paper-deep/60">
        <div className="mx-auto flex max-w-7xl flex-col gap-2 px-5 py-6 text-xs text-stone-500 sm:flex-row sm:items-center sm:justify-between sm:px-8">
          <p>© {currentYear} CivicSense · Built for clearer civic conversations.</p>
          <p>
            Authentication and data access are owned by the CivicSense Spring Boot API. Local demo
            mode uses seeded browser data only.
          </p>
        </div>
      </footer>
    </div>
  )
}
