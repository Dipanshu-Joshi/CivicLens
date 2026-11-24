import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { AppLayout } from '../components/AppLayout'
import { Icon } from '../components/Icon'
import {
  Badge,
  Button,
  Card,
  Field,
  PageHeader,
  Spinner,
  StatePanel,
  inputClass,
} from '../components/ui'
import {
  listAdminDepartments,
  listAdminUsers,
  updateAdminUserRole,
  updateAdminUserStatus,
  type AdminRole,
  type AdminUser,
  type AdminUserQuery,
  type AdminUserStatus,
} from '../lib/api/admin'
import { errorMessage } from '../lib/api/errors'
import { useAuth } from '../lib/auth/AuthContext'
import { formatDate, humanizeStatus } from '../lib/utils/format'

const PAGE_SIZE = 20
type ManualUserStatus = Extract<AdminUserStatus, 'ACTIVE' | 'SUSPENDED' | 'DISABLED'>
const statusChoices: ManualUserStatus[] = ['ACTIVE', 'SUSPENDED', 'DISABLED']
const roleChoices: AdminRole[] = ['CITIZEN', 'DEPARTMENT_ADMIN', 'SUPER_ADMIN']

function accountTone(status: AdminUserStatus) {
  if (status === 'ACTIVE') return 'success' as const
  if (status === 'PENDING_VERIFICATION') return 'info' as const
  return 'danger' as const
}

function reasonIsValid(reason: string): boolean {
  const length = reason.trim().length
  return length >= 10 && length <= 1000
}

interface Selection {
  user: AdminUser
  mode: 'status' | 'role'
}

