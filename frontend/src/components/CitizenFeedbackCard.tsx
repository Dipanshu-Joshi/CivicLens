import type { FeedbackEntry } from '../lib/api/citizen'
import { formatDateTime } from '../lib/utils/format'
import { Badge, Card } from './ui'

export function CitizenFeedbackCard({ feedback }: { feedback: FeedbackEntry | null }) {
  if (!feedback) return null

  return (
    <Card className="p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-base font-semibold text-stone-900">Citizen feedback</h2>
        <Badge tone={feedback.resolutionConfirmed ? 'success' : 'danger'}>
          {feedback.resolutionConfirmed ? 'Resolution confirmed' : 'Disputed'}
        </Badge>
      </div>
      <dl className="mt-4 space-y-3 text-sm">
        <div>
          <dt className="text-xs font-semibold tracking-wide text-stone-500 uppercase">Rating</dt>
          <dd className="mt-0.5 text-stone-900">{feedback.rating} / 5</dd>
        </div>
        {feedback.comment && (
          <div>
            <dt className="text-xs font-semibold tracking-wide text-stone-500 uppercase">Comment</dt>
            <dd className="mt-0.5 whitespace-pre-wrap leading-relaxed text-stone-700">
              {feedback.comment}
            </dd>
          </div>
        )}
        {feedback.disputeReason && (
          <div>
            <dt className="text-xs font-semibold tracking-wide text-stone-500 uppercase">
              Dispute reason
            </dt>
            <dd className="mt-0.5 whitespace-pre-wrap leading-relaxed text-stone-700">
              {feedback.disputeReason}
            </dd>
          </div>
        )}
        <div>
          <dt className="text-xs font-semibold tracking-wide text-stone-500 uppercase">Submitted</dt>
          <dd className="mt-0.5 text-stone-600">{formatDateTime(feedback.createdAt)}</dd>
        </div>
      </dl>
    </Card>
  )
}
