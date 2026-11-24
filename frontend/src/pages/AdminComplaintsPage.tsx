import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
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
  StatusBadge,
  inputClass,
} from '../components/ui'
import {
  listAdminDepartments,
  getAdminComplaintQueue,
  type AdminComplaintQuery,
  type AdminComplaintSummary,
} from '../lib/api/admin'
import type { ComplaintStatus, Severity } from '../lib/api/citizen'
import { errorMessage } from '../lib/api/errors'
import { formatDate, humanizeStatus, truncate } from '../lib/utils/format'

const PAGE_SIZE = 20

const statusOptions: ComplaintStatus[] = [
  'SUBMITTED',
  'AI_ANALYSIS_PENDING',
  'AI_ANALYSIS_COMPLETED',
  'AI_ANALYSIS_FAILED',
  'PENDING_ASSIGNMENT',
  'UNASSIGNED',
  'ASSIGNED',
  'ACKNOWLEDGED',
  'IN_PROGRESS',
  'ON_HOLD',
  'RESOLVED',
  'REOPENED',
  'REJECTED',
  'CLOSED',
]
const severityOptions: Severity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const sortOptions = [
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'createdAt,asc', label: 'Oldest first' },
]

function severityTone(severity: Severity) {
  if (severity === 'CRITICAL') return 'danger' as const
  if (severity === 'HIGH') return 'warn' as const
  return 'neutral' as const
}

function ComplaintRow({ complaint }: { complaint: AdminComplaintSummary }) {
  return (
    <tr className="transition hover:bg-stone-50">
      <td className="px-4 py-3">
        <Link
          to={`/admin/complaints/${complaint.id}`}
          className="font-mono text-xs font-semibold text-brand-700 hover:underline"
        >
          {complaint.complaintNumber}
        </Link>
        <p className="mt-1 max-w-xs text-xs text-stone-600">{truncate(complaint.description, 80)}</p>
      </td>
      <td className="px-4 py-3 text-xs text-stone-600">
        <span>{complaint.categoryCode ?? 'Uncategorised'}</span>
        {complaint.ward && <span className="mt-1 block text-stone-500">{complaint.ward}</span>}
      </td>
      <td className="px-4 py-3 text-xs text-stone-600">
        {complaint.departmentName ?? (complaint.departmentId ? 'Department ID on file' : 'Unassigned')}
      </td>
      <td className="px-4 py-3">
        <div className="flex flex-wrap gap-1.5">
          <StatusBadge status={complaint.status} />
          <Badge tone={severityTone(complaint.severity)}>{humanizeStatus(complaint.severity)}</Badge>
        </div>
        {complaint.routingOutcome && (
          <p className="mt-1 text-[11px] text-stone-500">Routing: {humanizeStatus(complaint.routingOutcome)}</p>
        )}
        {complaint.aiStatus && (
          <p className="mt-0.5 text-[11px] text-stone-500">AI: {humanizeStatus(complaint.aiStatus)}</p>
        )}
      </td>
      <td className="px-4 py-3 text-xs text-stone-500">{formatDate(complaint.createdAt)}</td>
      <td className="px-4 py-3 text-right">
        <Link
          to={`/admin/complaints/${complaint.id}`}
          aria-label={`Open ${complaint.complaintNumber}`}
          className="inline-flex text-stone-400 transition hover:text-brand-600"
        >
          <Icon name="arrow-right" className="h-4 w-4" />
        </Link>
      </td>
    </tr>
  )
}

