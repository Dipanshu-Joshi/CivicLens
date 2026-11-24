import { useEffect, useState, type FormEvent } from 'react'
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
import { errorMessage } from '../lib/api/errors'
import {
  createServiceArea,
  deleteServiceArea,
  getDepartmentProfile,
  listCapabilities,
  listServiceAreas,
  listTeamMembers,
  updateCapabilities,
  updateDepartmentProfile,
  updateServiceArea,
  updateTeamMember,
  type AdminLevel,
  type CapabilityResponse,
  type CapabilityUpdate,
  type ServiceAreaResponse,
  type ServiceAreaUpdate,
  type TeamMemberResponse,
  type TeamMemberUpdate,
} from '../lib/api/department'
import { formatDate, humanizeStatus } from '../lib/utils/format'

const adminLevels: AdminLevel[] = ['OWNER', 'ADMIN', 'OPERATOR']

interface SettingsForm {
  description: string
  contactNumber: string
  officeAddress: string
  acceptsComplaints: boolean
  autoAssignEnabled: boolean
}

interface AreaDraft extends ServiceAreaUpdate {
  coversWard: string
  coversZone: string
  jurisdictionId: string
}

function TeamRow({
  member,
  onSave,
  saving,
}: {
  member: TeamMemberResponse
  onSave: (userId: string, update: TeamMemberUpdate) => void
  saving: boolean
}) {
  const [adminLevel, setAdminLevel] = useState<AdminLevel>(member.adminLevel)
  const [primaryAdmin, setPrimaryAdmin] = useState(member.primaryAdmin)

  useEffect(() => {
    setAdminLevel(member.adminLevel)
    setPrimaryAdmin(member.primaryAdmin)
  }, [member.adminLevel, member.primaryAdmin])

  const changed = adminLevel !== member.adminLevel || primaryAdmin !== member.primaryAdmin

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-stone-200 p-4 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <p className="truncate text-sm font-semibold text-stone-900">{member.fullName}</p>
        <p className="truncate text-xs text-stone-500">{member.email}</p>
        <p className="mt-1 text-[11px] text-stone-500">
          {member.title || 'No title'} · {humanizeStatus(member.accountStatus)}
          {member.primaryAdmin ? ' · Primary admin' : ''}
        </p>
      </div>
      <div className="grid gap-3 sm:grid-cols-[minmax(120px,1fr)_auto_auto] sm:items-end">
        <Field label="Admin level">
          <select
            value={adminLevel}
            onChange={(event) => setAdminLevel(event.target.value as AdminLevel)}
            className={inputClass}
          >
            {adminLevels.map((level) => (
              <option key={level} value={level}>
                {humanizeStatus(level)}
              </option>
            ))}
          </select>
        </Field>
        <label className="flex items-center gap-2 pb-2.5 text-xs font-medium text-stone-700">
          <input
            type="checkbox"
            checked={primaryAdmin}
            onChange={(event) => setPrimaryAdmin(event.target.checked)}
            className="h-4 w-4 rounded border-stone-300 text-brand-600 focus:ring-brand-500"
          />
          Primary
        </label>
        <Button
          type="button"
          size="sm"
          loading={saving}
          disabled={!changed}
          onClick={() => onSave(member.userId, { adminLevel, primaryAdmin })}
        >
          Save
        </Button>
      </div>
    </div>
  )
}

