import { useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { AppLayout } from '../components/AppLayout'
import { Icon } from '../components/Icon'
import { StatusTimeline } from '../components/StatusTimeline'
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
  addComment,
  getMyComplaint,
  reopenComplaint,
  respondToInfoRequest,
  submitFeedback,
  type AiAnalysisResponse,
  type ComplaintPhoto,
  type ImageKind,
} from '../lib/api/citizen'
import { getImageViewUrl } from '../lib/api/uploads'
import { errorMessage, isNotFound } from '../lib/api/errors'
import { formatDateTime, humanizeStatus } from '../lib/utils/format'

function PhotoGrid({
  photos,
  urlById,
  emptyTitle,
  emptyDescription,
}: {
  photos: ComplaintPhoto[] | undefined
  urlById: Record<string, string>
  emptyTitle: string
  emptyDescription: string
}) {
  if (!photos?.length) {
    return (
      <div className="rounded-xl border border-dashed border-stone-300 bg-stone-50 px-4 py-6 text-center">
        <Icon name="camera" className="mx-auto h-5 w-5 text-stone-400" />
        <p className="mt-2 text-xs font-semibold text-stone-700">{emptyTitle}</p>
        <p className="mt-1 text-xs text-stone-500">{emptyDescription}</p>
      </div>
    )
  }
  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
      {photos.map((photo) => {
        const url = urlById[photo.id]
        const image = (
          <img
            src={url}
            alt={`${humanizeStatus(photo.imageKind)} image`}
            className="h-32 w-full rounded-xl border border-stone-200 object-cover transition hover:opacity-90"
          />
        )
        const caption = (
          <p className="mt-1 text-[10px] text-stone-500">
            {humanizeStatus(photo.imageKind)} · {photo.uploadStatus}
          </p>
        )
        return url ? (
          <a key={photo.id} href={url} target="_blank" rel="noreferrer">
            {image}
            {caption}
          </a>
        ) : (
          <div key={photo.id}>
            {image}
            {caption}
            <p className="mt-1 text-[10px] text-stone-400">Preview link expired</p>
          </div>
        )
      })}
    </div>
  )
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="rounded-xl border border-stone-200 p-3">
      <dt className="text-[11px] font-semibold tracking-wide text-stone-500 uppercase">{label}</dt>
      <dd className="mt-1 text-sm text-stone-800">{children}</dd>
    </div>
  )
}

function AiAnalysisPanel({
  analysis,
  hasImages,
}: {
  analysis: AiAnalysisResponse | null
  hasImages: boolean
}) {
  const failed = analysis
    ? ['FAILED', 'TIMEOUT', 'INVALID_SCHEMA'].includes(analysis.status)
    : false
  const inProgress = analysis
    ? ['PENDING', 'PROCESSING'].includes(analysis.status)
    : false
  const tone = !analysis
    ? 'neutral'
    : failed
      ? 'warn'
      : analysis.requiresHumanReview
        ? 'warn'
        : 'success'

  return (
    <Card className="p-5 sm:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-stone-900">AI image analysis</h2>
          <p className="mt-1 text-xs text-stone-500">
            Advisory only — a person and the routing engine remain in control.
          </p>
        </div>
        {analysis && <Badge tone={tone}>{humanizeStatus(analysis.status)}</Badge>}
      </div>

      {!analysis && (
        <p className="mt-4 rounded-xl bg-stone-50 px-4 py-5 text-center text-sm text-stone-500">
          {hasImages
            ? 'No analysis record has been created yet.'
            : 'Analysis was not run because no photos were attached to this report.'}
        </p>
      )}

      {inProgress && (
        <div className="mt-4 rounded-xl border border-stone-200 border-l-4 border-l-clay-500 bg-stone-50 px-4 py-3 text-sm text-stone-800">
          Your complaint is saved. Analysis is queued or running; refresh this page to see the
          result when it finishes.
        </div>
      )}

      {failed && (
        <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          Analysis could not be completed. The complaint and any confirmed images remain saved and
          will be handled without AI results.
        </div>
      )}

      {analysis && !inProgress && !failed && (
        <div className="mt-4 space-y-4">
          {analysis.isCivicIssue === false && (
            <p className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
              The AI did not detect a clear civic issue. A person will review this report.
            </p>
          )}
          {analysis.requiresHumanReview && (
            <p className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
              Flagged for human review: low confidence, unclear imagery, or the AI requested more
              information. No automatic department assignment will be made from this result.
            </p>
          )}
          <dl className="grid gap-3 sm:grid-cols-2">
            <Row label="Category">
              {analysis.categoryCode ?? 'Not identified'}
              {analysis.subcategoryCode ? ` · ${analysis.subcategoryCode}` : ''}
            </Row>
            <Row label="Severity">{analysis.severity ?? 'Not assessed'}</Row>
            <Row label="Confidence">
              {analysis.confidence === null ? '—' : `${Math.round(analysis.confidence * 100)}%`}
            </Row>
            <Row label="Safety concern">{analysis.safetyRisk ? 'Yes' : 'No'}</Row>
            <Row label="Image clarity">
              {analysis.isBlurry ? 'Blurry' : 'Usable'}
              {analysis.requiresAdditionalInfo ? ' · more info requested' : ''}
            </Row>
            <Row label="Visible damage">{analysis.visibleDamage || 'None reported'}</Row>
            {analysis.recommendedDepartmentHint && (
              <div className="sm:col-span-2">
                <Row label="Suggested department (advisory)">
                  {analysis.recommendedDepartmentHint}
                  <span className="mt-1 block text-xs text-stone-500">
                    This is a hint only. It does not assign or route the complaint.
                  </span>
                </Row>
              </div>
            )}
            {analysis.description && (
              <div className="sm:col-span-2">
                <Row label="AI description">{analysis.description}</Row>
              </div>
            )}
          </dl>
          <p className="text-[11px] leading-relaxed text-stone-500">
            Generated {formatDateTime(analysis.createdAt)}. The AI never infers an address or
            coordinates from photos.
          </p>
        </div>
      )}
    </Card>
  )
}

