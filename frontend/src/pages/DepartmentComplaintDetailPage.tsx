import { useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { AppLayout } from '../components/AppLayout'
import { CitizenFeedbackCard } from '../components/CitizenFeedbackCard'
import { Icon } from '../components/Icon'
import { StatusTimeline } from '../components/StatusTimeline'
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
import type {
  AiAnalysisResponse,
  ComplaintPhoto,
  ComplaintStatus,
  ImageKind,
} from '../lib/api/citizen'
import { getImageViewUrl } from '../lib/api/uploads'
import { errorMessage, isNotFound } from '../lib/api/errors'
import {
  acceptDepartmentComplaint,
  addDepartmentNote,
  getDepartmentComplaint,
  listDepartmentNotes,
  rejectDepartmentComplaint,
  requestDepartmentComplaintInfo,
  requestDepartmentReassignment,
  resolveDepartmentComplaint,
  updateDepartmentComplaintStatus,
  uploadDepartmentEvidence,
  type ComplaintStatusUpdate,
  type DepartmentEvidenceKind,
  type ReassignRequestPayload,
  type RoutingDecision,
} from '../lib/api/department'
import { formatDateTime, humanizeStatus } from '../lib/utils/format'

type ActionPanel =
  | 'none'
  | 'accept'
  | 'reject'
  | 'status'
  | 'request-info'
  | 'note'
  | 'evidence'
  | 'resolve'
  | 'reassign'

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="rounded-xl border border-stone-200 p-3">
      <dt className="text-[11px] font-semibold tracking-wide text-stone-500 uppercase">{label}</dt>
      <dd className="mt-1 text-sm text-stone-800">{children}</dd>
    </div>
  )
}

function PhotoStrip({
  photos,
  urlById,
  title,
}: {
  photos: ComplaintPhoto[]
  urlById: Record<string, string>
  title: string
}) {
  return (
    <Card className="p-5 sm:p-6">
      <h2 className="text-base font-semibold text-stone-900">{title}</h2>
      {photos.length === 0 ? (
        <div className="mt-4 rounded-xl border border-dashed border-stone-300 bg-stone-50 px-4 py-6 text-center">
          <Icon name="camera" className="mx-auto h-5 w-5 text-stone-400" />
          <p className="mt-2 text-xs font-semibold text-stone-700">No confirmed images</p>
          <p className="mt-1 text-xs text-stone-500">Only server-verified images are shown.</p>
        </div>
      ) : (
        <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
          {photos.map((photo) => {
            const url = urlById[photo.id]
            return (
              <div key={photo.id}>
                {url ? (
                  <a href={url} target="_blank" rel="noreferrer">
                    <img
                      src={url}
                      alt={`${humanizeStatus(photo.imageKind)} image`}
                      className="h-28 w-full rounded-xl border border-stone-200 object-cover"
                    />
                  </a>
                ) : (
                  <div className="flex h-28 items-center justify-center rounded-xl border border-dashed border-stone-300 bg-stone-50 text-xs text-stone-500">
                    Preview unavailable
                  </div>
                )}
                <p className="mt-1 text-[10px] text-stone-500">
                  {humanizeStatus(photo.imageKind)} · {photo.uploadStatus}
                </p>
              </div>
            )
          })}
        </div>
      )}
    </Card>
  )
}

