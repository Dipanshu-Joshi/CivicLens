import type { StatusEntry } from '../lib/api/citizen'
import { formatDateTime, humanizeStatus } from '../lib/utils/format'

function toneFor(status: string) {
  if (['RESOLVED', 'CLOSED'].includes(status)) return 'bg-brand-100 text-brand-800 ring-brand-300'
  if (['REJECTED', 'AI_ANALYSIS_FAILED'].includes(status)) return 'bg-red-100 text-red-800 ring-red-300'
  if (['ON_HOLD', 'UNASSIGNED', 'PENDING_ASSIGNMENT', 'REOPENED'].includes(status)) {
    return 'bg-amber-100 text-amber-800 ring-amber-300'
  }
  return 'bg-stone-900 text-stone-50 ring-stone-900'
}

export function StatusTimeline({ entries }: { entries: StatusEntry[] }) {
  if (!entries.length) {
    return <p className="text-sm text-stone-500">No status changes recorded yet.</p>
  }

  return (
    <ol className="relative space-y-5">
      {entries.map((entry, index) => (
        <li key={`${entry.createdAt}-${index}`} className="relative flex gap-4 pl-1">
          {index < entries.length - 1 && (
            <span className="absolute top-8 bottom-[-20px] left-[15px] w-px bg-stone-200" />
          )}
          <span
            className={`relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[10px] font-bold ring-1 ring-inset ${toneFor(entry.toStatus)}`}
          >
            {index + 1}
          </span>
          <div className="min-w-0 flex-1 pb-1">
            <div className="flex flex-wrap items-center gap-2">
              {entry.fromStatus && (
                <span className="text-xs text-stone-400">{humanizeStatus(entry.fromStatus)} →</span>
              )}
              <span className="text-sm font-semibold text-stone-900">
                {humanizeStatus(entry.toStatus)}
              </span>
            </div>
            <p className="mt-1 text-xs text-stone-500">
              {formatDateTime(entry.createdAt)}
              {entry.actorRole && ` · by ${humanizeStatus(entry.actorRole)}`}
            </p>
            {entry.reason && (
              <p className="mt-2 rounded-lg border-l-2 border-clay-300 bg-paper-deep/60 px-3 py-2 text-xs leading-relaxed text-stone-700">
                {entry.reason}
              </p>
            )}
          </div>
        </li>
      ))}
    </ol>
  )
}
