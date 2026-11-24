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
  getAdminDepartment,
  listAdminDepartments,
  updateAdminDepartmentOperations,
  updateAdminDepartmentStatus,
  type AdminDepartmentDetail,
  type AdminDepartmentQuery,
  type AdminDepartmentStatus,
  type AdminDepartmentSummary,
  type AdminVerificationStatus,
  type DepartmentOperationsPayload,
} from '../lib/api/admin'
import { errorMessage } from '../lib/api/errors'
import { formatDateTime, humanizeStatus } from '../lib/utils/format'

const verificationStatuses: AdminVerificationStatus[] = [
  'PENDING_VERIFICATION',
  'VERIFIED',
  'REJECTED',
  'SUSPENDED',
]

function statusTone(status: AdminVerificationStatus) {
  if (status === 'VERIFIED') return 'success' as const
  if (status === 'REJECTED') return 'danger' as const
  if (status === 'SUSPENDED') return 'warn' as const
  return 'info' as const
}

function reasonIsValid(reason: string): boolean {
  const length = reason.trim().length
  return length >= 10 && length <= 1000
}

const REASON_HINT = 'Required by policy and stored in the audit log (10–1000 characters).'

function OperationsForm({
  detail,
  pending,
  onSave,
  onCancel,
}: {
  detail: AdminDepartmentDetail
  pending: boolean
  onSave: (payload: DepartmentOperationsPayload) => void
  onCancel: () => void
}) {
  const [routingPriority, setRoutingPriority] = useState(String(detail.routingPriority ?? 0))
  const [maxOpenComplaints, setMaxOpenComplaints] = useState(
    detail.maxOpenComplaints === null || detail.maxOpenComplaints === undefined
      ? ''
      : String(detail.maxOpenComplaints),
  )
  const [autoAssignEnabled, setAutoAssignEnabled] = useState(
    Boolean(detail.autoAssignEnabled ?? detail.acceptsComplaints),
  )
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  function submit(event: FormEvent) {
    event.preventDefault()
    const priority = Number(routingPriority)
    const capacity = maxOpenComplaints.trim() === '' ? null : Number(maxOpenComplaints)
    if (!Number.isInteger(priority) || priority < 0 || priority > 10000) {
      setError('Routing priority must be a whole number between 0 and 10000.')
      return
    }
    if (capacity !== null && (!Number.isInteger(capacity) || capacity < 1 || capacity > 10000)) {
      setError('Maximum open complaints must be blank or a whole number between 1 and 10000.')
      return
    }
    if (!reasonIsValid(reason)) {
      setError(`Enter a reason of 10–1000 characters. ${REASON_HINT}`)
      return
    }
    if (
      !window.confirm(
        `Update routing operations for ${detail.name}? The change takes effect immediately.`,
      )
    ) {
      return
    }
    setError(null)
    onSave({
      routingPriority: priority,
      maxOpenComplaints: capacity,
      autoAssignEnabled,
      reason: reason.trim(),
    })
  }

  return (
    <form className="mt-4 space-y-3 border-t border-stone-200 pt-4" onSubmit={submit}>
      <p className="text-xs text-stone-600">
        Priority biases deterministic routing. Capacity and auto-assignment guards are enforced by
        the server.
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Routing priority (0–10000)" required>
          <input
            type="number"
            min={0}
            max={10000}
            value={routingPriority}
            onChange={(event) => setRoutingPriority(event.target.value)}
            className={inputClass}
          />
        </Field>
        <Field label="Max open complaints" hint="Leave blank for no cap.">
          <input
            type="number"
            min={1}
            max={10000}
            value={maxOpenComplaints}
            onChange={(event) => setMaxOpenComplaints(event.target.value)}
            className={inputClass}
            placeholder="No limit"
          />
        </Field>
      </div>
      <label className="flex items-center gap-2 text-sm text-stone-700">
        <input
          type="checkbox"
          checked={autoAssignEnabled}
          onChange={(event) => setAutoAssignEnabled(event.target.checked)}
          className="h-4 w-4 rounded border-stone-300 text-brand-600 focus:ring-brand-500"
        />
        Auto-assign new complaints to this department
      </label>
      <Field label="Reason for change" required hint={REASON_HINT} error={error ?? undefined}>
        <textarea
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          className={`${inputClass} min-h-20 resize-y`}
          maxLength={1000}
        />
      </Field>
      <div className="flex flex-wrap gap-2">
        <Button type="submit" loading={pending}>
          Save operations
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

interface Selection {
  department: AdminDepartmentSummary
  mode: 'status' | 'operations'
  status?: AdminDepartmentStatus
}

export default function AdminDepartmentsPage() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [verificationStatus, setVerificationStatus] = useState('')
  const [isActive, setIsActive] = useState('')
  const [departmentType, setDepartmentType] = useState('')
  const [filters, setFilters] = useState<AdminDepartmentQuery>({})
  const [selection, setSelection] = useState<Selection | null>(null)
  const [reason, setReason] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const departments = useQuery({
    queryKey: ['admin', 'departments', filters],
    queryFn: () => listAdminDepartments(filters),
    retry: false,
  })
  const detail = useQuery({
    queryKey: ['admin', 'departments', selection?.department.id, 'detail'],
    queryFn: () => getAdminDepartment(selection!.department.id),
    enabled: Boolean(selection && selection.mode === 'operations'),
    retry: false,
  })

  async function invalidate() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['admin', 'departments'] }),
      queryClient.invalidateQueries({ queryKey: ['admin', 'overview'] }),
      queryClient.invalidateQueries({ queryKey: ['departments'] }),
    ])
  }

  const statusMutation = useMutation({
    mutationFn: ({
      id,
      payload,
    }: {
      id: string
      payload: { status: AdminDepartmentStatus; reason: string }
    }) => updateAdminDepartmentStatus(id, payload),
    onSuccess: async (_result, variables) => {
      setSelection(null)
      setReason('')
      setFormError(null)
      setActionError(null)
      setNotice(
        `Department ${humanizeStatus(variables.payload.status).toLowerCase()}. The change was recorded in the audit log.`,
      )
      await invalidate()
    },
    onError: (error: unknown) => {
      setNotice(null)
      setActionError(errorMessage(error, 'The department status could not be changed.'))
    },
  })

  const operationsMutation = useMutation({
    mutationFn: ({
      id,
      payload,
    }: {
      id: string
      payload: DepartmentOperationsPayload
    }) => updateAdminDepartmentOperations(id, payload),
    onSuccess: async () => {
      setSelection(null)
      setNotice('Routing operations updated. The change was recorded in the audit log.')
      setActionError(null)
      await invalidate()
    },
    onError: (error: unknown) => {
      setNotice(null)
      setActionError(errorMessage(error, 'The routing operations could not be updated.'))
    },
  })

  function applyFilters(event: FormEvent) {
    event.preventDefault()
    setFilters({
      search: search.trim() || undefined,
      verificationStatus: (verificationStatus || undefined) as AdminVerificationStatus | undefined,
      isActive: isActive === '' ? undefined : isActive === 'true',
      departmentType: departmentType.trim() || undefined,
    })
  }

  function clearFilters() {
    setSearch('')
    setVerificationStatus('')
    setIsActive('')
    setDepartmentType('')
    setFilters({})
  }

  function openStatus(department: AdminDepartmentSummary, status: AdminDepartmentStatus) {
    setNotice(null)
    setActionError(null)
    setFormError(null)
    setReason('')
    setSelection({ department, mode: 'status', status })
  }

  function openOperations(department: AdminDepartmentSummary) {
    setNotice(null)
    setActionError(null)
    setFormError(null)
    setReason('')
    setSelection({ department, mode: 'operations' })
  }

  function submitStatus(event: FormEvent) {
    event.preventDefault()
    if (!selection?.status) return
    if (!reasonIsValid(reason)) {
      setFormError(`Enter a reason of 10–1000 characters. ${REASON_HINT}`)
      return
    }
    const verb = selection.status === 'VERIFIED' ? 'verify' : selection.status.toLowerCase()
    if (!window.confirm(`${humanizeStatus(verb)} ${selection.department.name}? This takes effect immediately.`)) {
      return
    }
    setFormError(null)
    statusMutation.mutate({
      id: selection.department.id,
      payload: { status: selection.status, reason: reason.trim() },
    })
  }

  const hasFilters = Object.values(filters).some((value) => value !== undefined)

  return (
    <AppLayout>
      <PageHeader
        eyebrow="Super admin"
        title="Department governance"
        description="Verify applications, suspend non-compliant departments and tune routing operations. Every change requires an audited reason."
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
            <Field label="Search" hint="Name, code or official email">
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className={inputClass}
                placeholder="Search departments"
              />
            </Field>
          </div>
          <Field label="Verification status">
            <select
              value={verificationStatus}
              onChange={(event) => setVerificationStatus(event.target.value)}
              className={inputClass}
            >
              <option value="">All statuses</option>
              {verificationStatuses.map((status) => (
                <option key={status} value={status}>
                  {humanizeStatus(status)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Active state">
            <select value={isActive} onChange={(event) => setIsActive(event.target.value)} className={inputClass}>
              <option value="">All departments</option>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </select>
          </Field>
          <Field label="Department type">
            <input
              value={departmentType}
              onChange={(event) => setDepartmentType(event.target.value)}
              className={inputClass}
              placeholder="e.g. MUNICIPAL"
            />
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

      {departments.isPending && (
        <Card className="flex items-center justify-center gap-3 p-12 text-sm text-stone-500">
          <Spinner className="h-5 w-5 text-brand-600" />
          <span role="status">Loading departments…</span>
        </Card>
      )}

      {departments.isError && (
        <div role="alert">
          <StatePanel
            tone="error"
            title="Could not load departments"
            description={errorMessage(departments.error, 'The service did not return departments.')}
            action={
              <Button variant="secondary" onClick={() => void departments.refetch()}>
                Try again
              </Button>
            }
          />
        </div>
      )}

      {departments.isSuccess && departments.data.length === 0 && (
        <StatePanel
          icon="clipboard"
          title={hasFilters ? 'No departments match these filters' : 'No departments registered'}
          description={
            hasFilters
              ? 'Clear a filter to widen the results.'
              : 'New department applications will appear here for verification.'
          }
          action={
            hasFilters ? (
              <Button variant="secondary" onClick={clearFilters}>
                Clear filters
              </Button>
            ) : undefined
          }
        />
      )}

      {departments.isSuccess && departments.data.length > 0 && (
        <div className="grid gap-4 lg:grid-cols-2">
          {departments.data.map((department) => {
            const isSelected = selection?.department.id === department.id
            return (
              <Card key={department.id} className="p-5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="font-semibold text-stone-900">{department.name}</h2>
                      <Badge tone={statusTone(department.verificationStatus)}>
                        {humanizeStatus(department.verificationStatus)}
                      </Badge>
                      <Badge tone={department.isActive ? 'success' : 'neutral'}>
                        {department.isActive ? 'Active' : 'Inactive'}
                      </Badge>
                    </div>
                    <p className="mt-1 font-mono text-xs text-stone-500">
                      {department.departmentCode} · {department.officialEmail}
                    </p>
                    <p className="mt-1 text-xs text-stone-500">
                      {humanizeStatus(department.departmentType)}
                      {department.city ? ` · ${department.city}` : ''}
                      {department.verifiedAt ? ` · Verified ${formatDateTime(department.verifiedAt)}` : ''}
                    </p>
                  </div>
                  <Badge tone={department.acceptsComplaints ? 'info' : 'neutral'}>
                    {department.acceptsComplaints ? 'Accepts complaints' : 'Intake closed'}
                  </Badge>
                </div>

                <div className="mt-4 flex flex-wrap gap-2">
                  {department.verificationStatus === 'PENDING_VERIFICATION' && (
                    <>
                      <Button
                        size="sm"
                        disabled={statusMutation.isPending}
                        onClick={() => openStatus(department, 'VERIFIED')}
                      >
                        Verify
                      </Button>
                      <Button
                        size="sm"
                        variant="danger"
                        disabled={statusMutation.isPending}
                        onClick={() => openStatus(department, 'REJECTED')}
                      >
                        Reject
                      </Button>
                    </>
                  )}
                  {department.verificationStatus === 'VERIFIED' && department.isActive && (
                    <Button
                      size="sm"
                      variant="danger"
                      disabled={statusMutation.isPending}
                      onClick={() => openStatus(department, 'SUSPENDED')}
                    >
                      Suspend
                    </Button>
                  )}
                  {(department.verificationStatus === 'SUSPENDED' ||
                    department.verificationStatus === 'REJECTED' ||
                    !department.isActive) && (
                    <Button
                      size="sm"
                      disabled={statusMutation.isPending}
                      onClick={() => openStatus(department, 'VERIFIED')}
                    >
                      Reactivate
                    </Button>
                  )}
                  <Button
                    size="sm"
                    variant="secondary"
                    disabled={operationsMutation.isPending || detail.isFetching}
                    onClick={() => openOperations(department)}
                  >
                    Routing operations
                  </Button>
                </div>

                {isSelected && selection.mode === 'status' && (
                  <form className="mt-4 space-y-3 border-t border-stone-200 pt-4" onSubmit={submitStatus}>
                    <p className="text-xs text-stone-600">
                      Set status to <strong>{humanizeStatus(selection.status ?? '')}</strong>.
                      Reactivation uses Verified; pending verification is not a manual target.
                    </p>
                    <Field
                      label="Reason for change"
                      required
                      hint={REASON_HINT}
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
                      <Button type="submit" variant="danger" loading={statusMutation.isPending}>
                        Confirm status change
                      </Button>
                      <Button type="button" variant="secondary" onClick={() => setSelection(null)}>
                        Cancel
                      </Button>
                    </div>
                  </form>
                )}

                {isSelected && selection.mode === 'operations' && (
                  <div className="mt-4 border-t border-stone-200 pt-4">
                    {detail.isPending && (
                      <p role="status" className="text-xs text-stone-500">Loading routing operations…</p>
                    )}
                    {detail.isError && (
                      <div role="alert">
                        <StatePanel
                          tone="error"
                          title="Could not load department details"
                          description={errorMessage(detail.error, 'Operations cannot be edited right now.')}
                          action={
                            <Button variant="secondary" size="sm" onClick={() => void detail.refetch()}>
                              Try again
                            </Button>
                          }
                        />
                      </div>
                    )}
                    {detail.data && (
                      <OperationsForm
                        detail={detail.data}
                        pending={operationsMutation.isPending}
                        onSave={(payload) =>
                          operationsMutation.mutate({ id: department.id, payload })
                        }
                        onCancel={() => setSelection(null)}
                      />
                    )}
                  </div>
                )}
              </Card>
            )
          })}
        </div>
      )}
    </AppLayout>
  )
}