function AnalysisPanel({
  analysis,
  hasImages,
}: {
  analysis: AiAnalysisResponse | null
  hasImages: boolean
}) {
  if (!analysis) {
    return (
      <Card className="p-5 sm:p-6">
        <h2 className="text-base font-semibold text-stone-900">AI analysis</h2>
        <p className="mt-3 rounded-xl bg-stone-50 px-4 py-5 text-center text-sm text-stone-500">
          {hasImages
            ? 'No analysis record is available for this complaint.'
            : 'No photos were attached, so no image analysis was run.'}
        </p>
      </Card>
    )
  }

  const failed = ['FAILED', 'TIMEOUT', 'INVALID_SCHEMA'].includes(analysis.status)
  const pending = ['PENDING', 'PROCESSING'].includes(analysis.status)
  const tone: 'neutral' | 'warn' | 'success' | 'info' = failed || analysis.requiresHumanReview
    ? 'warn'
    : pending
      ? 'info'
      : 'success'

  return (
    <Card className="p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-stone-900">AI analysis</h2>
          <p className="mt-1 text-xs text-stone-500">Advisory only; routing is deterministic.</p>
        </div>
        <Badge tone={tone}>{humanizeStatus(analysis.status)}</Badge>
      </div>
      {analysis.requiresHumanReview && (
        <p role="status" className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          Flagged for human review. Do not rely on the suggested category without checking the report.
        </p>
      )}
      {failed && (
        <p role="status" className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          Analysis did not complete: {analysis.errorMessage ?? 'unknown error'}. The report remains actionable.
        </p>
      )}
      <dl className="mt-4 grid gap-3 sm:grid-cols-2">
        <Row label="Category">
          {analysis.categoryCode ?? 'Not identified'}
          {analysis.subcategoryCode ? ` · ${analysis.subcategoryCode}` : ''}
        </Row>
        <Row label="Severity">{analysis.severity ?? 'Not assessed'}</Row>
        <Row label="Confidence">
          {analysis.confidence === null ? '—' : `${Math.round(analysis.confidence * 100)}%`}
        </Row>
        <Row label="Safety risk">{analysis.safetyRisk ? 'Yes' : 'No'}</Row>
        <Row label="Image clarity">
          {analysis.isBlurry ? 'Blurry' : 'Usable'}
          {analysis.requiresAdditionalInfo ? ' · more info requested' : ''}
        </Row>
        <Row label="Visible damage">{analysis.visibleDamage || 'None reported'}</Row>
        {analysis.recommendedDepartmentHint && (
          <div className="sm:col-span-2">
            <Row label="Suggested department (advisory)">{analysis.recommendedDepartmentHint}</Row>
          </div>
        )}
        {analysis.description && (
          <div className="sm:col-span-2">
            <Row label="AI description">{analysis.description}</Row>
          </div>
        )}
      </dl>
    </Card>
  )
}

function RoutingPanel({ decisions }: { decisions: RoutingDecision[] }) {
  const decision = decisions.find((item) => item.current) ?? decisions[decisions.length - 1]
  if (!decision) {
    return (
      <Card className="p-5">
        <h2 className="text-sm font-semibold text-stone-900">Routing decision</h2>
        <p className="mt-3 text-xs text-stone-500">No persisted routing decision was returned.</p>
      </Card>
    )
  }

  return (
    <Card className="p-5">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-stone-900">Routing decision</h2>
        {decision.current && <Badge tone="success">Current</Badge>}
      </div>
      <dl className="mt-4 space-y-3 text-sm">
        <div>
          <dt className="text-xs text-stone-500">Outcome</dt>
          <dd className="mt-0.5 font-medium text-stone-900">
            {decision.outcome ? humanizeStatus(decision.outcome) : '—'}
          </dd>
        </div>
        <div>
          <dt className="text-xs text-stone-500">Reason</dt>
          <dd className="mt-0.5 text-stone-700">{decision.reason ?? '—'}</dd>
        </div>
        {decision.rulesetVersion && (
          <div>
            <dt className="text-xs text-stone-500">Ruleset</dt>
            <dd className="mt-0.5 font-mono text-xs text-stone-700">{decision.rulesetVersion}</dd>
          </div>
        )}
        {decision.selectedDepartmentId && (
          <div>
            <dt className="text-xs text-stone-500">Selected department ID</dt>
            <dd className="mt-0.5 font-mono text-xs break-all text-stone-700">
              {decision.selectedDepartmentId}
            </dd>
          </div>
        )}
        {decision.createdAt && (
          <div>
            <dt className="text-xs text-stone-500">Recorded</dt>
            <dd className="mt-0.5 text-xs text-stone-700">{formatDateTime(decision.createdAt)}</dd>
          </div>
        )}
      </dl>
    </Card>
  )
}