export default function AdminComplaintsPage() {
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [categoryCode, setCategoryCode] = useState('')
  const [ward, setWard] = useState('')
  const [zone, setZone] = useState('')
  const [severity, setSeverity] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const [routingOutcome, setRoutingOutcome] = useState('')
  const [aiStatus, setAiStatus] = useState('')
  const [overdue, setOverdue] = useState(false)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [sort, setSort] = useState(sortOptions[0].value)
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<AdminComplaintQuery>({})

  const departments = useQuery({
    queryKey: ['admin', 'departments'],
    queryFn: () => listAdminDepartments(),
    retry: false,
  })
  const complaints = useQuery({
    queryKey: ['admin', 'complaints', 'queue', filters, page, sort],
    queryFn: () =>
      getAdminComplaintQueue({
        ...filters,
        page,
        size: PAGE_SIZE,
        sort,
      }),
    retry: false,
  })

  function applyFilters(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setFilters({
      search: search.trim() || undefined,
      status: (status || undefined) as ComplaintStatus | undefined,
      categoryCode: categoryCode.trim() || undefined,
      ward: ward.trim() || undefined,
      zone: zone.trim() || undefined,
      severity: (severity || undefined) as Severity | undefined,
      departmentId: departmentId || undefined,
      routingOutcome: routingOutcome.trim() || undefined,
      aiStatus: aiStatus.trim() || undefined,
      overdue: overdue || undefined,
      from: from ? new Date(`${from}T00:00:00`).toISOString() : undefined,
      to: to ? new Date(`${to}T23:59:59`).toISOString() : undefined,
    })
  }

  function clearFilters() {
    setSearch('')
    setStatus('')
    setCategoryCode('')
    setWard('')
    setZone('')
    setSeverity('')
    setDepartmentId('')
    setRoutingOutcome('')
    setAiStatus('')
    setOverdue(false)
    setFrom('')
    setTo('')
    setPage(0)
    setFilters({})
  }

  const hasFilters = Object.values(filters).some((value) => value !== undefined)
  const result = complaints.data
  const totalElements = result?.totalElements ?? 0
  const totalPages = result?.totalPages ?? 0
  const firstRow = totalElements === 0 ? 0 : page * PAGE_SIZE + 1
  const lastRow = Math.min((page + 1) * PAGE_SIZE, totalElements)

  return (
    <AppLayout>
      <PageHeader
        eyebrow="Super admin"
        title="Global complaint queue"
        description="Search every complaint on the platform, including routing and AI governance outcomes."
        actions={
          <Link to="/admin">
            <Button variant="secondary" size="sm" icon={<Icon name="arrow-left" className="h-4 w-4" />}>
              Overview
            </Button>
          </Link>
        }
      />

      <Card className="mb-6 p-5">
        <form onSubmit={applyFilters} className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <div className="sm:col-span-2 xl:col-span-2">
            <Field label="Search" hint="Complaint number or description">
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className={inputClass}
                placeholder="Search the global queue"
              />
            </Field>
          </div>
          <Field label="Status">
            <select value={status} onChange={(event) => setStatus(event.target.value)} className={inputClass}>
              <option value="">All statuses</option>
              {statusOptions.map((option) => (
                <option key={option} value={option}>
                  {humanizeStatus(option)}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Category code">
            <input
              value={categoryCode}
              onChange={(event) => setCategoryCode(event.target.value)}
              className={inputClass}
              placeholder="e.g. ROAD"
            />
          </Field>
          <Field label="Ward">
            <input
              value={ward}
              onChange={(event) => setWard(event.target.value)}
              className={inputClass}
              placeholder="e.g. WARD-12"
            />
          </Field>
          <Field label="Zone">
            <input
              value={zone}
              onChange={(event) => setZone(event.target.value)}
              className={inputClass}
              placeholder="e.g. ZONE-A"
            />
          </Field>
          <Field label="Severity">
            <select
              value={severity}
              onChange={(event) => setSeverity(event.target.value)}
              className={inputClass}
            >
              <option value="">All severities</option>
              {severityOptions.map((option) => (
                <option key={option} value={option}>
                  {humanizeStatus(option)}
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
          <Field label="Routing outcome" hint="Exact backend outcome code">
            <input
              value={routingOutcome}
              onChange={(event) => setRoutingOutcome(event.target.value)}
              className={inputClass}
              placeholder="e.g. ASSIGNED"
            />
          </Field>
          <Field label="AI status" hint="Exact backend AI status code">
            <input
              value={aiStatus}
              onChange={(event) => setAiStatus(event.target.value)}
              className={inputClass}
              placeholder="e.g. FAILED"
            />
          </Field>
          <Field label="From date">
            <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} className={inputClass} />
          </Field>
          <Field label="To date">
            <input type="date" value={to} onChange={(event) => setTo(event.target.value)} className={inputClass} />
          </Field>
          <div className="flex flex-col justify-end gap-3">
            <label className="flex items-center gap-2 text-sm text-stone-700">
              <input
                type="checkbox"
                checked={overdue}
                onChange={(event) => setOverdue(event.target.checked)}
                className="h-4 w-4 rounded border-stone-300 text-brand-600 focus:ring-brand-500"
              />
              Overdue only
            </label>
            <div className="flex gap-2">
              <Button type="submit" className="flex-1">Apply</Button>
              <Button type="button" variant="secondary" onClick={clearFilters} disabled={!hasFilters}>
                Clear
              </Button>
            </div>
          </div>
          <Field label="Sort by" hint="Applied by the queue API">
            <select
              value={sort}
              onChange={(event) => {
                setSort(event.target.value)
                setPage(0)
              }}
              className={inputClass}
            >
              {sortOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </Field>
        </form>
      </Card>

      {complaints.isPending && (
        <Card className="flex items-center justify-center gap-3 p-12 text-sm text-stone-500">
          <Spinner className="h-5 w-5 text-brand-600" />
          <span role="status">Loading complaint queue…</span>
        </Card>
      )}

      {complaints.isError && (
        <div role="alert">
          <StatePanel
            tone="error"
            title="Could not load the global complaint queue"
            description={errorMessage(complaints.error, 'The service did not return complaints.')}
            action={
              <Button variant="secondary" onClick={() => void complaints.refetch()}>
                Try again
              </Button>
            }
          />
        </div>
      )}

      {complaints.isSuccess && totalElements === 0 && (
        <StatePanel
          icon="clipboard"
          title={hasFilters ? 'No complaints match these filters' : 'The queue is empty'}
          description={
            hasFilters
              ? 'Try clearing a filter or widening the date range.'
              : 'Submitted complaints will appear here as soon as they are persisted.'
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

      {complaints.isSuccess && totalElements > 0 && result && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p role="status" className="text-xs font-medium text-stone-500">
              Showing {firstRow}–{lastRow} of {totalElements} complaint
              {totalElements === 1 ? '' : 's'}
            </p>
            {hasFilters && <Badge tone="info">Filtered</Badge>}
          </div>

          <Card className="hidden overflow-hidden sm:block">
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead className="bg-stone-50 text-[11px] font-semibold tracking-wide text-stone-500 uppercase">
                  <tr>
                    <th scope="col" className="px-4 py-3">Complaint</th>
                    <th scope="col" className="px-4 py-3">Category / ward</th>
                    <th scope="col" className="px-4 py-3">Department</th>
                    <th scope="col" className="px-4 py-3">Status / governance</th>
                    <th scope="col" className="px-4 py-3">Received</th>
                    <th scope="col" className="px-4 py-3"><span className="sr-only">Open</span></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-stone-100">
                  {result.content.map((complaint) => (
                    <ComplaintRow key={complaint.id} complaint={complaint} />
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <div className="grid gap-3 sm:hidden">
            {result.content.map((complaint) => (
              <Link key={complaint.id} to={`/admin/complaints/${complaint.id}`}>
                <Card className="p-4 transition hover:border-brand-300">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-xs font-semibold text-brand-700">
                      {complaint.complaintNumber}
                    </span>
                    <StatusBadge status={complaint.status} />
                    <Badge tone={severityTone(complaint.severity)}>
                      {humanizeStatus(complaint.severity)}
                    </Badge>
                  </div>
                  <p className="mt-2 text-sm text-stone-800">{truncate(complaint.description, 130)}</p>
                  <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-stone-500">
                    <span>{complaint.categoryCode ?? 'Uncategorised'}</span>
                    {complaint.ward && <span>{complaint.ward}</span>}
                    <span>{complaint.departmentName ?? 'Unassigned'}</span>
                    <span>{formatDate(complaint.createdAt)}</span>
                  </div>
                  {complaint.routingOutcome && (
                    <p className="mt-2 text-[11px] text-stone-500">
                      Routing: {humanizeStatus(complaint.routingOutcome)}
                    </p>
                  )}
                  {complaint.aiStatus && (
                    <p className="mt-0.5 text-[11px] text-stone-500">
                      AI: {humanizeStatus(complaint.aiStatus)}
                    </p>
                  )}
                </Card>
              </Link>
            ))}
          </div>

          <div className="flex items-center justify-between gap-3">
            <Button
              variant="secondary"
              size="sm"
              disabled={page === 0 || complaints.isFetching}
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
              disabled={page + 1 >= totalPages || complaints.isFetching}
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