export default function ComplaintDetailPage() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()
  const [panel, setPanel] = useState<'none' | 'reopen' | 'feedback'>('none')
  const [reopenReason, setReopenReason] = useState('')
  const [rating, setRating] = useState(5)
  const [resolutionConfirmed, setResolutionConfirmed] = useState(true)
  const [feedbackComment, setFeedbackComment] = useState('')
  const [disputeReason, setDisputeReason] = useState('')
  const [message, setMessage] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const complaint = useQuery({
    queryKey: ['citizen', 'complaint', id],
    queryFn: () => getMyComplaint(id),
    retry: false,
  })

  // Presigned view URLs are short-lived capabilities; refresh before expiry.
  const imageUrls = useQuery({
    queryKey: [
      'citizen',
      'image-urls',
      id,
      complaint.data?.images?.map((image) => image.id).join(',') ?? '',
    ],
    queryFn: async () => {
      const entries = await Promise.all(
        (complaint.data?.images ?? []).map(async (image) => {
          const view = await getImageViewUrl(image.id)
          return [image.id, view.url] as const
        }),
      )
      return Object.fromEntries(entries)
    },
    enabled: Boolean(complaint.data?.images?.length),
    retry: false,
    staleTime: 4 * 60_000,
    refetchInterval: 4 * 60_000,
  })

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['citizen', 'complaint', id] }),
      queryClient.invalidateQueries({ queryKey: ['citizen', 'complaints'] }),
      queryClient.invalidateQueries({ queryKey: ['notifications', 'list'] }),
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unread'] }),
    ])
  }

  const reopen = useMutation({
    mutationFn: () => reopenComplaint(id, reopenReason.trim()),
    onSuccess: async () => {
      setPanel('none')
      setReopenReason('')
      setActionError(null)
      await invalidate()
    },
  })

  const feedbackMutation = useMutation({
    mutationFn: async () => {
      await submitFeedback(id, {
        rating,
        comment: feedbackComment.trim() || undefined,
        resolutionConfirmed,
        disputeReason: resolutionConfirmed ? undefined : disputeReason.trim() || undefined,
      })
    },
    onSuccess: async () => {
      setPanel('none')
      setActionError(null)
      await invalidate()
    },
  })

  const comment = useMutation({
    mutationFn: () => addComment(id, message.trim()),
    onSuccess: async () => {
      setMessage('')
      setActionError(null)
      await invalidate()
    },
  })

  const infoResponseMutation = useMutation({
    mutationFn: () => respondToInfoRequest(id, message.trim()),
    onSuccess: async () => {
      setMessage('')
      setActionError(null)
      await invalidate()
    },
  })

  function submitReopen(event: FormEvent) {
    event.preventDefault()
    setActionError(null)
    if (reopenReason.trim().length < 20) {
      setActionError('Please provide at least 20 characters explaining why it should reopen.')
      return
    }
    reopen.mutate()
  }

  function handleFeedbackSubmit(event: FormEvent) {
    event.preventDefault()
    setActionError(null)
    if (!resolutionConfirmed && disputeReason.trim().length < 10) {
      setActionError('Please explain what remains unresolved.')
      return
    }
    feedbackMutation.mutate()
  }

  function submitMessage(event: FormEvent) {
    event.preventDefault()
    setActionError(null)
    if (!message.trim()) return
    if (awaitingInfoResponse) {
      infoResponseMutation.mutate()
      return
    }
    comment.mutate()
  }

  if (complaint.isPending) {
    return (
      <AppLayout>
        <Card className="p-10 text-center text-sm text-stone-500">Loading complaint…</Card>
      </AppLayout>
    )
  }

  if (complaint.isError || !complaint.data) {
    return (
      <AppLayout>
        <StatePanel
          tone="error"
          title={isNotFound(complaint.error) ? 'Complaint not found' : 'Could not load complaint'}
          description={errorMessage(complaint.error, 'The complaint could not be retrieved.')}
          action={
            <Link to="/complaints">
              <Button variant="secondary">Back to my complaints</Button>
            </Link>
          }
        />
      </AppLayout>
    )
  }

  const data = complaint.data
  const imageKinds = (kinds: ImageKind[]) =>
    data.images.filter((image) => kinds.includes(image.imageKind))
  const citizenPhotos = imageKinds(['CITIZEN', 'BEFORE'])
  const evidencePhotos = imageKinds(['AFTER', 'EVIDENCE'])
  const resolvedUrls = imageUrls.data ?? {}
  const canActOnResolved = data.status === 'RESOLVED'
  const lastComment = data.comments.at(-1)
  const awaitingInfoResponse =
    lastComment?.authorRole === 'DEPARTMENT_ADMIN' &&
    !['RESOLVED', 'CLOSED', 'REJECTED'].includes(data.status)
  const mapsUrl = `https://www.openstreetmap.org/?mlat=${data.latitude}&mlon=${data.longitude}#map=16/${data.latitude}/${data.longitude}`

  return (
    <AppLayout>
      <PageHeader
        eyebrow="Complaint detail"
        title={data.complaintNumber}
        description={`Submitted ${formatDateTime(data.createdAt)}`}
        actions={
          <>
            <StatusBadge status={data.status} />
            <Badge tone="neutral">{data.severity}</Badge>
            <Link to="/complaints">
              <Button variant="secondary" size="sm">
                <Icon name="arrow-left" className="h-4 w-4" />
                All complaints
              </Button>
            </Link>
          </>
        }
      />

      {actionError && (
        <div
          role="alert"
          className="mb-5 rounded-xl border border-red-200 bg-red-50 px-3 py-2.5 text-sm text-red-700"
        >
          {actionError}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[1.35fr_0.65fr]">
        <div className="space-y-6">
          <Card className="p-5 sm:p-6">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone="info">{data.categoryCode ?? 'Uncategorised'}</Badge>
              {data.subcategoryCode && <Badge tone="neutral">{data.subcategoryCode}</Badge>}
              {data.ward && <Badge tone="neutral">Ward {data.ward}</Badge>}
            </div>
            <h2 className="mt-4 text-base font-semibold text-stone-900">Issue description</h2>
            <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-stone-700">
              {data.description}
            </p>
            {data.additionalInfo && (
              <p className="mt-3 rounded-xl bg-stone-50 p-3 text-sm leading-relaxed text-stone-600">
                {data.additionalInfo}
              </p>
            )}
            <div className="mt-5">
              <h3 className="text-xs font-semibold tracking-wide text-stone-500 uppercase">
                Reported photo
              </h3>
              <div className="mt-2">
                <PhotoGrid
                  photos={citizenPhotos}
                  urlById={resolvedUrls}
                  emptyTitle="No confirmed photo attached"
                  emptyDescription="Photos that pass server verification appear here."
                />
              </div>
            </div>
          </Card>

          <AiAnalysisPanel analysis={data.analysis} hasImages={data.images.length > 0} />

          <Card className="p-5 sm:p-6">
            <h2 className="text-base font-semibold text-stone-900">Location</h2>
            <div className="mt-3 grid gap-4 sm:grid-cols-2">
              <div className="rounded-xl border border-stone-200 p-4">
                <p className="text-xs font-semibold text-stone-500">Coordinates</p>
                <p className="mt-1 font-mono text-sm text-stone-900">
                  {data.latitude.toFixed(5)}, {data.longitude.toFixed(5)}
                </p>
                <p className="mt-1 text-xs text-stone-500">Source: {data.locationSource}</p>
              </div>
              <div className="rounded-xl border border-stone-200 p-4">
                <p className="text-xs font-semibold text-stone-500">Address</p>
                <p className="mt-1 text-sm text-stone-900">
                  {[data.addressLine, data.ward, data.city, data.state, data.postalCode, data.country]
                    .filter(Boolean)
                    .join(', ') || 'No address provided'}
                </p>
                <a
                  href={mapsUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="mt-2 inline-flex items-center gap-1 text-xs font-semibold text-brand-700 hover:underline"
                >
                  <Icon name="map" className="h-3.5 w-3.5" />
                  Open in map
                </a>
              </div>
            </div>
          </Card>

          <Card className="p-5 sm:p-6">
            <h2 className="text-base font-semibold text-stone-900">Status timeline</h2>
            <p className="mt-1 text-xs text-stone-500">Every recorded change, oldest first.</p>
            <div className="mt-6">
              <StatusTimeline entries={data.timeline} />
            </div>
          </Card>

          <Card className="p-5 sm:p-6">
            <h2 className="text-base font-semibold text-stone-900">Conversation</h2>
            <p className="mt-1 text-xs text-stone-500">Public comments from you and the department.</p>
            {awaitingInfoResponse && lastComment && (
              <div
                role="status"
                className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900"
              >
                <p className="font-semibold">Information requested</p>
                <p className="mt-1 leading-relaxed">{lastComment.body}</p>
                <p className="mt-2 text-xs">
                  Reply below. The owning department is notified when your response is recorded.
                </p>
              </div>
            )}
            <div className="mt-4 space-y-3">
              {data.comments.length === 0 ? (
                <p className="rounded-xl bg-stone-50 px-4 py-5 text-center text-sm text-stone-500">
                  No comments yet.
                </p>
              ) : (
                data.comments.map((commentEntry) => {
                  const fromDepartment = commentEntry.authorRole === 'DEPARTMENT_ADMIN'
                  return (
                    <div
                      key={commentEntry.id}
                      className={`rounded-xl border p-4 ${
                        fromDepartment
                          ? 'border-brand-200 bg-brand-50/60'
                          : 'border-stone-200 bg-white'
                      }`}
                    >
                      <div className="flex items-center justify-between gap-3">
                        <p className="text-xs font-semibold text-stone-800">
                          {fromDepartment ? commentEntry.authorName : 'You'}
                        </p>
                        <p className="text-[11px] text-stone-500">
                          {formatDateTime(commentEntry.createdAt)}
                        </p>
                      </div>
                      <p className="mt-2 text-sm leading-relaxed text-stone-700">
                        {commentEntry.body}
                      </p>
                    </div>
                  )
                })
              )}
            </div>
            <form className="mt-5 flex flex-col gap-2 sm:flex-row" onSubmit={submitMessage}>
              <label className="sr-only" htmlFor="citizen-comment">
                {awaitingInfoResponse ? 'Reply to information request' : 'Message for department'}
              </label>
              <input
                id="citizen-comment"
                value={message}
                onChange={(event) => setMessage(event.target.value)}
                className={inputClass}
                placeholder={
                  awaitingInfoResponse
                    ? 'Reply to the information request'
                    : 'Add a message for the department'
                }
                maxLength={2000}
              />
              <Button
                type="submit"
                loading={comment.isPending || infoResponseMutation.isPending}
                disabled={!message.trim()}
              >
                {awaitingInfoResponse ? 'Send response' : 'Send'}
              </Button>
            </form>
          </Card>

          <Card className="p-5 sm:p-6">
            <h2 className="text-base font-semibold text-stone-900">Resolution evidence</h2>
            <p className="mt-1 text-xs text-stone-500">Published when the department marks it resolved.</p>
            <div className="mt-4">
              <PhotoGrid
                photos={evidencePhotos}
                urlById={resolvedUrls}
                emptyTitle="No resolution evidence yet"
                emptyDescription="Evidence will appear here when a verified image is published by the department."
              />
            </div>
          </Card>
        </div>

        <div className="space-y-6">
          <Card className="p-5">
            <h2 className="text-sm font-semibold text-stone-900">Ownership</h2>
            <dl className="mt-4 space-y-3 text-sm">
              <div>
                <dt className="text-xs text-stone-500">Assigned department</dt>
                <dd className="mt-0.5 font-medium text-stone-900">
                  {data.departmentName ?? 'Awaiting assignment'}
                </dd>
              </div>
              <div>
                <dt className="text-xs text-stone-500">Assigned at</dt>
                <dd className="mt-0.5 text-stone-700">{formatDateTime(data.assignedAt)}</dd>
              </div>
              <div>
                <dt className="text-xs text-stone-500">Resolution target</dt>
                <dd className="mt-0.5 text-stone-700">{formatDateTime(data.resolutionDueAt)}</dd>
              </div>
              <div>
                <dt className="text-xs text-stone-500">Resolved at</dt>
                <dd className="mt-0.5 text-stone-700">{formatDateTime(data.resolvedAt)}</dd>
              </div>
            </dl>
          </Card>

          <Card className="p-5">
            <h2 className="text-sm font-semibold text-stone-900">Your actions</h2>
            {canActOnResolved ? (
              <div className="mt-3 space-y-2">
                <Button
                  fullWidth
                  variant={panel === 'feedback' ? 'primary' : 'secondary'}
                  onClick={() => setPanel(panel === 'feedback' ? 'none' : 'feedback')}
                >
                  Give feedback
                </Button>
                <Button
                  fullWidth
                  variant={panel === 'reopen' ? 'primary' : 'secondary'}
                  onClick={() => setPanel(panel === 'reopen' ? 'none' : 'reopen')}
                >
                  Reopen complaint
                </Button>
              </div>
            ) : (
              <p className="mt-3 text-xs leading-relaxed text-stone-500">
                Reopening and feedback become available when the department marks this complaint as
                resolved.
              </p>
            )}

            {panel === 'feedback' && (
              <form className="mt-5 space-y-4 border-t border-stone-200 pt-5" onSubmit={handleFeedbackSubmit}>
                <Field label="How would you rate the resolution?" required>
                  <select
                    value={rating}
                    onChange={(event) => setRating(Number(event.target.value))}
                    className={inputClass}
                  >
                    {[5, 4, 3, 2, 1].map((value) => (
                      <option key={value} value={value}>
                        {value} / 5
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Was the issue resolved?">
                  <select
                    value={resolutionConfirmed ? 'yes' : 'no'}
                    onChange={(event) => setResolutionConfirmed(event.target.value === 'yes')}
                    className={inputClass}
                  >
                    <option value="yes">Yes — close my complaint</option>
                    <option value="no">No — reopen it</option>
                  </select>
                </Field>
                {!resolutionConfirmed && (
                  <Field label="What remains unresolved?" required>
                    <textarea
                      value={disputeReason}
                      onChange={(event) => setDisputeReason(event.target.value)}
                      className={`${inputClass} min-h-24 resize-y`}
                    />
                  </Field>
                )}
                <Field label="Comment (optional)">
                  <textarea
                    value={feedbackComment}
                    onChange={(event) => setFeedbackComment(event.target.value)}
                    className={`${inputClass} min-h-20 resize-y`}
                  />
                </Field>
                <Button type="submit" fullWidth loading={feedbackMutation.isPending}>
                  Submit feedback
                </Button>
              </form>
            )}

            {panel === 'reopen' && (
              <form className="mt-5 space-y-4 border-t border-stone-200 pt-5" onSubmit={submitReopen}>
                <Field label="Why should this complaint reopen?" required hint="At least 20 characters.">
                  <textarea
                    value={reopenReason}
                    onChange={(event) => setReopenReason(event.target.value)}
                    className={`${inputClass} min-h-28 resize-y`}
                  />
                </Field>
                <Button type="submit" fullWidth loading={reopen.isPending}>
                  Submit reopen request
                </Button>
              </form>
            )}
          </Card>

          {data.feedback && (
            <Card className="p-5">
              <h2 className="text-sm font-semibold text-stone-900">Your feedback</h2>
              <div className="mt-3 space-y-2 text-xs text-stone-600">
                <p>Rating: {data.feedback.rating} / 5</p>
                <p>
                  Outcome:{' '}
                  {data.feedback.resolutionConfirmed ? 'Resolution confirmed' : 'Disputed'}
                </p>
                {data.feedback.comment && <p>{data.feedback.comment}</p>}
                {data.feedback.disputeReason && <p>{data.feedback.disputeReason}</p>}
              </div>
            </Card>
          )}

          <div className="rounded-xl border border-brand-200 bg-brand-50 p-4 text-xs leading-relaxed text-brand-900">
            <p className="flex items-center gap-2 font-semibold">
              <Icon name="shield" className="h-4 w-4" />
              Ownership protected
            </p>
            <p className="mt-1">
              This complaint is restricted to its owner by the backend. Department-only internal notes
              are never exposed here.
            </p>
          </div>
        </div>
      </div>
    </AppLayout>
  )
}
