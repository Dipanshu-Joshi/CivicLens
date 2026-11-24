import { useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
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
  createAdminCategory,
  createAdminJurisdiction,
  listAdminCategories,
  listAdminDepartments,
  listAdminJurisdictions,
  listAdminServiceAreas,
  updateAdminCategory,
  type AdminCategory,
  type ServiceAreaQuery,
} from '../lib/api/admin'
import { errorMessage } from '../lib/api/errors'
import { DEMO_ENABLED } from '../demo/feature'
import { formatDate } from '../lib/utils/format'

function CategoryTree({
  categories,
  onToggle,
  busyId,
}: {
  categories: AdminCategory[]
  onToggle?: (category: AdminCategory) => void
  busyId?: string | null
}) {
  if (categories.length === 0) {
    return <p className="mt-4 text-xs text-stone-500">No categories are configured.</p>
  }
  return (
    <ul className="mt-4 space-y-2">
      {categories.map((category) => (
        <li key={category.id} className="rounded-xl border border-stone-200 p-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <p className="text-sm font-semibold text-stone-900">{category.name}</p>
              <p className="mt-0.5 font-mono text-[11px] text-stone-500">{category.code}</p>
            </div>
            <div className="flex items-center gap-2">
              <Badge tone={category.active ? 'success' : 'neutral'}>
                {category.active ? 'Active' : 'Inactive'}
              </Badge>
              {onToggle && (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  loading={busyId === category.id}
                  onClick={() => onToggle(category)}
                >
                  {category.active ? 'Deactivate' : 'Activate'}
                </Button>
              )}
            </div>
          </div>
          {category.description && (
            <p className="mt-2 text-xs text-stone-600">{category.description}</p>
          )}
          {category.children.length > 0 && (
            <ul className="mt-3 grid gap-2 border-l-2 border-brand-100 pl-3 sm:grid-cols-2">
              {category.children.map((child) => (
                <li key={child.id} className="flex items-center justify-between gap-2 text-xs">
                  <span className="text-stone-700">
                    {child.name} <span className="font-mono text-stone-400">({child.code})</span>
                  </span>
                  <div className="flex items-center gap-2">
                    <Badge tone={child.active ? 'success' : 'neutral'}>
                      {child.active ? 'Active' : 'Inactive'}
                    </Badge>
                    {onToggle && (
                      <button
                        type="button"
                        disabled={busyId === child.id}
                        onClick={() => onToggle(child)}
                        className="text-[11px] font-semibold text-brand-700 hover:underline disabled:opacity-50"
                      >
                        {child.active ? 'Deactivate' : 'Activate'}
                      </button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </li>
      ))}
    </ul>
  )
}

export default function AdminPlatformPage() {
  const queryClient = useQueryClient()
  const [departmentId, setDepartmentId] = useState('')
  const [active, setActive] = useState('')
  const [filters, setFilters] = useState<ServiceAreaQuery>({})
  const [categoryDraft, setCategoryDraft] = useState({ code: '', name: '', description: '', parentId: '' })
  const [jurisdictionDraft, setJurisdictionDraft] = useState({ code: '', name: '', description: '' })
  const [busy, setBusy] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const categories = useQuery({
    queryKey: ['admin', 'reference', 'categories'],
    queryFn: () => listAdminCategories(true),
    retry: false,
  })
  const jurisdictions = useQuery({
    queryKey: ['admin', 'reference', 'jurisdictions'],
    queryFn: () => listAdminJurisdictions(true),
    retry: false,
  })
  const departments = useQuery({
    queryKey: ['admin', 'departments'],
    queryFn: () => listAdminDepartments(),
    retry: false,
  })
  const serviceAreas = useQuery({
    queryKey: ['admin', 'reference', 'service-areas', filters],
    queryFn: () => listAdminServiceAreas(filters),
    retry: false,
  })

  async function refreshReference(keys: string[][]) {
    await Promise.all(keys.map((key) => queryClient.invalidateQueries({ queryKey: key })))
  }

  async function addCategory(event: FormEvent) {
    event.preventDefault()
    setBusy('add-category')
    setNotice(null)
    setFormError(null)
    try {
      await createAdminCategory({
        code: categoryDraft.code.trim().toUpperCase(),
        name: categoryDraft.name.trim(),
        description: categoryDraft.description.trim() || undefined,
        parentId: categoryDraft.parentId || undefined,
      })
      setCategoryDraft({ code: '', name: '', description: '', parentId: '' })
      setNotice('Demo category added to local reference data.')
      await refreshReference([
        ['admin', 'reference', 'categories'],
        ['meta', 'categories'],
      ])
    } catch (err) {
      setFormError(errorMessage(err, 'Could not add the demo category'))
    } finally {
      setBusy(null)
    }
  }

  async function toggleCategory(category: AdminCategory) {
    setBusy(category.id)
    setNotice(null)
    setFormError(null)
    try {
      await updateAdminCategory(category.id, { active: !category.active })
      setNotice(`${category.name} is now ${category.active ? 'inactive' : 'active'} in the demo.`)
      await refreshReference([
        ['admin', 'reference', 'categories'],
        ['meta', 'categories'],
      ])
    } catch (err) {
      setFormError(errorMessage(err, 'Could not update the demo category'))
    } finally {
      setBusy(null)
    }
  }

  async function addJurisdiction(event: FormEvent) {
    event.preventDefault()
    setBusy('add-jurisdiction')
    setNotice(null)
    setFormError(null)
    try {
      await createAdminJurisdiction({
        code: jurisdictionDraft.code.trim().toUpperCase(),
        name: jurisdictionDraft.name.trim(),
        description: jurisdictionDraft.description.trim() || undefined,
      })
      setJurisdictionDraft({ code: '', name: '', description: '' })
      setNotice('Demo jurisdiction added to local reference data.')
      await refreshReference([['admin', 'reference', 'jurisdictions']])
    } catch (err) {
      setFormError(errorMessage(err, 'Could not add the demo jurisdiction'))
    } finally {
      setBusy(null)
    }
  }

  function applyFilters(event: FormEvent) {
    event.preventDefault()
    setFilters({
      departmentId: departmentId || undefined,
      active: active === '' ? undefined : active === 'true',
    })
  }

  function clearFilters() {
    setDepartmentId('')
    setActive('')
    setFilters({})
  }

  const hasFilters = Object.values(filters).some((value) => value !== undefined)

  return (
    <AppLayout>
      <PageHeader
        eyebrow="Super admin"
        title="Platform reference data"
        description={
          DEMO_ENABLED
            ? 'Local reference data: categories and jurisdictions can be added or toggled in demo mode. Changes stay in this browser.'
            : 'Read-only oversight of categories, jurisdictions and platform-wide service areas. Geometry editing is not exposed in this console.'
        }
        actions={
          <Link to="/admin">
            <Button variant="secondary" size="sm" icon={<Icon name="arrow-left" className="h-4 w-4" />}>
              Overview
            </Button>
          </Link>
        }
      />

      {notice && (
        <p role="status" className="mb-4 rounded-xl border border-brand-200 bg-brand-50 px-3 py-2 text-xs font-medium text-brand-800">
          {notice}
        </p>
      )}
      {formError && (
        <p role="alert" className="mb-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
          {formError}
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="p-5 sm:p-6">
          <div className="flex items-center justify-between gap-2">
            <div>
              <h2 className="text-base font-semibold text-stone-900">Categories</h2>
              <p className="mt-1 text-xs text-stone-500">Inactive entries are included.</p>
            </div>
            {categories.isError && (
              <Button variant="ghost" size="sm" onClick={() => void categories.refetch()}>
                Retry
              </Button>
            )}
          </div>
          {categories.isPending && (
            <p role="status" className="mt-4 text-xs text-stone-500">Loading categories…</p>
          )}
          {categories.isError && (
            <div role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
              {errorMessage(categories.error, 'Could not load categories.')}
            </div>
          )}
          {categories.data && (
            <CategoryTree
              categories={categories.data}
              onToggle={DEMO_ENABLED ? (category) => void toggleCategory(category) : undefined}
              busyId={busy}
            />
          )}
          {DEMO_ENABLED && (
            <form onSubmit={addCategory} className="mt-5 space-y-3 border-t border-stone-200 pt-5">
              <h3 className="text-xs font-semibold tracking-wide text-stone-700 uppercase">
                Add a demo category
              </h3>
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Code">
                  <input
                    value={categoryDraft.code}
                    onChange={(event) => setCategoryDraft({ ...categoryDraft, code: event.target.value })}
                    className={inputClass}
                    required
                  />
                </Field>
                <Field label="Name">
                  <input
                    value={categoryDraft.name}
                    onChange={(event) => setCategoryDraft({ ...categoryDraft, name: event.target.value })}
                    className={inputClass}
                    required
                  />
                </Field>
              </div>
              <Field label="Description (optional)">
                <input
                  value={categoryDraft.description}
                  onChange={(event) => setCategoryDraft({ ...categoryDraft, description: event.target.value })}
                  className={inputClass}
                />
              </Field>
              <Field label="Parent category (optional)">
                <select
                  value={categoryDraft.parentId}
                  onChange={(event) => setCategoryDraft({ ...categoryDraft, parentId: event.target.value })}
                  className={inputClass}
                >
                  <option value="">Top level</option>
                  {categories.data?.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </select>
              </Field>
              <Button type="submit" size="sm" loading={busy === 'add-category'}>
                Add demo category
              </Button>
            </form>
          )}
        </Card>

        <Card className="p-5 sm:p-6">
          <div className="flex items-center justify-between gap-2">
            <div>
              <h2 className="text-base font-semibold text-stone-900">Jurisdictions</h2>
              <p className="mt-1 text-xs text-stone-500">Routing boundaries registered on the platform.</p>
            </div>
            {jurisdictions.isError && (
              <Button variant="ghost" size="sm" onClick={() => void jurisdictions.refetch()}>
                Retry
              </Button>
            )}
          </div>
          {jurisdictions.isPending && (
            <p role="status" className="mt-4 text-xs text-stone-500">Loading jurisdictions…</p>
          )}
          {jurisdictions.isError && (
            <div role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
              {errorMessage(jurisdictions.error, 'Could not load jurisdictions.')}
            </div>
          )}
          {jurisdictions.data && jurisdictions.data.length === 0 && (
            <p className="mt-4 rounded-xl bg-stone-50 px-4 py-6 text-center text-xs text-stone-500">
              No jurisdictions have been configured.
            </p>
          )}
          {jurisdictions.data && jurisdictions.data.length > 0 && (
            <div className="mt-4 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-stone-50 text-[11px] font-semibold tracking-wide text-stone-500 uppercase">
                  <tr>
                    <th scope="col" className="px-3 py-2">Name</th>
                    <th scope="col" className="px-3 py-2">Code</th>
                    <th scope="col" className="px-3 py-2">State</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-stone-100">
                  {jurisdictions.data.map((jurisdiction) => (
                    <tr key={jurisdiction.id}>
                      <td className="px-3 py-2">
                        <p className="font-medium text-stone-800">{jurisdiction.name}</p>
                        {jurisdiction.description && (
                          <p className="mt-0.5 text-xs text-stone-500">{jurisdiction.description}</p>
                        )}
                      </td>
                      <td className="px-3 py-2 font-mono text-xs text-stone-600">{jurisdiction.code}</td>
                      <td className="px-3 py-2">
                        <Badge tone={jurisdiction.active ? 'success' : 'neutral'}>
                          {jurisdiction.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {DEMO_ENABLED && (
            <form onSubmit={addJurisdiction} className="mt-5 space-y-3 border-t border-stone-200 pt-5">
              <h3 className="text-xs font-semibold tracking-wide text-stone-700 uppercase">
                Add a demo jurisdiction
              </h3>
              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Code">
                  <input
                    value={jurisdictionDraft.code}
                    onChange={(event) => setJurisdictionDraft({ ...jurisdictionDraft, code: event.target.value })}
                    className={inputClass}
                    required
                  />
                </Field>
                <Field label="Name">
                  <input
                    value={jurisdictionDraft.name}
                    onChange={(event) => setJurisdictionDraft({ ...jurisdictionDraft, name: event.target.value })}
                    className={inputClass}
                    required
                  />
                </Field>
              </div>
              <Field label="Description (optional)">
                <input
                  value={jurisdictionDraft.description}
                  onChange={(event) => setJurisdictionDraft({ ...jurisdictionDraft, description: event.target.value })}
                  className={inputClass}
                />
              </Field>
              <Button type="submit" size="sm" loading={busy === 'add-jurisdiction'}>
                Add demo jurisdiction
              </Button>
            </form>
          )}
        </Card>
      </div>

      <Card className="mt-6 p-5 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-base font-semibold text-stone-900">Service areas</h2>
            <p className="mt-1 text-xs text-stone-500">
              Platform-wide read model. Polygon geometry is not editable here.
            </p>
          </div>
          {serviceAreas.isError && (
            <Button variant="ghost" size="sm" onClick={() => void serviceAreas.refetch()}>
              Retry
            </Button>
          )}
        </div>

        <form onSubmit={applyFilters} className="mt-5 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
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
          <Field label="Active state">
            <select value={active} onChange={(event) => setActive(event.target.value)} className={inputClass}>
              <option value="">All service areas</option>
              <option value="true">Active</option>
              <option value="false">Inactive</option>
            </select>
          </Field>
          <div className="flex items-end gap-2">
            <Button type="submit" className="flex-1">Apply filters</Button>
            <Button type="button" variant="secondary" onClick={clearFilters} disabled={!hasFilters}>
              Clear
            </Button>
          </div>
        </form>

        <div className="mt-5">
          {serviceAreas.isPending && (
            <div className="flex items-center justify-center gap-3 py-10 text-xs text-stone-500">
              <Spinner className="h-5 w-5 text-brand-600" />
              <span role="status">Loading service areas…</span>
            </div>
          )}
          {serviceAreas.isError && (
            <div role="alert">
              <StatePanel
                tone="error"
                title="Could not load service areas"
                description={errorMessage(serviceAreas.error, 'The service did not return service areas.')}
                action={
                  <Button variant="secondary" onClick={() => void serviceAreas.refetch()}>
                    Try again
                  </Button>
                }
              />
            </div>
          )}
          {serviceAreas.data && serviceAreas.data.length === 0 && (
            <StatePanel
              icon="clipboard"
              title={hasFilters ? 'No service areas match these filters' : 'No service areas configured'}
              description={
                hasFilters
                  ? 'Clear the filters to see all service areas.'
                  : 'Departments have not registered any service areas.'
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
          {serviceAreas.data && serviceAreas.data.length > 0 && (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-stone-50 text-[11px] font-semibold tracking-wide text-stone-500 uppercase">
                  <tr>
                    <th scope="col" className="px-3 py-2">Area</th>
                    <th scope="col" className="px-3 py-2">Department</th>
                    <th scope="col" className="px-3 py-2">Coverage</th>
                    <th scope="col" className="px-3 py-2">State</th>
                    <th scope="col" className="px-3 py-2">Created</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-stone-100">
                  {serviceAreas.data.map((area) => (
                    <tr key={area.id}>
                      <td className="px-3 py-2 font-medium text-stone-800">{area.name}</td>
                      <td className="px-3 py-2 text-xs text-stone-600">
                        {area.departmentName ?? area.departmentId}
                      </td>
                      <td className="px-3 py-2 text-xs text-stone-600">
                        {[area.jurisdictionCode, area.coversWard, area.coversZone]
                          .filter(Boolean)
                          .join(' · ') || 'No textual coverage'}
                      </td>
                      <td className="px-3 py-2">
                        <Badge tone={area.active ? 'success' : 'neutral'}>
                          {area.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>
                      <td className="px-3 py-2 text-xs text-stone-500">{formatDate(area.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </Card>
    </AppLayout>
  )
}