function confirms(message: string): boolean {
  return typeof window === 'undefined' || window.confirm(message)
}

interface ActionState {
  success: (message: string) => Promise<void> | void
  failure: (message: string) => void
}

function useDepartmentAction<TVariables>(
  mutationFn: (variables: TVariables) => Promise<unknown>,
  state: ActionState,
  successMessage: string,
) {
  return useMutation({
    mutationFn,
    onSuccess: async () => {
      await state.success(successMessage)
    },
    onError: (error: unknown) => {
      state.failure(errorMessage(error, 'The action could not be completed.'))
    },
  })
}

const statusChoices: ComplaintStatus[] = [
  'ACKNOWLEDGED',
  'IN_PROGRESS',
  'ON_HOLD',
  'REOPENED',
  'CLOSED',
]

export default function DepartmentComplaintDetailPage() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()

  const [panel, setPanel] = useState<ActionPanel>('none')
  const [reason, setReason] = useState('')
  const [body, setBody] = useState('')
  const [evidenceFile, setEvidenceFile] = useState<File | null>(null)
  const [evidenceKind, setEvidenceKind] = useState<DepartmentEvidenceKind>('AFTER')
  const [summary, setSummary] = useState('')
  const [targetDepartmentId, setTargetDepartmentId] = useState('')
  const [nextStatus, setNextStatus] = useState<ComplaintStatus>('IN_PROGRESS')
  const [notice, setNotice] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const complaint = useQuery({
    queryKey: ['department', 'complaint', id],
    queryFn: () => getDepartmentComplaint(id),
    retry: false,
  })
  const notes = useQuery({
    queryKey: ['department', 'complaint', id, 'notes'],
    queryFn: () => listDepartmentNotes(id),
    retry: false,
  })
  const imageIds = complaint.data?.images.map((image) => image.id).join(',') ?? ''
  const imageUrls = useQuery({
    queryKey: ['department', 'image-urls', id, imageIds],
    queryFn: async () => {
      const entries = await Promise.all(
        (complaint.data?.images ?? []).map(async (image) => {
          const view = await getImageViewUrl(image.id)
          return [image.id, view.url] as const
        }),
      )
      return Object.fromEntries(entries)
    },
    enabled: Boolean(imageIds),
    retry: false,
    staleTime: 4 * 60_000,
    refetchInterval: 4 * 60_000,
  })

  async function invalidate() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['department', 'complaint', id] }),
      queryClient.invalidateQueries({ queryKey: ['department', 'complaints'] }),
      queryClient.invalidateQueries({ queryKey: ['department', 'overview'] }),
      queryClient.invalidateQueries({ queryKey: ['notifications', 'list'] }),
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unread'] }),
    ])
  }

  function clearFields() {
    setReason('')
    setBody('')
    setEvidenceFile(null)
    setEvidenceKind('AFTER')
    setSummary('')
    setTargetDepartmentId('')
  }

  const actionState: ActionState = {
    success: async (message) => {
      setActionError(null)
      setNotice(message)
      setPanel('none')
      clearFields()
      await invalidate()
    },
    failure: (message) => {
      setNotice(null)
      setActionError(message)
    },
  }

  const accept = useDepartmentAction<void>(
    () => acceptDepartmentComplaint(id),
    actionState,
    'Complaint accepted.',
  )
  const reject = useDepartmentAction<string>(
    (value) => rejectDepartmentComplaint(id, value),
    actionState,
    'Complaint rejected.',
  )
  const statusUpdate = useDepartmentAction<ComplaintStatusUpdate>(
    (value) => updateDepartmentComplaintStatus(id, value),
    actionState,
    'Status updated.',
  )
  const requestInfo = useDepartmentAction<string>(
    (value) => requestDepartmentComplaintInfo(id, value),
    actionState,
    'Information request sent to the citizen.',
  )
  const note = useDepartmentAction<string>(
    (value) => addDepartmentNote(id, value),
    actionState,
    'Internal note added.',
  )
  const evidence = useDepartmentAction<{ file: File; kind: DepartmentEvidenceKind }>(
    ({ file, kind }) => uploadDepartmentEvidence(id, file, kind),
    actionState,
    'Evidence image attached.',
  )
  const resolve = useDepartmentAction<string>(
    (value) => resolveDepartmentComplaint(id, value),
    actionState,
    'Complaint resolved.',
  )
  const reassign = useDepartmentAction<ReassignRequestPayload>(
    (value) => requestDepartmentReassignment(id, value),
    actionState,
    'Reassignment request submitted for review.',
  )

  if (complaint.isPending) {
    return (
      <AppLayout>
        <Card className="flex items-center justify-center gap-3 p-12 text-sm text-stone-500">
          <Spinner className="h-5 w-5 text-brand-600" />
          <span role="status">Loading complaint…</span>
        </Card>
      </AppLayout>
    )
  }

  if (complaint.isError || !complaint.data) {
    return (
      <AppLayout>
        <StatePanel
          tone="error"
          title={isNotFound(complaint.error) ? 'Complaint not found' : 'Could not load complaint'}
          description={errorMessage(complaint.error, 'This complaint could not be retrieved.')}
          action={
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="secondary" onClick={() => void complaint.refetch()}>
                Try again
              </Button>
              <Link to="/department/complaints">
                <Button variant="ghost">Back to complaint queue</Button>
              </Link>
            </div>
          }
        />
      </AppLayout>
    )
  }

  const data = complaint.data
  const canAccept = data.status === 'ASSIGNED'
  const terminal = ['RESOLVED', 'CLOSED', 'REJECTED'].includes(data.status)
  const verifiedEvidence = data.images.some(
    (image) =>
      (['AFTER', 'EVIDENCE'] as ImageKind[]).includes(image.imageKind) &&
      image.uploadStatus === 'VERIFIED',
  )
  const internalNotes = notes.data ?? data.internalNotes ?? []
  const citizenPhotos = data.images.filter((image) => ['CITIZEN', 'BEFORE'].includes(image.imageKind))
  const evidencePhotos = data.images.filter((image) => ['AFTER', 'EVIDENCE'].includes(image.imageKind))
  const imageUrlsById = imageUrls.data ?? {}
  const mapsUrl = `https://www.openstreetmap.org/?mlat=${data.latitude}&mlon=${data.longitude}#map=16/${data.latitude}/${data.longitude}`

  function togglePanel(next: ActionPanel) {
    setActionError(null)
    setNotice(null)
    setPanel((current) => (current === next ? 'none' : next))
  }

  function submitReject(event: FormEvent) {
    event.preventDefault()
    if (reason.trim().length < 10) {
      setActionError('Please provide a reason of at least 10 characters.')
      return
    }
    if (!confirms(`Reject ${data.complaintNumber}? The citizen will be notified.`)) return
    reject.mutate(reason.trim())
  }

  function submitStatus(event: FormEvent) {
    event.preventDefault()
    if (!confirms(`Change the status of ${data.complaintNumber} to ${humanizeStatus(nextStatus)}?`)) {
      return
    }
    statusUpdate.mutate({ status: nextStatus, reason: reason.trim() || undefined })
  }

  function submitInfo(event: FormEvent) {
    event.preventDefault()
    if (!body.trim()) {
      setActionError('Enter the message to send to the citizen.')
      return
    }
    requestInfo.mutate(body.trim())
  }

  function submitNote(event: FormEvent) {
    event.preventDefault()
    if (!body.trim()) {
      setActionError('Enter the internal note before saving.')
      return
    }
    note.mutate(body.trim())
  }

  function submitEvidence(event: FormEvent) {
    event.preventDefault()
    if (!evidenceFile) {
      setActionError('Choose a JPEG, PNG or WebP image to attach.')
      return
    }
    if (!confirms(`Upload and attach "${evidenceFile.name}" as ${evidenceKind} evidence?`)) return
    evidence.mutate({ file: evidenceFile, kind: evidenceKind })
  }

  function submitResolve(event: FormEvent) {
    event.preventDefault()
    if (!verifiedEvidence) {
      setActionError('A verified AFTER or EVIDENCE image is required before resolving.')
      return
    }
    if (summary.trim().length < 10) {
      setActionError('Provide a resolution summary of at least 10 characters.')
      return
    }
    if (!confirms(`Resolve ${data.complaintNumber} with this summary?`)) return
    resolve.mutate(summary.trim())
  }

  function submitReassign(event: FormEvent) {
    event.preventDefault()
    if (!targetDepartmentId.trim() || reason.trim().length < 10) {
      setActionError('Provide a target department ID and a reason of at least 10 characters.')
      return
    }
    if (!confirms('Submit a reassignment request for this complaint?')) return
    reassign.mutate({ toDepartmentId: targetDepartmentId.trim(), reason: reason.trim() })
  }

  const anyMutationPending =
    accept.isPending ||
    reject.isPending ||
    statusUpdate.isPending ||
    requestInfo.isPending ||
    note.isPending ||
    evidence.isPending ||
    resolve.isPending ||
    reassign.isPending

  return (
    <AppLayout>
      <PageHeader
        eyebrow="Department complaint"
        title={data.complaintNumber}
        description={`Submitted ${formatDateTime(data.createdAt)}`}
        actions={
          <>
            <StatusBadge status={data.status} />
            <Badge tone="neutral">{humanizeStatus(data.severity)}</Badge>
            <Link to="/department/complaints">
              <Button variant="secondary" size="sm" icon={<Icon name="arrow-left" className="h-4 w-4" />}>
                Queue
              </Button>
            </Link>
          </>
        }
      />

      {actionError && (
        <div role="alert" className="mb-5 rounded-xl border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700">
          {actionError}
        </div>
      )}
      {notice && (
        <div role="status" className="mb-5 rounded-xl border border-brand-200 bg-brand-50 px-3 py-2.5 text-sm text-brand-800">
          {notice}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[1.35fr_0.65fr]">
        <div className="space-y-6">
          <Card className="p-5 sm:p-6">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone="info">{data.categoryCode ?? 'Uncategorised'}</Badge>
              {data.subcategoryCode && <Badge tone="neutral">{data.subcategoryCode}</Badge>}
              {data.ward && <Badge tone="neutral">{data.ward}</Badge>}
              {data.zone && <Badge tone="neutral">{data.zone}</Badge>}
            </div>
            <h2 className="mt-4 text-base font-semibold text-stone-900">Citizen description</h2>
            <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-stone-700">
              {data.description}
            </p>
            {data.additionalInfo && (
              <p className="mt-3 rounded-xl bg-stone-50 p-3 text-sm leading-relaxed text-stone-600">
                {data.additionalInfo}
              </p>
            )}
            <div className="mt-5 grid gap-3 sm:grid-cols-2">
              <Row label="Reported at">{formatDateTime(data.createdAt)}</Row>
              <Row label="Resolution target">{formatDateTime(data.resolutionDueAt)}</Row>
              {data.resolutionSummary && (
                <div className="sm:col-span-2">
                  <Row label="Resolution summary">{data.resolutionSummary}</Row>
                </div>
              )}
            </div>
          </Card>

          <PhotoStrip photos={citizenPhotos} urlById={imageUrlsById} title="Reported photos" />
          <AnalysisPanel analysis={data.analysis} hasImages={data.images.length > 0} />

          <Card className="p-5 sm:p-6">
            <h2 className="text-base font-semibold text-stone-900">Location</h2>
            <dl className="mt-4 grid gap-3 sm:grid-cols-2">
              <Row label="Coordinates">
                {data.latitude.toFixed(5)}, {data.longitude.toFixed(5)}
                <span className="mt-1 block text-xs text-stone-500">Source: {data.locationSource}</span>
              </Row>
              <Row label="Address">
                {[data.addressLine, data.ward, data.city, data.state, data.postalCode, data.country]
                  .filter(Boolean)
                  .join(', ') || 'No address provided'}
              </Row>
            </dl>
            <a
              href={mapsUrl}
              target="_blank"
              rel="noreferrer"
              className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-brand-700 hover:underline"
            >
              <Icon name="map" className="h-3.5 w-3.5" />
              Open in map
            </a>
          </Card>

          <PhotoStrip photos={evidencePhotos} urlById={imageUrlsById} title="Resolution evidence" />

          <Card className="p-5 sm:p-6">
            <h2 className="text-base font-semibold text-stone-900">Status history</h2>
            <p className="mt-1 text-xs text-stone-500">Every recorded transition, oldest first.</p>
            <div className="mt-6">
              <StatusTimeline entries={data.timeline} />
            </div>
          </Card>

          <Card className="p-5 sm:p-6">
            <h2 className="text-base font-semibold text-stone-900">Citizen conversation</h2>
            <p className="mt-1 text-xs text-stone-500">Public comments and information requests.</p>
            <div className="mt-4 space-y-3">
              {data.comments.length === 0 ? (
                <p className="rounded-xl bg-stone-50 px-4 py-5 text-center text-sm text-stone-500">
                  No citizen-visible comments yet.
                </p>
              ) : (
                data.comments.map((comment) => (
                  <div
                    key={comment.id}
                    className={`rounded-xl border p-4 ${
                      comment.authorRole === 'DEPARTMENT_ADMIN'
                        ? 'border-brand-200 bg-brand-50/60'
                        : 'border-stone-200 bg-white'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <p className="text-xs font-semibold text-stone-800">{comment.authorName}</p>
                      <p className="text-[11px] text-stone-500">{formatDateTime(comment.createdAt)}</p>
                    </div>
                    <p className="mt-2 text-sm leading-relaxed text-stone-700">{comment.body}</p>
                  </div>
                ))
              )}
            </div>
            {panel === 'request-info' && (
              <form className="mt-5 space-y-3 border-t border-stone-200 pt-5" onSubmit={submitInfo}>
                <Field label="Message to the citizen" required hint="This becomes a public comment.">
                  <textarea
                    value={body}
                    onChange={(event) => setBody(event.target.value)}
                    className={`${inputClass} min-h-24 resize-y`}
                    maxLength={2000}
                  />
                </Field>
                <div className="flex gap-2">
                  <Button type="submit" loading={requestInfo.isPending} disabled={!body.trim()}>
                    Send request
                  </Button>
                  <Button type="button" variant="secondary" onClick={() => togglePanel('request-info')}>
                    Cancel
                  </Button>
                </div>
              </form>
            )}
          </Card>

          <CitizenFeedbackCard feedback={data.feedback} />
        </div>

        <div className="space-y-6">
          <Card className="p-5">
            <h2 className="text-sm font-semibold text-stone-900">Ownership</h2>
            <dl className="mt-4 space-y-3 text-sm">
              <div>
                <dt className="text-xs text-stone-500">Assigned department</dt>
                <dd className="mt-0.5 font-medium text-stone-900">
                  {data.departmentName ?? 'Not assigned'}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-stone-500">Assigned at</dt>
                <dd className="mt-0.5 text-stone-700">{formatDateTime(data.assignedAt)}</dd>
              </div>
              <div>
                <dt className="text-xs text-stone-500">Resolved at</dt>
                <dd className="mt-0.5 text-stone-700">{formatDateTime(data.resolvedAt)}</dd>
              </div>
            </dl>
          </Card>

          <Card className="p-5">
            <h2 className="text-sm font-semibold text-stone-900">Complaint actions</h2>
            <div className="mt-4 grid gap-2">
              <Button
                variant={panel === 'accept' ? 'primary' : 'secondary'}
                disabled={!canAccept || anyMutationPending}
                onClick={() => togglePanel('accept')}
              >
                Accept assignment
              </Button>
              <Button
                variant={panel === 'reject' ? 'danger' : 'secondary'}
                disabled={!canAccept || anyMutationPending}
                onClick={() => togglePanel('reject')}
              >
                Reject complaint
              </Button>
              <Button
                variant={panel === 'status' ? 'primary' : 'secondary'}
                disabled={terminal || anyMutationPending}
                onClick={() => togglePanel('status')}
              >
                Change status
              </Button>
              <Button
                variant={panel === 'request-info' ? 'primary' : 'secondary'}
                disabled={anyMutationPending}
                onClick={() => togglePanel('request-info')}
              >
                Request information
              </Button>
              <Button
                variant={panel === 'evidence' ? 'primary' : 'secondary'}
                disabled={anyMutationPending}
                onClick={() => togglePanel('evidence')}
              >
                Attach evidence
              </Button>
              <Button
                variant={panel === 'resolve' ? 'primary' : 'secondary'}
                disabled={terminal || anyMutationPending}
                onClick={() => togglePanel('resolve')}
              >
                Resolve complaint
              </Button>
              <Button
                variant={panel === 'reassign' ? 'primary' : 'secondary'}
                disabled={anyMutationPending}
                onClick={() => togglePanel('reassign')}
              >
                Request reassignment
              </Button>
            </div>
            {!canAccept && (
              <p className="mt-3 text-xs text-stone-500">
                Accept and reject are available only while the complaint is assigned.
              </p>
            )}

            {panel === 'accept' && (
              <div className="mt-4 border-t border-stone-200 pt-4">
                <p className="text-xs leading-relaxed text-stone-600">
                  Acknowledge ownership and make this complaint visible in your active queue.
                </p>
                <Button
                  className="mt-3"
                  loading={accept.isPending}
                  onClick={() => {
                    if (confirms(`Accept ${data.complaintNumber}?`)) accept.mutate()
                  }}
                >
                  Confirm acceptance
                </Button>
              </div>
            )}

            {panel === 'reject' && (
              <form className="mt-4 space-y-3 border-t border-stone-200 pt-4" onSubmit={submitReject}>
                <Field label="Reason for rejection" required hint="At least 10 characters.">
                  <textarea
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    className={`${inputClass} min-h-24 resize-y`}
                    maxLength={1000}
                  />
                </Field>
                <Button type="submit" variant="danger" loading={reject.isPending}>
                  Confirm rejection
                </Button>
              </form>
            )}

            {panel === 'status' && (
              <form className="mt-4 space-y-3 border-t border-stone-200 pt-4" onSubmit={submitStatus}>
                <Field label="New status" required>
                  <select
                    value={nextStatus}
                    onChange={(event) => setNextStatus(event.target.value as ComplaintStatus)}
                    className={inputClass}
                  >
                    {statusChoices.map((choice) => (
                      <option key={choice} value={choice}>
                        {humanizeStatus(choice)}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Reason (optional)">
                  <textarea
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    className={`${inputClass} min-h-20 resize-y`}
                    maxLength={1000}
                  />
                </Field>
                <Button type="submit" loading={statusUpdate.isPending}>
                  Update status
                </Button>
              </form>
            )}

            {panel === 'evidence' && (
              <form className="mt-4 space-y-3 border-t border-stone-200 pt-4" onSubmit={submitEvidence}>
                <Field label="Evidence type" required>
                  <select
                    value={evidenceKind}
                    onChange={(event) => setEvidenceKind(event.target.value as DepartmentEvidenceKind)}
                    className={inputClass}
                  >
                    <option value="BEFORE">Before</option>
                    <option value="AFTER">After</option>
                    <option value="EVIDENCE">Supporting evidence</option>
                  </select>
                </Field>
                <Field
                  label="Evidence image"
                  required
                  hint="JPEG, PNG or WebP. Uploaded through the secure upload API before attachment."
                >
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    onChange={(event) => setEvidenceFile(event.target.files?.[0] ?? null)}
                    className={inputClass}
                  />
                </Field>
                {evidenceFile && (
                  <p role="status" className="truncate text-xs text-stone-500">
                    Selected: {evidenceFile.name}
                  </p>
                )}
                <Button type="submit" loading={evidence.isPending} disabled={!evidenceFile}>
                  Upload and attach
                </Button>
              </form>
            )}

            {panel === 'resolve' && (
              <form className="mt-4 space-y-3 border-t border-stone-200 pt-4" onSubmit={submitResolve}>
                <p
                  role={verifiedEvidence ? 'status' : 'alert'}
                  className={`rounded-xl px-3 py-2 text-xs ${
                    verifiedEvidence
                      ? 'bg-brand-50 text-brand-800'
                      : 'bg-amber-50 text-amber-900'
                  }`}
                >
                  {verifiedEvidence
                    ? 'A verified AFTER/EVIDENCE image is attached.'
                    : 'No verified AFTER/EVIDENCE image is attached. Resolution requires one.'}
                </p>
                <Field label="Resolution summary" required hint="At least 10 characters.">
                  <textarea
                    value={summary}
                    onChange={(event) => setSummary(event.target.value)}
                    className={`${inputClass} min-h-24 resize-y`}
                    maxLength={2000}
                  />
                </Field>
                <Button type="submit" loading={resolve.isPending}>
                  Confirm resolution
                </Button>
              </form>
            )}

            {panel === 'reassign' && (
              <form className="mt-4 space-y-3 border-t border-stone-200 pt-4" onSubmit={submitReassign}>
                <Field label="Target department ID" required>
                  <input
                    value={targetDepartmentId}
                    onChange={(event) => setTargetDepartmentId(event.target.value)}
                    className={inputClass}
                    placeholder="00000000-0000-0000-0000-000000000000"
                  />
                </Field>
                <Field label="Reason" required hint="At least 10 characters.">
                  <textarea
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    className={`${inputClass} min-h-20 resize-y`}
                    maxLength={1000}
                  />
                </Field>
                <Button type="submit" loading={reassign.isPending}>
                  Submit request
                </Button>
              </form>
            )}
          </Card>

          <Card className="p-5">
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-sm font-semibold text-stone-900">Internal notes</h2>
              <Badge tone="neutral">{internalNotes.length}</Badge>
            </div>
            <p className="mt-1 text-xs text-stone-500">Department-private; never shown to citizens.</p>
            {notes.isPending ? (
              <p role="status" className="mt-4 text-xs text-stone-500">Loading notes…</p>
            ) : notes.isError ? (
              <div role="alert" className="mt-4 rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-xs text-red-700">
                {errorMessage(notes.error, 'Could not load internal notes.')}
                <Button
                  variant="ghost"
                  size="sm"
                  className="ml-2"
                  onClick={() => void notes.refetch()}
                >
                  Retry
                </Button>
              </div>
            ) : internalNotes.length === 0 ? (
              <p className="mt-4 text-xs text-stone-500">No internal notes recorded.</p>
            ) : (
              <ul className="mt-4 space-y-3">
                {internalNotes.map((entry) => (
                  <li key={entry.id} className="rounded-xl border border-stone-200 bg-stone-50 p-3">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-xs font-semibold text-stone-800">{entry.authorName}</p>
                      <p className="text-[10px] text-stone-500">{formatDateTime(entry.createdAt)}</p>
                    </div>
                    <p className="mt-1 whitespace-pre-wrap text-xs leading-relaxed text-stone-700">
                      {entry.body}
                    </p>
                  </li>
                ))}
              </ul>
            )}
            {panel === 'note' && (
              <form className="mt-4 space-y-3 border-t border-stone-200 pt-4" onSubmit={submitNote}>
                <Field label="New internal note" required>
                  <textarea
                    value={body}
                    onChange={(event) => setBody(event.target.value)}
                    className={`${inputClass} min-h-24 resize-y`}
                    maxLength={2000}
                  />
                </Field>
                <Button type="submit" loading={note.isPending} disabled={!body.trim()}>
                  Save note
                </Button>
              </form>
            )}
            {panel !== 'note' && (
              <Button
                variant="secondary"
                size="sm"
                fullWidth
                className="mt-4"
                disabled={anyMutationPending}
                onClick={() => togglePanel('note')}
              >
                Add internal note
              </Button>
            )}
          </Card>

          <RoutingPanel decisions={data.routingDecisions ?? []} />
        </div>
      </div>
    </AppLayout>
  )
}