export default function DepartmentProfilePage() {
  const queryClient = useQueryClient()

  const [settings, setSettings] = useState<SettingsForm>({
    description: '',
    contactNumber: '',
    officeAddress: '',
    acceptsComplaints: false,
    autoAssignEnabled: false,
  })
  const [settingsNotice, setSettingsNotice] = useState<string | null>(null)
  const [settingsError, setSettingsError] = useState<string | null>(null)

  const [capabilityRows, setCapabilityRows] = useState<CapabilityUpdate[]>([])
  const [capabilityNotice, setCapabilityNotice] = useState<string | null>(null)
  const [capabilityError, setCapabilityError] = useState<string | null>(null)

  const [areaDraft, setAreaDraft] = useState<AreaDraft>({
    name: '',
    boundaryWkt: '',
    coversWard: '',
    coversZone: '',
    jurisdictionId: '',
  })
  const [editingAreaId, setEditingAreaId] = useState<string | null>(null)
  const [editAreaDraft, setEditAreaDraft] = useState<ServiceAreaUpdate>({
    name: '',
    boundaryWkt: '',
    coversWard: '',
    coversZone: '',
  })
  const [areaNotice, setAreaNotice] = useState<string | null>(null)
  const [areaError, setAreaError] = useState<string | null>(null)
  const [teamError, setTeamError] = useState<string | null>(null)

  const profile = useQuery({
    queryKey: ['department', 'profile'],
    queryFn: getDepartmentProfile,
    retry: false,
  })
  const capabilities = useQuery({
    queryKey: ['department', 'capabilities'],
    queryFn: listCapabilities,
    retry: false,
  })
  const team = useQuery({
    queryKey: ['department', 'team'],
    queryFn: listTeamMembers,
    retry: false,
  })
  const areas = useQuery({
    queryKey: ['department', 'service-areas'],
    queryFn: listServiceAreas,
    retry: false,
  })

  useEffect(() => {
    if (!profile.data) return
    setSettings({
      description: profile.data.description ?? '',
      contactNumber: profile.data.contactNumber ?? '',
      officeAddress: profile.data.officeAddress ?? '',
      acceptsComplaints: profile.data.acceptsComplaints,
      autoAssignEnabled: profile.data.autoAssignEnabled,
    })
  }, [profile.data])

  useEffect(() => {
    if (!capabilities.data) return
    setCapabilityRows(
      capabilities.data.map((capability) => ({
        categoryId: capability.categoryId,
        primary: capability.primary,
        priority: capability.priority,
      })),
    )
  }, [capabilities.data])

  async function invalidateAll() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['department', 'profile'] }),
      queryClient.invalidateQueries({ queryKey: ['department', 'me'] }),
      queryClient.invalidateQueries({ queryKey: ['department', 'capabilities'] }),
      queryClient.invalidateQueries({ queryKey: ['department', 'team'] }),
      queryClient.invalidateQueries({ queryKey: ['department', 'service-areas'] }),
      queryClient.invalidateQueries({ queryKey: ['department', 'overview'] }),
    ])
  }

  const saveSettings = useMutation({
    mutationFn: () =>
      updateDepartmentProfile({
        description: settings.description,
        contactNumber: settings.contactNumber,
        officeAddress: settings.officeAddress,
        acceptsComplaints: settings.acceptsComplaints,
        autoAssignEnabled: settings.autoAssignEnabled,
      }),
    onSuccess: async () => {
      setSettingsError(null)
      setSettingsNotice('Department settings saved.')
      await invalidateAll()
    },
    onError: (error: unknown) => {
      setSettingsNotice(null)
      setSettingsError(errorMessage(error, 'Settings could not be saved.'))
    },
  })

  const saveCapabilities = useMutation({
    mutationFn: () => updateCapabilities(capabilityRows),
    onSuccess: async () => {
      setCapabilityError(null)
      setCapabilityNotice('Capabilities saved.')
      await invalidateAll()
    },
    onError: (error: unknown) => {
      setCapabilityNotice(null)
      setCapabilityError(errorMessage(error, 'Capabilities could not be saved.'))
    },
  })

  const saveTeamMember = useMutation({
    mutationFn: ({
      userId,
      update,
    }: {
      userId: string
      update: TeamMemberUpdate
    }) => updateTeamMember(userId, update),
    onSuccess: async () => {
      setTeamError(null)
      await invalidateAll()
    },
    onError: (error: unknown) => {
      setTeamError(errorMessage(error, 'Team member could not be updated.'))
    },
  })

  const createArea = useMutation({
    mutationFn: () =>
      createServiceArea({
        name: areaDraft.name.trim(),
        boundaryWkt: areaDraft.boundaryWkt.trim(),
        coversWard: areaDraft.coversWard.trim() || undefined,
        coversZone: areaDraft.coversZone.trim() || undefined,
        jurisdictionId: areaDraft.jurisdictionId.trim() || undefined,
      }),
    onSuccess: async () => {
      setAreaError(null)
      setAreaNotice('Service area created.')
      setAreaDraft({ name: '', boundaryWkt: '', coversWard: '', coversZone: '', jurisdictionId: '' })
      await invalidateAll()
    },
    onError: (error: unknown) => {
      setAreaNotice(null)
      setAreaError(errorMessage(error, 'Service area could not be created.'))
    },
  })

  const saveArea = useMutation({
    mutationFn: () =>
      updateServiceArea(editingAreaId ?? '', {
        name: editAreaDraft.name.trim(),
        boundaryWkt: editAreaDraft.boundaryWkt.trim(),
        coversWard: (editAreaDraft.coversWard ?? '').trim() || undefined,
        coversZone: (editAreaDraft.coversZone ?? '').trim() || undefined,
      }),
    onSuccess: async () => {
      setAreaError(null)
      setAreaNotice('Service area updated.')
      setEditingAreaId(null)
      setEditAreaDraft({ name: '', boundaryWkt: '', coversWard: '', coversZone: '' })
      await invalidateAll()
    },
    onError: (error: unknown) => {
      setAreaNotice(null)
      setAreaError(errorMessage(error, 'Service area could not be updated.'))
    },
  })

  const removeArea = useMutation({
    mutationFn: (areaId: string) => deleteServiceArea(areaId),
    onSuccess: async () => {
      setAreaError(null)
      setAreaNotice('Service area deactivated.')
      await invalidateAll()
    },
    onError: (error: unknown) => {
      setAreaNotice(null)
      setAreaError(errorMessage(error, 'Service area could not be deactivated.'))
    },
  })

  function submitSettings(event: FormEvent) {
    event.preventDefault()
    setSettingsNotice(null)
    saveSettings.mutate()
  }

  function submitCapabilities(event: FormEvent) {
    event.preventDefault()
    setCapabilityNotice(null)
    if (capabilityRows.length === 0) {
      setCapabilityError('Add at least one category capability before saving.')
      return
    }
    if (
      capabilityRows.some(
        (row) => !row.categoryId.trim() || !Number.isFinite(row.priority) || row.priority < 1,
      )
    ) {
      setCapabilityError('Every capability needs a category ID and a priority of at least 1.')
      return
    }
    saveCapabilities.mutate()
  }

  function submitArea(event: FormEvent) {
    event.preventDefault()
    setAreaNotice(null)
    if (!areaDraft.name.trim() || !areaDraft.boundaryWkt.trim()) {
      setAreaError('A name and boundary WKT are required.')
      return
    }
    createArea.mutate()
  }

  function submitAreaUpdate(event: FormEvent) {
    event.preventDefault()
    setAreaNotice(null)
    if (!editingAreaId || !editAreaDraft.name.trim() || !editAreaDraft.boundaryWkt.trim()) {
      setAreaError('A name and boundary WKT are required to update a service area.')
      return
    }
    saveArea.mutate()
  }

  function startEditing(area: ServiceAreaResponse) {
    setAreaError(null)
    setAreaNotice(null)
    setEditingAreaId(area.id)
    setEditAreaDraft({
      name: area.name,
      boundaryWkt: '',
      coversWard: area.coversWard ?? '',
      coversZone: area.coversZone ?? '',
    })
  }

  const capabilityMeta = new Map<string, CapabilityResponse>(
    (capabilities.data ?? []).map((item) => [item.categoryId, item] as const),
  )

  return (
    <AppLayout>
      <PageHeader
        eyebrow="Department workspace"
        title="Department profile"
        description="Identity, intake settings, capabilities, service areas and team administration."
        actions={
          <Link to="/department">
            <Button variant="secondary" size="sm" icon={<Icon name="arrow-left" className="h-4 w-4" />}>
              Overview
            </Button>
          </Link>
        }
      />

      <div className="space-y-6">
        {profile.isPending && (
          <Card className="flex items-center justify-center gap-3 p-10 text-sm text-stone-500">
            <Spinner className="h-5 w-5 text-brand-600" />
            <span role="status">Loading department profile…</span>
          </Card>
        )}
        {profile.isError && (
          <div role="alert">
            <StatePanel
              tone="error"
              title="Could not load department profile"
              description={errorMessage(profile.error, 'The service did not return your department.')}
              action={<Button variant="secondary" onClick={() => void profile.refetch()}>Try again</Button>}
            />
          </div>
        )}
        {profile.data && (
          <>
            <Card className="p-5 sm:p-6">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-[11px] font-semibold tracking-[0.16em] text-stone-500 uppercase">
                    {profile.data.departmentCode}
                  </p>
                  <h2 className="mt-1 text-xl font-semibold text-stone-900">{profile.data.name}</h2>
                  <p className="mt-1 text-sm text-stone-500">
                    {profile.data.officialEmail} · {humanizeStatus(profile.data.departmentType)}
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Badge
                    tone={
                      profile.data.verificationStatus === 'VERIFIED'
                        ? 'success'
                        : profile.data.verificationStatus === 'REJECTED' ||
                            profile.data.verificationStatus === 'SUSPENDED'
                          ? 'danger'
                          : 'warn'
                    }
                  >
                    {humanizeStatus(profile.data.verificationStatus)}
                  </Badge>
                  <Badge tone={profile.data.active ? 'success' : 'neutral'}>
                    {profile.data.active ? 'Active' : 'Inactive'}
                  </Badge>
                </div>
              </div>
              <dl className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <div className="rounded-xl border border-stone-200 p-3">
                  <dt className="text-[11px] font-semibold tracking-wide text-stone-500 uppercase">Intake</dt>
                  <dd className="mt-1 text-sm text-stone-900">
                    {profile.data.acceptsComplaints ? 'Accepting complaints' : 'Paused'}
                  </dd>
                </div>
                <div className="rounded-xl border border-stone-200 p-3">
                  <dt className="text-[11px] font-semibold tracking-wide text-stone-500 uppercase">Auto-assign</dt>
                  <dd className="mt-1 text-sm text-stone-900">
                    {profile.data.autoAssignEnabled ? 'Enabled' : 'Disabled'}
                  </dd>
                </div>
                <div className="rounded-xl border border-stone-200 p-3">
                  <dt className="text-[11px] font-semibold tracking-wide text-stone-500 uppercase">Routing priority</dt>
                  <dd className="mt-1 text-sm text-stone-900">{profile.data.routingPriority}</dd>
                </div>
                <div className="rounded-xl border border-stone-200 p-3">
                  <dt className="text-[11px] font-semibold tracking-wide text-stone-500 uppercase">Max open</dt>
                  <dd className="mt-1 text-sm text-stone-900">
                    {profile.data.maxOpenComplaints ?? 'Not configured'}
                  </dd>
                </div>
              </dl>
              {[profile.data.description, profile.data.officeAddress].filter(Boolean).length > 0 && (
                <div className="mt-4 space-y-2 text-sm text-stone-600">
                  {profile.data.description && <p>{profile.data.description}</p>}
                  {profile.data.officeAddress && (
                    <p className="flex items-start gap-2">
                      <Icon name="location" className="mt-0.5 h-4 w-4 shrink-0 text-brand-600" />
                      {profile.data.officeAddress}
                    </p>
                  )}
                </div>
              )}
            </Card>

            <Card className="p-5 sm:p-6">
              <h2 className="text-base font-semibold text-stone-900">Intake settings</h2>
              <p className="mt-1 text-xs text-stone-500">
                Changes are validated and persisted by the department API.
              </p>
              {settingsNotice && (
                <p role="status" className="mt-4 rounded-xl border border-brand-200 bg-brand-50 px-3 py-2 text-sm text-brand-800">
                  {settingsNotice}
                </p>
              )}
              {settingsError && (
                <p role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                  {settingsError}
                </p>
              )}
              <form className="mt-5 grid gap-4 sm:grid-cols-2" onSubmit={submitSettings}>
                <div className="sm:col-span-2">
                  <Field label="Description">
                    <textarea
                      value={settings.description}
                      onChange={(event) => setSettings({ ...settings, description: event.target.value })}
                      className={`${inputClass} min-h-20 resize-y`}
                      maxLength={1000}
                    />
                  </Field>
                </div>
                <Field label="Contact number">
                  <input
                    value={settings.contactNumber}
                    onChange={(event) => setSettings({ ...settings, contactNumber: event.target.value })}
                    className={inputClass}
                    maxLength={100}
                  />
                </Field>
                <Field label="Office address">
                  <input
                    value={settings.officeAddress}
                    onChange={(event) => setSettings({ ...settings, officeAddress: event.target.value })}
                    className={inputClass}
                    maxLength={500}
                  />
                </Field>
                <label className="flex items-center gap-2 text-sm text-stone-700">
                  <input
                    type="checkbox"
                    checked={settings.acceptsComplaints}
                    onChange={(event) =>
                      setSettings({ ...settings, acceptsComplaints: event.target.checked })
                    }
                    className="h-4 w-4 rounded border-stone-300 text-brand-600 focus:ring-brand-500"
                  />
                  Accept new complaints
                </label>
                <label className="flex items-center gap-2 text-sm text-stone-700">
                  <input
                    type="checkbox"
                    checked={settings.autoAssignEnabled}
                    onChange={(event) =>
                      setSettings({ ...settings, autoAssignEnabled: event.target.checked })
                    }
                    className="h-4 w-4 rounded border-stone-300 text-brand-600 focus:ring-brand-500"
                  />
                  Enable automatic assignment
                </label>
                <div className="sm:col-span-2">
                  <Button type="submit" loading={saveSettings.isPending}>
                    Save settings
                  </Button>
                </div>
              </form>
            </Card>
          </>
        )}

        <Card className="p-5 sm:p-6">
          <h2 className="text-base font-semibold text-stone-900">Category capabilities</h2>
          <p className="mt-1 text-xs text-stone-500">
            The saved list replaces all current capabilities. A validated department must handle at
            least one active category.
          </p>
          {capabilities.isPending && (
            <p role="status" className="mt-4 text-xs text-stone-500">Loading capabilities…</p>
          )}
          {capabilities.isError && (
            <div role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {errorMessage(capabilities.error, 'Capabilities could not be loaded.')}
              <Button variant="ghost" size="sm" className="ml-2" onClick={() => void capabilities.refetch()}>
                Retry
              </Button>
            </div>
          )}
          {capabilityNotice && (
            <p role="status" className="mt-4 rounded-xl border border-brand-200 bg-brand-50 px-3 py-2 text-sm text-brand-800">
              {capabilityNotice}
            </p>
          )}
          {capabilityError && (
            <p role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {capabilityError}
            </p>
          )}
          {capabilities.isSuccess && (
            <form className="mt-5 space-y-3" onSubmit={submitCapabilities}>
              {capabilityRows.length === 0 && (
                <p className="rounded-xl bg-stone-50 px-4 py-5 text-center text-sm text-stone-500">
                  No capabilities configured. Add a category below.
                </p>
              )}
              {capabilityRows.map((row, index) => {
                const meta = capabilityMeta.get(row.categoryId)
                return (
                  <div
                    key={`${row.categoryId}-${index}`}
                    className="grid gap-3 rounded-xl border border-stone-200 p-3 sm:grid-cols-[2fr_auto_100px_auto] sm:items-end"
                  >
                    <Field label={`Category ${index + 1}`}>
                      {meta ? (
                        <div className="rounded-xl border border-stone-200 bg-stone-50 px-3 py-2.5 text-sm text-stone-800">
                          {meta.categoryName} <span className="text-stone-500">({meta.categoryCode})</span>
                        </div>
                      ) : (
                        <input
                          value={row.categoryId}
                          onChange={(event) =>
                            setCapabilityRows((rows) =>
                              rows.map((item, i) =>
                                i === index ? { ...item, categoryId: event.target.value } : item,
                              ),
                            )
                          }
                          className={inputClass}
                          placeholder="Category ID"
                        />
                      )}
                    </Field>
                    <label className="flex items-center gap-2 pb-2.5 text-xs font-medium text-stone-700">
                      <input
                        type="checkbox"
                        checked={row.primary}
                        onChange={(event) =>
                          setCapabilityRows((rows) =>
                            rows.map((item, i) =>
                              i === index
                                ? {
                                    ...item,
                                    primary: event.target.checked,
                                  }
                                : item,
                            ),
                          )
                        }
                        className="h-4 w-4 rounded border-stone-300 text-brand-600 focus:ring-brand-500"
                      />
                      Primary
                    </label>
                    <Field label="Priority">
                      <input
                        type="number"
                        min={1}
                        max={1000}
                        value={row.priority}
                        onChange={(event) =>
                          setCapabilityRows((rows) =>
                            rows.map((item, i) =>
                              i === index
                                ? { ...item, priority: Number(event.target.value) }
                                : item,
                            ),
                          )
                        }
                        className={inputClass}
                      />
                    </Field>
                    <Button
                      type="button"
                      variant="danger"
                      size="sm"
                      className="mb-0.5"
                      onClick={() =>
                        setCapabilityRows((rows) => rows.filter((_, i) => i !== index))
                      }
                    >
                      Remove
                    </Button>
                  </div>
                )
              })}
              <div className="flex flex-wrap gap-2">
                <Button
                  type="button"
                  variant="secondary"
                  size="sm"
                  onClick={() => setCapabilityRows((rows) => [...rows, { categoryId: '', primary: false, priority: 1 }])}
                >
                  Add category
                </Button>
                <Button type="submit" loading={saveCapabilities.isPending}>
                  Save capabilities
                </Button>
              </div>
            </form>
          )}
        </Card>

        <Card className="p-5 sm:p-6">
          <h2 className="text-base font-semibold text-stone-900">Team members</h2>
          <p className="mt-1 text-xs text-stone-500">
            Admin level and primary-admin flags are written through the department API.
          </p>
          {teamError && (
            <p role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {teamError}
            </p>
          )}
          {team.isPending && (
            <p role="status" className="mt-4 text-xs text-stone-500">Loading team…</p>
          )}
          {team.isError && (
            <div role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {errorMessage(team.error, 'The team could not be loaded.')}
              <Button variant="ghost" size="sm" className="ml-2" onClick={() => void team.refetch()}>
                Retry
              </Button>
            </div>
          )}
          {team.isSuccess && team.data.length === 0 && (
            <p className="mt-4 text-sm text-stone-500">No team members were returned.</p>
          )}
          {team.isSuccess && team.data.length > 0 && (
            <div className="mt-4 space-y-3">
              {team.data.map((member) => (
                <TeamRow
                  key={member.userId}
                  member={member}
                  saving={saveTeamMember.isPending}
                  onSave={(userId, update) => saveTeamMember.mutate({ userId, update })}
                />
              ))}
            </div>
          )}
        </Card>

        <Card className="p-5 sm:p-6">
          <h2 className="text-base font-semibold text-stone-900">Service areas</h2>
          <p className="mt-1 text-xs text-stone-500">
            Boundaries are stored as WKT and validated by the backend before auto-assignment uses
            them.
          </p>
          {areaNotice && (
            <p role="status" className="mt-4 rounded-xl border border-brand-200 bg-brand-50 px-3 py-2 text-sm text-brand-800">
              {areaNotice}
            </p>
          )}
          {areaError && (
            <p role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {areaError}
            </p>
          )}
          {areas.isPending && (
            <p role="status" className="mt-4 text-xs text-stone-500">Loading service areas…</p>
          )}
          {areas.isError && (
            <div role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
              {errorMessage(areas.error, 'Service areas could not be loaded.')}
              <Button variant="ghost" size="sm" className="ml-2" onClick={() => void areas.refetch()}>
                Retry
              </Button>
            </div>
          )}
          {areas.isSuccess && (
            <>
              <div className="mt-4 space-y-3">
                {areas.data.length === 0 && (
                  <p className="rounded-xl bg-stone-50 px-4 py-5 text-center text-sm text-stone-500">
                    No service areas are configured.
                  </p>
                )}
                {areas.data.map((area) => (
                  <div key={area.id} className="rounded-xl border border-stone-200 p-4">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="text-sm font-semibold text-stone-900">{area.name}</p>
                          <Badge tone={area.active ? 'success' : 'neutral'}>
                            {area.active ? 'Active' : 'Inactive'}
                          </Badge>
                        </div>
                        <p className="mt-1 text-xs text-stone-500">
                          {[area.coversWard, area.coversZone, area.jurisdictionCode]
                            .filter(Boolean)
                            .join(' · ') || 'No ward, zone or jurisdiction tag'}
                        </p>
                        <p className="mt-1 text-[11px] text-stone-400">
                          Added {formatDate(area.createdAt)}
                        </p>
                      </div>
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() =>
                            editingAreaId === area.id ? setEditingAreaId(null) : startEditing(area)
                          }
                        >
                          {editingAreaId === area.id ? 'Cancel' : 'Edit'}
                        </Button>
                        <Button
                          size="sm"
                          variant="danger"
                          loading={removeArea.isPending}
                          onClick={() => {
                            if (
                              typeof window === 'undefined' ||
                              window.confirm(`Deactivate service area "${area.name}"?`)
                            ) {
                              removeArea.mutate(area.id)
                            }
                          }}
                        >
                          Deactivate
                        </Button>
                      </div>
                    </div>
                    {editingAreaId === area.id && (
                      <form className="mt-4 grid gap-3 sm:grid-cols-2" onSubmit={submitAreaUpdate}>
                        <Field label="Name" required>
                          <input
                            value={editAreaDraft.name}
                            onChange={(event) =>
                              setEditAreaDraft({ ...editAreaDraft, name: event.target.value })
                            }
                            className={inputClass}
                          />
                        </Field>
                        <Field label="Covers ward">
                          <input
                            value={editAreaDraft.coversWard ?? ''}
                            onChange={(event) =>
                              setEditAreaDraft({ ...editAreaDraft, coversWard: event.target.value })
                            }
                            className={inputClass}
                          />
                        </Field>
                        <Field label="Covers zone">
                          <input
                            value={editAreaDraft.coversZone ?? ''}
                            onChange={(event) =>
                              setEditAreaDraft({ ...editAreaDraft, coversZone: event.target.value })
                            }
                            className={inputClass}
                          />
                        </Field>
                        <div className="sm:col-span-2">
                          <Field
                            label="Boundary WKT"
                            required
                            hint="The list API does not return WKT. Re-enter the polygon to update it."
                          >
                            <textarea
                              value={editAreaDraft.boundaryWkt}
                              onChange={(event) =>
                                setEditAreaDraft({ ...editAreaDraft, boundaryWkt: event.target.value })
                              }
                              className={`${inputClass} min-h-20 resize-y font-mono text-xs`}
                              placeholder="POLYGON ((...))"
                            />
                          </Field>
                        </div>
                        <div className="sm:col-span-2">
                          <Button type="submit" loading={saveArea.isPending}>
                            Save service area
                          </Button>
                        </div>
                      </form>
                    )}
                  </div>
                ))}
              </div>

              <form className="mt-6 space-y-3 border-t border-stone-200 pt-5" onSubmit={submitArea}>
                <h3 className="text-sm font-semibold text-stone-900">Add a service area</h3>
                <div className="grid gap-3 sm:grid-cols-2">
                  <Field label="Name" required>
                    <input
                      value={areaDraft.name}
                      onChange={(event) => setAreaDraft({ ...areaDraft, name: event.target.value })}
                      className={inputClass}
                      placeholder="Central ward"
                    />
                  </Field>
                  <Field label="Jurisdiction ID (optional)">
                    <input
                      value={areaDraft.jurisdictionId ?? ''}
                      onChange={(event) =>
                        setAreaDraft({ ...areaDraft, jurisdictionId: event.target.value })
                      }
                      className={inputClass}
                    />
                  </Field>
                  <Field label="Covers ward (optional)">
                    <input
                      value={areaDraft.coversWard ?? ''}
                      onChange={(event) =>
                        setAreaDraft({ ...areaDraft, coversWard: event.target.value })
                      }
                      className={inputClass}
                    />
                  </Field>
                  <Field label="Covers zone (optional)">
                    <input
                      value={areaDraft.coversZone ?? ''}
                      onChange={(event) =>
                        setAreaDraft({ ...areaDraft, coversZone: event.target.value })
                      }
                      className={inputClass}
                    />
                  </Field>
                  <div className="sm:col-span-2">
                    <Field label="Boundary WKT" required hint="For example: POLYGON ((0 0, 0 1, 1 1, 1 0, 0 0))">
                      <textarea
                        value={areaDraft.boundaryWkt}
                        onChange={(event) =>
                          setAreaDraft({ ...areaDraft, boundaryWkt: event.target.value })
                        }
                        className={`${inputClass} min-h-24 resize-y font-mono text-xs`}
                      />
                    </Field>
                  </div>
                </div>
                <Button type="submit" loading={createArea.isPending}>
                  Add service area
                </Button>
              </form>
            </>
          )}
        </Card>
      </div>
    </AppLayout>
  )
}
