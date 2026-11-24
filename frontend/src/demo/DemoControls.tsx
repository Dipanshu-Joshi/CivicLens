import { useEffect, useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Button, inputClass } from '../components/ui'
import { setAccessToken } from '../lib/api/client'
import { useAuth } from '../lib/auth/AuthContext'
import { DEMO_ACCOUNTS, DEMO_ENABLED } from './feature'
import { switchDemoAccount } from './session'
import { clearDemoSession, resetDemoState, subscribeDemoChanges } from './storage'

export function DemoBadge({ compact = false }: { compact?: boolean }) {
  if (!DEMO_ENABLED) return null
  return (
    <span
      className={`inline-flex shrink-0 items-center gap-1.5 rounded-full border border-clay-300 bg-clay-50 font-semibold tracking-wide text-clay-700 uppercase ${
        compact ? 'px-2 py-0.5 text-[10px]' : 'px-2.5 py-1 text-[11px]'
      }`}
      title="Local development data only; no backend is used"
    >
      <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-clay-500" />
      DEMO MODE
    </span>
  )
}

/** Keeps cached React Query data aligned with local demo mutations and resets. */
export function DemoBridge() {
  const queryClient = useQueryClient()
  useEffect(() => {
    if (!DEMO_ENABLED) return
    return subscribeDemoChanges(() => {
      void queryClient.invalidateQueries()
    })
  }, [queryClient])
  return null
}

export function DemoAccountSwitcher({ compact = false }: { compact?: boolean }) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [busy, setBusy] = useState(false)

  if (!DEMO_ENABLED || !user) return null

  async function changeAccount(event: FormEvent<HTMLSelectElement>) {
    const email = event.currentTarget.value
    if (!email || email === user?.email) return
    setBusy(true)
    try {
      const session = switchDemoAccount(email)
      setAccessToken(session.token)
      queryClient.clear()
      // A full reload reinitialises auth from the persisted demo session, so
      // role guards never evaluate the account that was just switched away.
      const target = DEMO_ACCOUNTS.find((account) => account.email === email)
      window.location.assign(target?.home ?? '/dashboard')
    } finally {
      setBusy(false)
    }
  }

  const options = DEMO_ACCOUNTS.some((account) => account.email === user.email)
    ? DEMO_ACCOUNTS
    : [
        { key: 'current' as const, label: 'Current account', email: user.email, password: '', home: '/dashboard', description: '' },
        ...DEMO_ACCOUNTS,
      ]

  return (
    <div className={compact ? '' : 'mt-3'}>
      <label
        htmlFor="demo-account-switcher"
        className="block text-[10px] font-semibold tracking-wide text-stone-500 uppercase"
      >
        Switch demo account
      </label>
      <select
        id="demo-account-switcher"
        value={user.email}
        disabled={busy}
        onChange={(event) => void changeAccount(event)}
        className={`${inputClass} mt-1 py-1.5 text-xs`}
      >
        {options.map((account) => (
          <option key={account.email} value={account.email}>
            {account.label} · {account.email}
          </option>
        ))}
      </select>
    </div>
  )
}

export function DemoResetButton({ compact = false }: { compact?: boolean }) {
  const queryClient = useQueryClient()
  const [busy, setBusy] = useState(false)

  if (!DEMO_ENABLED) return null

  async function reset() {
    if (!window.confirm('RESET DEMO DATA? All local demo changes will be discarded and the original seed restored.')) {
      return
    }
    setBusy(true)
    try {
      resetDemoState()
      clearDemoSession()
      setAccessToken(null)
      queryClient.clear()
      window.location.assign('/login')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      loading={busy}
      onClick={() => void reset()}
      className={compact ? 'w-full justify-center text-[11px]' : 'w-full justify-center'}
    >
      RESET DEMO DATA
    </Button>
  )
}