export default function AdminUsersPage() {
  const { user: currentUser } = useAuth()
  const queryClient = useQueryClient()
  const [role, setRole] = useState('')
  const [status, setStatus] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<AdminUserQuery>({})
  const [selection, setSelection] = useState<Selection | null>(null)
  const [nextStatus, setNextStatus] = useState<ManualUserStatus>('SUSPENDED')
  const [nextRole, setNextRole] = useState<AdminRole>('DEPARTMENT_ADMIN')
  const [reason, setReason] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const departments = useQuery({
    queryKey: ['admin', 'departments'],
    queryFn: () => listAdminDepartments(),
    retry: false,
  })
  const users = useQuery({
    queryKey: ['admin', 'users', filters, page],
    queryFn: () => listAdminUsers({ ...filters, page, size: PAGE_SIZE }),
    retry: false,
  })

  async function invalidate() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] }),
      queryClient.invalidateQueries({ queryKey: ['admin', 'overview'] }),
      queryClient.invalidateQueries({ queryKey: ['me'] }),
    ])
  }

  const statusMutation = useMutation({
    mutationFn: ({
      id,
      payload,
    }: {
      id: string
      payload: { status: ManualUserStatus; reason: string }
    }) => updateAdminUserStatus(id, payload),
    onSuccess: async (_result, variables) => {
      setSelection(null)
      setReason('')
      setNotice(`Account status changed to ${humanizeStatus(variables.payload.status).toLowerCase()}. This change was recorded in the audit log.`)
      setActionError(null)
      setFormError(null)
      await invalidate()
    },
    onError: (error: unknown) => {
      setNotice(null)
      setActionError(errorMessage(error, 'The account status could not be changed.'))
    },
  })

  const roleMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: { role: AdminRole; reason: string } }) =>
      updateAdminUserRole(id, payload),
    onSuccess: async (_result, variables) => {
      setSelection(null)
      setReason('')
      setNotice(`Role changed to ${humanizeStatus(variables.payload.role)}. This change was recorded in the audit log.`)
      setActionError(null)
      setFormError(null)
      await invalidate()
    },
    onError: (error: unknown) => {
      setNotice(null)
      setActionError(errorMessage(error, 'The account role could not be changed.'))
    },
  })

  function applyFilters(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setFilters({
      role: (role || undefined) as AdminRole | undefined,
      status: (status || undefined) as AdminUserStatus | undefined,
      departmentId: departmentId || undefined,
      search: search.trim() || undefined,
    })
  }

  function clearFilters() {
    setRole('')
    setStatus('')
    setDepartmentId('')
    setSearch('')
    setPage(0)
    setFilters({})
  }

  function openSelection(user: AdminUser, mode: 'status' | 'role') {
    if (currentUser?.id === user.id) return
    setNotice(null)
    setActionError(null)
    setFormError(null)
    setReason('')
    const currentStatus = user.accountStatus as ManualUserStatus
    setNextStatus(statusChoices.includes(currentStatus) ? currentStatus : 'SUSPENDED')
    setNextRole(user.role)
    setSelection({ user, mode })
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!selection || currentUser?.id === selection.user.id) return
    if (!reasonIsValid(reason)) {
      setFormError('Enter a reason of 10–1000 characters. It is stored in the audit log.')
      return
    }
    const description =
      selection.mode === 'status'
        ? `Change ${selection.user.email} to ${humanizeStatus(nextStatus)} status`
        : `Change ${selection.user.email} to ${humanizeStatus(nextRole)} role`
    if (!window.confirm(`${description}? Suspended or disabled accounts lose active sessions.`)) {
      return
    }
    setFormError(null)
    if (selection.mode === 'status') {
      statusMutation.mutate({
        id: selection.user.id,
        payload: { status: nextStatus, reason: reason.trim() },
      })
    } else {
      roleMutation.mutate({
        id: selection.user.id,
        payload: { role: nextRole, reason: reason.trim() },
      })
    }
  }

  const hasFilters = Object.values(filters).some((value) => value !== undefined)
  const result = users.data
  const totalElements = result?.totalElements ?? 0
  const totalPages = result?.totalPages ?? 0
  const firstRow = totalElements === 0 ? 0 : page * PAGE_SIZE + 1
  const lastRow = Math.min((page + 1) * PAGE_SIZE, totalElements)
  const pending = statusMutation.isPending || roleMutation.isPending

  return (
    <AppLayout>
      <PageHeader
        eyebrow="Super admin"
        title="User management"
        description="Review accounts, change roles and enforce account status. Self-changes and removal of the last active super admin are blocked by the server."
        actions={
          <Link to="/admin">
            <Button variant="secondary" size="sm" icon={<Icon name="arrow-left" className="h-4 w-4" />}>
              Overview
            </Button>
          </Link>
        }
      />

      {notice && (
        <div role="status" className="mb-5 rounded-xl border border-brand-200 bg-brand-50 px-3 py-2.5 text-sm text-brand-800">
          {notice}
        </div>
      )}
      {actionError && (
        <div role="alert" className="mb-5 rounded-xl border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700">
          {actionError}
        </div>
      )}

      <Card className="mb-6 p-5">
        <form onSubmit={applyFilters} className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <div className="sm:col-span-2 xl:col-span-2">
            <Field label="Search" hint="Name or email">
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className={inputClass}
                placeholder="Search users"
              />
            </Field>
          </div>
          <Field label="Role">
            <select value={role} onChange={(event) => setRole(event.target.value)} className={inputClass}>
              <option value="">All roles</option>
              {roleChoices.map((choice) => (
                <option key={choice} value={choice}>
                  {humanizeStatus(choice)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Account status">
            <select value={status} onChange={(event) => setStatus(event.target.value)} className={inputClass}>
              <option value="">All statuses</option>
              {statusChoices.map((choice) => (
                <option key={choice} value={choice}>
                  {humanizeStatus(choice)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Department" hint={departments.isError ? 'Department list unavailable' : undefined}>
            <select
              value={departmentId}
              onChange={(event) => setDepartmentId(event.target.value)}
              className={inputClass}
            >
              <option value="">All departments</option>
              {departments.data?.map((department) => (
                <option key={department.id} value={department.id}>
                  {department.name}
                </option>
              ))}
            </select>
          </Field>
          <div className="flex flex-col justify-end gap-2 sm:col-span-2 xl:col-span-2">
            <div className="flex gap-2">
              <Button type="submit" className="flex-1">Apply filters</Button>
              <Button type="button" variant="secondary" onClick={clearFilters} disabled={!hasFilters}>
                Clear
              </Button>
            </div>
          </div>
        </form>
      </Card>

      {users.isPending && (
        <Card className="flex items-center justify-center gap-3 p-12 text-sm text-stone-500">
          <Spinner className="h-5 w-5 text-brand-600" />
          <span role="status">Loading users…</span>
        </Card>
      )}

      {users.isError && (
        <div role="alert">
          <StatePanel
            tone="error"
            title="Could not load users"
            description={errorMessage(users.error, 'The service did not return an account list.')}
            action={
              <Button variant="secondary" onClick={() => void users.refetch()}>
                Try again
              </Button>
            }
          />
        </div>
      )}

      {users.isSuccess && totalElements === 0 && (
        <StatePanel
          icon="info"
          title={hasFilters ? 'No users match these filters' : 'No users found'}
          description={hasFilters ? 'Try clearing a filter.' : 'Accounts will appear here once registered.'}
          action={
            hasFilters ? (
              <Button variant="secondary" onClick={clearFilters}>
                Clear filters
              </Button>
            ) : undefined
          }
        />
      )}

      {result && totalElements > 0 && (
        <div className="space-y-4">
          <p role="status" className="text-xs font-medium text-stone-500">
            Showing {firstRow}–{lastRow} of {totalElements} user{totalElements === 1 ? '' : 's'}
          </p>
          <div className="grid gap-4 lg:grid-cols-2">
            {result.content.map((account) => {
              const isSelf = currentUser?.id === account.id
              const isSelected = selection?.user.id === account.id
              return (
                <Card key={account.id} className="p-5">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <h2 className="font-semibold text-stone-900">{account.fullName}</h2>
                        <Badge tone={accountTone(account.accountStatus)}>
                          {humanizeStatus(account.accountStatus)}
                        </Badge>
                        <Badge tone={account.role === 'SUPER_ADMIN' ? 'info' : 'neutral'}>
                          {humanizeStatus(account.role)}
                        </Badge>
                        {isSelf && <Badge tone="success">You</Badge>}
                      </div>
                      <p className="mt-1 text-xs text-stone-600">{account.email}</p>
                      <p className="mt-1 text-xs text-stone-500">
                        {account.departmentName ?? account.departmentId ?? 'No department'}
                        {account.createdAt ? ` · Joined ${formatDate(account.createdAt)}` : ''}
                      </p>
                    </div>
                  </div>

                  {isSelf ? (
                    <p role="status" className="mt-4 rounded-xl bg-stone-50 px-3 py-2 text-xs text-stone-600">
                      Self-service changes are disabled here. Contact another super admin to change
                      your own role or status.
                    </p>
                  ) : (
                    <div className="mt-4 flex flex-wrap gap-2">
                      <Button
                        size="sm"
                        variant="secondary"
                        disabled={pending}
                        aria-label={`Change status for ${account.email}`}
                        onClick={() => openSelection(account, 'status')}
                      >
                        Change status
                      </Button>
                      <Button
                        size="sm"
                        variant="secondary"
                        disabled={pending}
                        aria-label={`Change role for ${account.email}`}
                        onClick={() => openSelection(account, 'role')}
                      >
                        Change role
                      </Button>
                    </div>
                  )}

                  {isSelected && !isSelf && (
                    <form className="mt-4 space-y-3 border-t border-stone-200 pt-4" onSubmit={submit}>
                      <Field label={selection.mode === 'status' ? 'New account status' : 'New role'} required>
                        <select
                          value={selection.mode === 'status' ? nextStatus : nextRole}
                          onChange={(event) =>
                            selection.mode === 'status'
                              ? setNextStatus(event.target.value as ManualUserStatus)
                              : setNextRole(event.target.value as AdminRole)
                          }
                          className={inputClass}
                        >
                          {(selection.mode === 'status' ? statusChoices : roleChoices).map((choice) => (
                            <option key={choice} value={choice}>
                              {humanizeStatus(choice)}
                            </option>
                          ))}
                        </select>
                      </Field>
                      <Field
                        label="Reason for change"
                        required
                        hint="Required (10–1000 characters) and stored in the audit log."
                        error={formError ?? undefined}
                      >
                        <textarea
                          value={reason}
                          onChange={(event) => setReason(event.target.value)}
                          className={`${inputClass} min-h-20 resize-y`}
                          maxLength={1000}
                        />
                      </Field>
                      <div className="flex flex-wrap gap-2">
                        <Button type="submit" variant="danger" loading={pending}>
                          Confirm change
                        </Button>
                        <Button type="button" variant="secondary" onClick={() => setSelection(null)}>
                          Cancel
                        </Button>
                      </div>
                    </form>
                  )}
                </Card>
              )
            })}
          </div>

          <div className="flex items-center justify-between gap-3">
            <Button
              variant="secondary"
              size="sm"
              disabled={page === 0 || users.isFetching}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
            >
              <Icon name="arrow-left" className="h-4 w-4" />
              Previous
            </Button>
            <span className="text-xs font-medium text-stone-600">
              Page {page + 1} of {Math.max(1, totalPages)}
            </span>
            <Button
              variant="secondary"
              size="sm"
              disabled={page + 1 >= totalPages || users.isFetching}
              onClick={() => setPage((value) => value + 1)}
            >
              Next
              <Icon name="arrow-right" className="h-4 w-4" />
            </Button>
          </div>
        </div>
      )}
    </AppLayout>
  )
}
