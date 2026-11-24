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
  inputClass,
} from '../components/ui'
import { listAdminAuditLogs, type AdminAuditEntry, type AuditLogQuery } from '../lib/api/admin'
import { errorMessage } from '../lib/api/errors'
import { formatDateTime, humanizeStatus } from '../lib/utils/format'

const PAGE_SIZE = 20

function AuditRow({ entry }: { entry: AdminAuditEntry }) {
  return (
    <tr className="transition hover:bg-stone-50">
      <td className="px-4 py-3">
        <p className="text-xs font-semibold text-stone-800">{entry.actorName ?? 'Unknown actor'}</p>
        <p className="mt-0.5 text-[11px] text-stone-500">
          {entry.actorRole ? humanizeStatus(entry.actorRole) : 'Role not recorded'}
        </p>
      </td>
      <td className="px-4 py-3">
        <Badge tone="info">{humanizeStatus(entry.action)}</Badge>
      </td>
      <td className="px-4 py-3 text-xs text-stone-600">
        {entry.entityType}
        {entry.entityId ? (
          <span className="mt-0.5 block font-mono text-[10px] break-all text-stone-500">
            {entry.entityId}
          </span>
        ) : null}
      </td>
      <td className="px-4 py-3 text-xs text-stone-600">{entry.reason ?? 'No reason recorded'}</td>
      <td className="px-4 py-3 font-mono text-[10px] break-all text-stone-500">
        {entry.correlationId ?? '—'}
      </td>
      <td className="px-4 py-3 text-xs text-stone-500">{formatDateTime(entry.createdAt)}</td>
    </tr>
  )
}

export default function AdminAuditPage() {
  const [action, setAction] = useState('')
  const [entityType, setEntityType] = useState('')
  const [entityId, setEntityId] = useState('')
  const [actorId, setActorId] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState<AuditLogQuery>({})

  const audit = useQuery({
    queryKey: ['admin', 'audit-logs', filters, page],
    queryFn: () => listAdminAuditLogs({ ...filters, page, size: PAGE_SIZE }),
    retry: false,
  })

  function applyFilters(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setFilters({
      action: action.trim() || undefined,
      entityType: entityType.trim() || undefined,
      entityId: entityId.trim() || undefined,
      actorId: actorId.trim() || undefined,
      from: from ? new Date(`${from}T00:00:00`).toISOString() : undefined,
      to: to ? new Date(`${to}T23:59:59`).toISOString() : undefined,
    })
  }

  function clearFilters() {
    setAction('')
    setEntityType('')
    setEntityId('')
    setActorId('')
    setFrom('')
    setTo('')
    setPage(0)
    setFilters({})
  }

  const hasFilters = Object.values(filters).some((value) => value !== undefined)
  const result = audit.data
  const totalElements = result?.totalElements ?? 0
  const totalPages = result?.totalPages ?? 0
  const firstRow = totalElements === 0 ? 0 : page * PAGE_SIZE + 1
  const lastRow = Math.min((page + 1) * PAGE_SIZE, totalElements)

  return (
    <AppLayout>
      <PageHeader
        eyebrow="Super admin"
        title="Audit explorer"
        description="Explore persisted administrative actions, including actor, target, reason and correlation id."
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
          <Field label="Action" hint="Exact action code">
            <input
              value={action}
              onChange={(event) => setAction(event.target.value)}
              className={inputClass}
              placeholder="e.g. DEPARTMENT_VERIFY"
            />
          </Field>
          <Field label="Entity type">
            <input
              value={entityType}
              onChange={(event) => setEntityType(event.target.value)}
              className={inputClass}
              placeholder="e.g. department"
            />
          </Field>
          <Field label="Entity id">
            <input
              value={entityId}
              onChange={(event) => setEntityId(event.target.value)}
              className={inputClass}
              placeholder="UUID"
            />
          </Field>
          <Field label="Actor id">
            <input
              value={actorId}
              onChange={(event) => setActorId(event.target.value)}
              className={inputClass}
              placeholder="UUID"
            />
          </Field>
          <Field label="From date">
            <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} className={inputClass} />
          </Field>
          <Field label="To date">
            <input type="date" value={to} onChange={(event) => setTo(event.target.value)} className={inputClass} />
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

      {audit.isPending && (
        <Card className="flex items-center justify-center gap-3 p-12 text-sm text-stone-500">
          <Spinner className="h-5 w-5 text-brand-600" />
          <span role="status">Loading audit log…</span>
        </Card>
      )}

      {audit.isError && (
        <div role="alert">
          <StatePanel
            tone="error"
            title="Could not load audit log"
            description={errorMessage(audit.error, 'The service did not return audit entries.')}
            action={
              <Button variant="secondary" onClick={() => void audit.refetch()}>
                Try again
              </Button>
            }
          />
        </div>
      )}

      {audit.isSuccess && totalElements === 0 && (
        <StatePanel
          icon="clipboard"
          title={hasFilters ? 'No audit entries match these filters' : 'No audit entries recorded'}
          description={
            hasFilters
              ? 'Try widening the date range or clearing a filter.'
              : 'Administrative changes will appear here once recorded.'
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

      {audit.isSuccess && totalElements > 0 && result && (
        <div className="space-y-4">
          <p role="status" className="text-xs font-medium text-stone-500">
            Showing {firstRow}–{lastRow} of {totalElements} audit entr{totalElements === 1 ? 'y' : 'ies'}
          </p>

          <Card className="hidden overflow-hidden lg:block">
            <div className="overflow-x-auto">
              <table className="w-full text-left">
                <thead className="bg-stone-50 text-[11px] font-semibold tracking-wide text-stone-500 uppercase">
                  <tr>
                    <th scope="col" className="px-4 py-3">Actor</th>
                    <th scope="col" className="px-4 py-3">Action</th>
                    <th scope="col" className="px-4 py-3">Target</th>
                    <th scope="col" className="px-4 py-3">Reason</th>
                    <th scope="col" className="px-4 py-3">Correlation id</th>
                    <th scope="col" className="px-4 py-3">Timestamp</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-stone-100">
                  {result.content.map((entry) => (
                    <AuditRow key={entry.id} entry={entry} />
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <div className="grid gap-3 lg:hidden">
            {result.content.map((entry) => (
              <Card key={entry.id} className="p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="text-sm font-semibold text-stone-900">
                      {entry.actorName ?? 'Unknown actor'}
                    </p>
                    <p className="text-[11px] text-stone-500">
                      {entry.actorRole ? humanizeStatus(entry.actorRole) : 'Role not recorded'}
                    </p>
                  </div>
                  <Badge tone="info">{humanizeStatus(entry.action)}</Badge>
                </div>
                <p className="mt-2 text-xs text-stone-600">
                  {entry.entityType}
                  {entry.entityId ? ` · ${entry.entityId}` : ''}
                </p>
                {entry.reason && <p className="mt-2 text-xs text-stone-700">{entry.reason}</p>}
                <p className="mt-2 font-mono text-[10px] break-all text-stone-500">
                  {entry.correlationId ?? 'No correlation id'}
                </p>
                <p className="mt-1 text-[11px] text-stone-500">{formatDateTime(entry.createdAt)}</p>
              </Card>
            ))}
          </div>

          <div className="flex items-center justify-between gap-3">
            <Button
              variant="secondary"
              size="sm"
              disabled={page === 0 || audit.isFetching}
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
              disabled={page + 1 >= totalPages || audit.isFetching}
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
