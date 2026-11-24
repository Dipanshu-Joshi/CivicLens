import type { ButtonHTMLAttributes, ReactNode } from 'react'
import type { ComplaintStatus } from '../lib/api/citizen'
import { humanizeStatus } from '../lib/utils/format'
import { Icon } from './Icon'

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
type ButtonSize = 'sm' | 'md' | 'lg'

const buttonStyles: Record<ButtonVariant, string> = {
  primary:
    'bg-brand-700 text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.12)] hover:bg-brand-800 active:bg-brand-900 focus-visible:ring-brand-700',
  secondary:
    'border border-stone-300 bg-white text-stone-800 hover:border-stone-900 hover:bg-stone-50 focus-visible:ring-stone-700',
  ghost: 'text-stone-600 hover:bg-stone-200/60 hover:text-stone-900 focus-visible:ring-stone-400',
  danger:
    'border border-red-200 bg-red-50 text-red-800 hover:border-red-400 hover:bg-red-100 focus-visible:ring-red-500',
}

const buttonSizes: Record<ButtonSize, string> = {
  sm: 'px-3 py-1.5 text-xs',
  md: 'px-4 py-2.5 text-sm',
  lg: 'px-5 py-3 text-sm',
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  fullWidth = false,
  className = '',
  icon,
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
  fullWidth?: boolean
  icon?: ReactNode
}) {
  return (
    <button
      {...props}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 rounded-lg font-semibold transition duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-paper active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 disabled:active:scale-100 ${buttonStyles[variant]} ${buttonSizes[size]} ${fullWidth ? 'w-full' : ''} ${className}`}
    >
      {loading && <Spinner className="h-4 w-4" />}
      {!loading && icon}
      {children}
    </button>
  )
}

export function Spinner({ className = 'h-5 w-5' }: { className?: string }) {
  return (
    <svg className={`animate-spin ${className}`} viewBox="0 0 24 24" aria-hidden="true">
      <circle
        className="opacity-20"
        cx="12"
        cy="12"
        r="9"
        fill="none"
        stroke="currentColor"
        strokeWidth="3"
      />
      <path
        d="M21 12a9 9 0 0 0-9-9"
        fill="none"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinecap="round"
      />
    </svg>
  )
}

export function Card({
  children,
  className = '',
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <section
      className={`rounded-2xl border border-stone-200 bg-white shadow-soft ${className}`}
    >
      {children}
    </section>
  )
}

type Tone = 'neutral' | 'success' | 'warn' | 'danger' | 'info'

const badgeStyles: Record<Tone, string> = {
  neutral: 'bg-stone-100 text-stone-700 ring-stone-200',
  success: 'bg-brand-50 text-brand-800 ring-brand-200',
  warn: 'bg-amber-50 text-amber-800 ring-amber-200',
  danger: 'bg-red-50 text-red-800 ring-red-200',
  info: 'bg-stone-900 text-stone-50 ring-stone-900',
}

const badgeDots: Record<Tone, string> = {
  neutral: 'bg-stone-400',
  success: 'bg-brand-500',
  warn: 'bg-amber-500',
  danger: 'bg-red-500',
  info: 'bg-clay-300',
}

export function Badge({
  children,
  tone = 'neutral',
  className = '',
}: {
  children: ReactNode
  tone?: Tone
  className?: string
}) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-semibold tracking-wide ring-1 ring-inset ${badgeStyles[tone]} ${className}`}
    >
      <span aria-hidden="true" className={`h-1.5 w-1.5 rounded-full ${badgeDots[tone]}`} />
      {children}
    </span>
  )
}

const statusTones: Record<ComplaintStatus, Tone> = {
  SUBMITTED: 'info',
  AI_ANALYSIS_PENDING: 'info',
  AI_ANALYSIS_COMPLETED: 'info',
  AI_ANALYSIS_FAILED: 'warn',
  PENDING_ASSIGNMENT: 'warn',
  UNASSIGNED: 'warn',
  ASSIGNED: 'info',
  ACKNOWLEDGED: 'info',
  IN_PROGRESS: 'info',
  ON_HOLD: 'warn',
  RESOLVED: 'success',
  REOPENED: 'warn',
  REJECTED: 'danger',
  CLOSED: 'success',
}

