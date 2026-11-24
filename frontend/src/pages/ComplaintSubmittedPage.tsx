import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { AppLayout } from '../components/AppLayout'
import { Icon } from '../components/Icon'
import { Button, Card, StatePanel, StatusBadge } from '../components/ui'
import { getMyComplaint } from '../lib/api/citizen'
import { errorMessage, isNotFound } from '../lib/api/errors'
import { formatDateTime, humanizeStatus } from '../lib/utils/format'

export default function ComplaintSubmittedPage() {
  const { id = '' } = useParams()
  const complaint = useQuery({
    queryKey: ['citizen', 'complaint', id],
    queryFn: () => getMyComplaint(id),
    retry: false,
  })

  const detail = complaint.data
  const analysis = detail?.analysis ?? null
  const analysisFailed = analysis
    ? ['FAILED', 'TIMEOUT', 'INVALID_SCHEMA'].includes(analysis.status)
    : false
  const analysisTitle = analysisFailed
    ? 'Analysis needs review'
    : analysis
      ? `${humanizeStatus(analysis.status)} by AI`
      : detail?.images.length
        ? 'Analysis queued'
        : 'No analysis needed'
  const analysisBody = analysisFailed
    ? 'AI analysis could not be completed. Your complaint and confirmed photos are saved, and normal review continues.'
    : analysis
      ? 'The AI result is advisory only. It never assigns a department or infers a precise location from photos.'
      : detail?.images.length
        ? 'Your confirmed photos are being analysed in the background. Open the complaint to see the result.'
        : 'You submitted this report without photos, so no image analysis was run.'

  return (
    <AppLayout>
      <div className="mx-auto max-w-3xl">
        {complaint.isPending && (
          <Card className="p-10 text-center text-sm text-stone-500">Loading confirmation…</Card>
        )}

        {complaint.isError && (
          <StatePanel
            tone="error"
            title={isNotFound(complaint.error) ? 'Complaint not found' : 'Could not load confirmation'}
            description={errorMessage(complaint.error, 'The complaint could not be retrieved.')}
            action={
              <Link to="/complaints">
                <Button variant="secondary">Back to my complaints</Button>
              </Link>
            }
          />
        )}

        {complaint.isSuccess && (
          <Card className="animate-rise overflow-hidden">
            <div className="border-b border-brand-100 bg-brand-50 px-6 py-10 text-center">
              <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-brand-700 text-white">
                <Icon name="check" className="h-7 w-7" />
              </div>
              <h1 className="mt-5 text-2xl font-semibold tracking-tight text-stone-900">
                Complaint submitted
              </h1>
              <p className="mt-2 text-sm text-brand-900">
                Your report is now in the civic workflow. Keep this reference for your records.
              </p>
              <p className="mt-4 inline-flex rounded-xl border border-brand-200 bg-white px-4 py-2 font-mono text-sm font-semibold text-brand-800">
                {complaint.data.complaintNumber}
              </p>
            </div>

            <div className="space-y-5 p-6">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <StatusBadge status={complaint.data.status} />
                <span className="text-xs text-stone-500">
                  Submitted {formatDateTime(complaint.data.createdAt)}
                </span>
              </div>

              <div className="rounded-xl border border-stone-200 border-l-4 border-l-clay-500 bg-stone-50 p-4">
                <div className="flex gap-3">
                  <Icon name="clock" className="mt-0.5 h-5 w-5 shrink-0 text-stone-700" />
                  <div>
                    <p className="text-sm font-semibold text-stone-900">{analysisTitle}</p>
                    <p className="mt-1 text-xs leading-relaxed text-stone-800">{analysisBody}</p>
                  </div>
                </div>
              </div>

              <div className="grid gap-3 sm:grid-cols-2">
                <Link to={`/complaints/${complaint.data.id}`}>
                  <Button fullWidth>Track this complaint</Button>
                </Link>
                <Link to="/dashboard">
                  <Button variant="secondary" fullWidth>
                    Back to dashboard
                  </Button>
                </Link>
              </div>
            </div>
          </Card>
        )}
      </div>
    </AppLayout>
  )
}
