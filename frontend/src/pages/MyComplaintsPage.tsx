import { useMemo, useState, type FormEvent } from 'react'
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
  StatePanel,
  StatusBadge,
  inputClass,
} from '../components/ui'
import {
  listCategories,
  listMyComplaints,
  type Category,
  type ComplaintFilters,
  type ComplaintStatus,
} from '../lib/api/citizen'
import { errorMessage } from '../lib/api/errors'
import { formatDate, humanizeStatus, truncate } from '../lib/utils/format'

const statusOptions: ComplaintStatus[] = [
  'SUBMITTED',
  'AI_ANALYSIS_PENDING',
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

function flattenCategories(categories: Category[]): Category[] {
  return categories.flatMap((category) => [category, ...flattenCategories(category.children)])
}

export default function MyComplaintsPage() {
  const [filters, setFilters] = useState<ComplaintFilters>({})
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  const categories = useQuery({
    queryKey: ['meta', 'categories'],
    queryFn: () => listCategories(),
  })
  const allCategories = useMemo(
    () => flattenCategories(categories.data ?? []),
    [categories.data],
  )

  const complaints = useQuery({
    queryKey: ['citizen', 'complaints', filters],
    queryFn: () => listMyComplaints(filters),
  })

  function applyFilters(event: FormEvent) {
    event.preventDefault()
    setFilters({
      search: search.trim() || undefined,
      status: (status || undefined) as ComplaintStatus | undefined,
      categoryId: categoryId || undefined,
      from: from ? new Date(`${from}T00:00:00`).toISOString() : undefined,
      to: to ? new Date(`${to}T23:59:59`).toISOString() : undefined,
    })
  }

  function clearFilters() {
    setSearch('')
    setStatus('')
    setCategoryId('')
    setFrom('')
    setTo('')
    setFilters({})
  }

  const hasFilters = Object.values(filters).some(Boolean)

  return (
    <AppLayout>
      <PageHeader
        eyebrow="Citizen workspace"
        title="My complaints"
        description="Every report you have submitted, filtered and sorted from live backend data."
        actions={
          <Link to="/complaints/new">
            <Button icon={<Icon name="plus" className="h-4 w-4" />}>New report</Button>
          </Link>
        }
      />

      <Card className="mb-6 p-5">
        <form onSubmit={applyFilters} className="grid gap-4 md:grid-cols-5">
          <div className="md:col-span-2">
            <Field label="Search">
              <input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className={inputClass}
                placeholder="Search descriptions"
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
          <Field label="Category">
            <select
              value={categoryId}
              onChange={(event) => setCategoryId(event.target.value)}
              className={inputClass}
            >
              <option value="">All categories</option>
              {allCategories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </Field>
          <div className="flex items-end gap-2">
            <Button type="submit" className="flex-1">
              Apply
            </Button>
            <Button type="button" variant="secondary" onClick={clearFilters} disabled={!hasFilters}>
              Clear
            </Button>
          </div>
          <Field label="From date">
            <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} className={inputClass} />
          </Field>
          <Field label="To date">
            <input type="date" value={to} onChange={(event) => setTo(event.target.value)} className={inputClass} />
          </Field>
        </form>
      </Card>

      {complaints.isPending && (
        <Card className="p-10 text-center text-sm text-stone-500">Loading complaints…</Card>
      )}

      {complaints.isError && (
        <StatePanel
          tone="error"
          title="Could not load complaints"
          description={errorMessage(complaints.error, 'The service did not return your reports.')}
          action={<Button variant="secondary" onClick={() => void complaints.refetch()}>Try again</Button>}
        />
      )}

      {complaints.isSuccess && complaints.data.length === 0 && (
        <StatePanel
          icon="clipboard"
          title={hasFilters ? 'No complaints match these filters' : 'No complaints yet'}
          description={
            hasFilters
              ? 'Try clearing a filter to see more of your reports.'
              : 'Submit your first report to start tracking progress here.'
          }
          action={
            hasFilters ? (
              <Button variant="secondary" onClick={clearFilters}>
                Clear filters
              </Button>
            ) : (
              <Link to="/complaints/new">
                <Button>Report an issue</Button>
              </Link>
            )
          }
        />
      )}

      {complaints.isSuccess && complaints.data.length > 0 && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-xs font-medium text-stone-500">
              {complaints.data.length} {complaints.data.length === 1 ? 'report' : 'reports'}
            </p>
            {hasFilters && <Badge tone="info">Filtered</Badge>}
          </div>
          <div className="grid gap-4">
            {complaints.data.map((complaint) => (
              <Link key={complaint.id} to={`/complaints/${complaint.id}`}>
                <Card className="group p-5 transition duration-200 hover:-translate-y-0.5 hover:border-stone-400 hover:shadow-lift">
                  <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono text-xs font-semibold text-brand-700">
                          {complaint.complaintNumber}
                        </span>
                        <StatusBadge status={complaint.status} />
                        <Badge tone="neutral">{complaint.severity}</Badge>
                      </div>
                      <p className="mt-2 text-sm leading-relaxed text-stone-800">
                        {truncate(complaint.description, 180)}
                      </p>
                      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-stone-500">
                        <span className="flex items-center gap-1">
                          <Icon name="clock" className="h-3.5 w-3.5" />
                          {formatDate(complaint.createdAt)}
                        </span>
                        {complaint.categoryCode && (
                          <span className="flex items-center gap-1">
                            <Icon name="clipboard" className="h-3.5 w-3.5" />
                            {complaint.categoryCode}
                          </span>
                        )}
                        {(complaint.ward || complaint.city) && (
                          <span className="flex items-center gap-1">
                            <Icon name="location" className="h-3.5 w-3.5" />
                            {[complaint.ward, complaint.city].filter(Boolean).join(', ')}
                          </span>
                        )}
                      </div>
                    </div>
                    <Icon
                      name="arrow-right"
                      className="h-5 w-5 shrink-0 text-stone-300 transition group-hover:text-brand-600"
                    />
                  </div>
                </Card>
              </Link>
            ))}
          </div>
        </div>
      )}
    </AppLayout>
  )
}