export function StatusBadge({ status }: { status: ComplaintStatus }) {
  return <Badge tone={statusTones[status]}>{humanizeStatus(status)}</Badge>
}

export function Field({
  label,
  hint,
  error,
  required = false,
  children,
}: {
  label: string
  hint?: string
  error?: string
  required?: boolean
  children: ReactNode
}) {
  return (
    <label className="block">
      <span className="mb-1.5 flex items-center gap-1 text-sm font-medium text-stone-800">
        {label}
        {required && <span className="text-clay-500">*</span>}
      </span>
      {children}
      {error ? (
        <span className="mt-1.5 block text-xs font-medium text-red-700">{error}</span>
      ) : hint ? (
        <span className="mt-1.5 block text-xs text-stone-500">{hint}</span>
      ) : null}
    </label>
  )
}

export const inputClass =
  'w-full rounded-lg border border-stone-300 bg-white px-3.5 py-2.5 text-sm text-stone-900 outline-none transition placeholder:text-stone-400 hover:border-stone-400 focus:border-brand-600 focus:ring-4 focus:ring-brand-600/15'

export function Eyebrow({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <p
      className={`flex items-center gap-2 text-[11px] font-semibold tracking-[0.16em] text-stone-500 uppercase ${className}`}
    >
      <span aria-hidden="true" className="h-px w-5 bg-clay-500" />
      {children}
    </p>
  )
}

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: string
  title: string
  description?: string
  actions?: ReactNode
}) {
  return (
    <header className="mb-8 flex flex-col gap-4 border-b border-stone-200 pb-6 sm:flex-row sm:items-end sm:justify-between">
      <div>
        {eyebrow && <Eyebrow className="mb-2">{eyebrow}</Eyebrow>}
        <h1 className="text-3xl font-semibold tracking-tight text-stone-900 sm:text-4xl">{title}</h1>
        {description && <p className="mt-2 max-w-2xl text-sm text-stone-600">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </header>
  )
}

export function StatePanel({
  tone = 'neutral',
  icon,
  title,
  description,
  action,
}: {
  tone?: 'neutral' | 'error'
  icon?: 'info' | 'warning' | 'check-circle' | 'clock' | 'clipboard' | 'bell'
  title: string
  description?: string
  action?: ReactNode
}) {
  const isError = tone === 'error'
  return (
    <div
      className={`flex flex-col items-center justify-center rounded-2xl border border-dashed px-6 py-12 text-center ${
        isError ? 'border-red-300 bg-red-50/60' : 'border-stone-300 bg-paper-deep/50'
      }`}
    >
      <div
        className={`mb-3 flex h-11 w-11 items-center justify-center rounded-full ${
          isError ? 'bg-red-100 text-red-700' : 'bg-white text-stone-700 ring-1 ring-stone-200'
        }`}
      >
        <Icon name={icon ?? (isError ? 'warning' : 'info')} />
      </div>
      <h3 className="text-sm font-semibold text-stone-900">{title}</h3>
      {description && <p className="mt-1 max-w-md text-sm text-stone-600">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function StatCard({
  label,
  value,
  detail,
  icon,
  tone = 'neutral',
}: {
  label: string
  value: number | string
  detail?: string
  icon: 'clipboard' | 'clock' | 'sparkle' | 'check-circle'
  tone?: Tone
}) {
  const iconTone: Record<Tone, string> = {
    neutral: 'bg-stone-100 text-stone-700',
    success: 'bg-brand-100 text-brand-800',
    warn: 'bg-amber-100 text-amber-800',
    danger: 'bg-red-100 text-red-800',
    info: 'bg-clay-100 text-clay-700',
  }
  return (
    <Card className="p-5 transition duration-200 hover:-translate-y-0.5 hover:shadow-lift">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-xs font-medium tracking-wide text-stone-500 uppercase">{label}</p>
          <p className="mt-3 font-display text-4xl font-semibold tracking-tight text-stone-900 tabular-nums">
            {value}
          </p>
          {detail && <p className="mt-1.5 text-xs text-stone-500">{detail}</p>}
        </div>
        <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${iconTone[tone]}`}>
          <Icon name={icon} className="h-5 w-5" />
        </div>
      </div>
    </Card>
  )
}
