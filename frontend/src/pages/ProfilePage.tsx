import { useState, type FormEvent } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { AppLayout } from '../components/AppLayout'
import { Icon } from '../components/Icon'
import { Badge, Button, Card, Field, PageHeader, inputClass } from '../components/ui'
import { updateDemoProfile } from '../lib/api/client'
import { errorMessage } from '../lib/api/errors'
import { useAuth } from '../lib/auth/AuthContext'
import { DEMO_ENABLED } from '../demo/feature'
import { humanizeStatus } from '../lib/utils/format'

function DemoProfileForm({
  initialName,
  initialPhone,
}: {
  initialName: string
  initialPhone: string
}) {
  const queryClient = useQueryClient()
  const [fullName, setFullName] = useState(initialName)
  const [phone, setPhone] = useState(initialPhone)
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function saveProfile(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setNotice(null)
    setError(null)
    try {
      await updateDemoProfile({ fullName: fullName.trim(), phone: phone.trim() })
      await queryClient.invalidateQueries({ queryKey: ['me'] })
      setNotice('Demo profile saved in this browser.')
    } catch (err) {
      setError(errorMessage(err, 'Could not save the demo profile'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={saveProfile} className="mt-6 border-t border-stone-200 pt-6">
      <h3 className="text-sm font-semibold text-stone-900">Edit demo profile</h3>
      <p className="mt-1 text-xs text-stone-500">
        Stored locally under the demo session; resetting demo data restores the seed.
      </p>
      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <Field label="Full name">
          <input
            value={fullName}
            onChange={(event) => setFullName(event.target.value)}
            className={inputClass}
            required
          />
        </Field>
        <Field label="Phone (optional)">
          <input
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
            className={inputClass}
            placeholder="Optional"
          />
        </Field>
      </div>
      {notice && <p className="mt-3 text-xs font-medium text-brand-700">{notice}</p>}
      {error && <p className="mt-3 text-xs text-red-700">{error}</p>}
      <Button type="submit" size="sm" className="mt-4" loading={saving}>
        Save demo profile
      </Button>
    </form>
  )
}

export default function ProfilePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function signOut() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <AppLayout>
      <PageHeader
        eyebrow="Account"
        title="Profile & settings"
        description={
          DEMO_ENABLED
            ? 'Your local demo identity. Edits are saved to browser storage only.'
            : 'Your CivicSense identity and the current limits of the account API.'
        }
      />

      <div className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
        <Card className="p-6">
          <div className="flex items-center gap-4">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-100 text-brand-800">
              <Icon name="user" className="h-6 w-6" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-stone-900">
                {user?.fullName ?? 'Citizen user'}
              </h2>
              <p className="text-sm text-stone-500">{user?.email}</p>
            </div>
          </div>

          <dl className="mt-7 grid gap-4 sm:grid-cols-2">
            <div className="rounded-xl border border-stone-200 p-4">
              <dt className="text-xs font-semibold text-stone-500">Role</dt>
              <dd className="mt-1 text-sm font-medium text-stone-900">
                {humanizeStatus(user?.role ?? 'CITIZEN')}
              </dd>
            </div>
            <div className="rounded-xl border border-stone-200 p-4">
              <dt className="text-xs font-semibold text-stone-500">Account status</dt>
              <dd className="mt-1 text-sm font-medium text-stone-900">
                {humanizeStatus(user?.accountStatus ?? 'ACTIVE')}
              </dd>
            </div>
            <div className="rounded-xl border border-stone-200 p-4 sm:col-span-2">
              <dt className="text-xs font-semibold text-stone-500">Permissions</dt>
              <dd className="mt-2 flex flex-wrap gap-2">
                {user?.permissions.length ? (
                  user.permissions.map((permission) => (
                    <Badge key={permission} tone="info">
                      {humanizeStatus(permission)}
                    </Badge>
                  ))
                ) : (
                  <span className="text-sm text-stone-500">No explicit permissions listed</span>
                )}
              </dd>
            </div>
            {user?.departmentId && (
              <div className="rounded-xl border border-stone-200 p-4 sm:col-span-2">
                <dt className="text-xs font-semibold text-stone-500">Department</dt>
                <dd className="mt-1 font-mono text-xs text-stone-700">{user.departmentId}</dd>
                {user.verificationStatus && (
                  <dd className="mt-1 text-xs text-stone-500">
                    Verification: {humanizeStatus(user.verificationStatus)}
                  </dd>
                )}
              </div>
            )}
          </dl>

          {DEMO_ENABLED && user && (
            <DemoProfileForm
              key={`${user.id}:${user.fullName}:${user.phone ?? ''}`}
              initialName={user.fullName}
              initialPhone={user.phone ?? ''}
            />
          )}
        </Card>

        <div className="space-y-6">
          <Card className="p-6">
            <h2 className="text-sm font-semibold text-stone-900">Session security</h2>
            <ul className="mt-4 space-y-3 text-xs leading-relaxed text-stone-600">
              {DEMO_ENABLED ? (
                <>
                  <li className="flex gap-2">
                    <Icon name="check-circle" className="h-4 w-4 shrink-0 text-brand-600" />
                    Demo sign-in is validated locally against seeded accounts.
                  </li>
                  <li className="flex gap-2">
                    <Icon name="check-circle" className="h-4 w-4 shrink-0 text-brand-600" />
                    The demo session persists in browser storage across refreshes.
                  </li>
                  <li className="flex gap-2">
                    <Icon name="check-circle" className="h-4 w-4 shrink-0 text-brand-600" />
                    No real token, cookie or backend session is created.
                  </li>
                </>
              ) : (
                <>
                  <li className="flex gap-2">
                    <Icon name="check-circle" className="h-4 w-4 shrink-0 text-brand-600" />
                    Access tokens are held in memory only.
                  </li>
                  <li className="flex gap-2">
                    <Icon name="check-circle" className="h-4 w-4 shrink-0 text-brand-600" />
                    Refresh tokens are httpOnly cookies managed by the API.
                  </li>
                  <li className="flex gap-2">
                    <Icon name="check-circle" className="h-4 w-4 shrink-0 text-brand-600" />
                    Expired sessions refresh automatically when possible.
                  </li>
                </>
              )}
            </ul>
            <Button variant="secondary" fullWidth className="mt-6" onClick={() => void signOut()}>
              Sign out
            </Button>
          </Card>

          <Card className="p-6">
            <h2 className="text-sm font-semibold text-stone-900">Settings</h2>
            <div className="mt-4 space-y-4 text-xs leading-relaxed text-stone-600">
              <div className="flex gap-2">
                <Icon name="bell" className="mt-0.5 h-4 w-4 shrink-0 text-brand-600" />
                <p>
                  {DEMO_ENABLED
                    ? 'Demo notifications are local and refresh whenever the browser data changes.'
                    : 'Notifications are refreshed from the API every 30 seconds. Push delivery is not configured in this phase.'}
                </p>
              </div>
              <div className="flex gap-2">
                <Icon name="info" className="mt-0.5 h-4 w-4 shrink-0 text-brand-600" />
                <p>
                  {DEMO_ENABLED
                    ? 'Demo profile edits never reach the real API. RESET DEMO DATA restores the seeded name and phone.'
                    : 'Profile editing is not available yet because the backend exposes read-only /auth/me. No local-only changes are presented as saved account changes.'}
                </p>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </AppLayout>
  )
}
