import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { describeError } from '../lib/api'
import { label } from '../lib/labels'
import type { ActionStatus, CertificateStatus, CriterionResult, InspectionStatus, Severity } from '../lib/types'

export function Page({ title, subtitle, actions, children }: {
  title: string
  subtitle?: ReactNode
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>{title}</h1>
          {subtitle && <p className="subtitle">{subtitle}</p>}
        </div>
        {actions && <div className="page-actions">{actions}</div>}
      </header>
      {children}
    </div>
  )
}

export function Card({ title, actions, children, className = '' }: {
  title?: ReactNode
  actions?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <div className="card-head">
          {title && <h2>{title}</h2>}
          {actions && <div className="card-actions">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  )
}

export type Tone = 'neutral' | 'ok' | 'warn' | 'bad' | 'info' | 'seal'

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`badge badge-${tone}`}>{children}</span>
}

const resultTone: Record<CriterionResult, Tone> = { APPROVED: 'ok', OBSERVED: 'warn', REJECTED: 'bad' }
const severityTone: Record<Severity, Tone> = { LOW: 'info', MEDIUM: 'warn', HIGH: 'bad', CRITICAL: 'bad' }
const inspectionTone: Record<InspectionStatus, Tone> = { ASSIGNED: 'info', IN_PROGRESS: 'warn', CLOSED: 'neutral' }
const certificateTone: Record<CertificateStatus, Tone> = { VALID: 'ok', SUSPENDED: 'warn', EXPIRED: 'bad' }
const actionTone: Record<ActionStatus, Tone> = {
  PENDING_PLANNING: 'warn',
  PLANNED: 'info',
  EXECUTION_REPORTED: 'info',
  CLOSED: 'ok',
  VOIDED: 'neutral',
}

export const ResultBadge = ({ result }: { result: CriterionResult }) => (
  <Badge tone={resultTone[result]}>{label(result)}</Badge>
)
export const SeverityBadge = ({ severity }: { severity: Severity | null }) =>
  severity ? <Badge tone={severityTone[severity]}>Severidad {label(severity).toLowerCase()}</Badge> : null
export const InspectionBadge = ({ status }: { status: InspectionStatus }) => (
  <Badge tone={inspectionTone[status]}>{label(status)}</Badge>
)
export const CertificateBadge = ({ status }: { status: CertificateStatus }) => (
  <Badge tone={certificateTone[status]}>{label(status)}</Badge>
)
export const ActionBadge = ({ status }: { status: ActionStatus }) => (
  <Badge tone={actionTone[status]}>{label(status)}</Badge>
)

export function Field({ label: text, hint, children, wide, group }: {
  label: string
  hint?: string
  children: ReactNode
  wide?: boolean
  /** Use when the content is several controls (checkboxes, buttons): a label would wrongly bind to the first one. */
  group?: boolean
}) {
  const className = `field ${wide ? 'field-wide' : ''}`
  if (group) {
    return (
      <div className={className} role="group" aria-label={text}>
        <span className="field-label">{text}</span>
        {children}
        {hint && <span className="field-hint">{hint}</span>}
      </div>
    )
  }
  return (
    <label className={className}>
      <span className="field-label">{text}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  )
}

export function Notice({ kind = 'info', children }: { kind?: 'info' | 'error' | 'ok' | 'warn'; children: ReactNode }) {
  return (
    <div className={`notice notice-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  )
}

export function Loading({ what = 'Cargando…' }: { what?: string }) {
  return <p className="muted loading">{what}</p>
}

export function LoadError({ message, retry }: { message: string; retry?: () => void }) {
  return (
    <Notice kind="error">
      {message}{' '}
      {retry && (
        <button type="button" className="link" onClick={retry}>
          Reintentar
        </button>
      )}
    </Notice>
  )
}

export function Empty({ title, hint, children }: { title: string; hint?: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <strong>{title}</strong>
      {hint && <p className="muted">{hint}</p>}
      {children}
    </div>
  )
}

/** Key/value rows for details. */
export function Facts({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="facts">
      {items.map(([name, value]) => (
        <div key={name}>
          <dt>{name}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  )
}

// ---------------------------------------------------------------- toasts and actions

interface ToastApi {
  show: (message: string) => void
}
const ToastContext = createContext<ToastApi>({ show: () => undefined })
export const useToast = () => useContext(ToastContext)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<{ id: number; text: string } | null>(null)
  const show = useCallback((text: string) => {
    const id = Date.now()
    setMessage({ id, text })
    window.setTimeout(() => setMessage((current) => (current?.id === id ? null : current)), 3500)
  }, [])
  return (
    <ToastContext.Provider value={{ show }}>
      {children}
      <div className="toast-region" aria-live="polite">
        {message && <div className="toast">{message.text}</div>}
      </div>
    </ToastContext.Provider>
  )
}

/**
 * Runs something the person asked for (a save, a state change): blocks double clicks, shows the
 * failure next to the form and confirms success with a short message.
 */
export function useAction() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const toast = useToast()

  const run = useCallback(
    async (work: () => Promise<unknown>, done?: string): Promise<boolean> => {
      setBusy(true)
      setError(null)
      try {
        await work()
        if (done) toast.show(done)
        return true
      } catch (failure) {
        setError(describeError(failure))
        return false
      } finally {
        setBusy(false)
      }
    },
    [toast],
  )
  return { run, busy, error, clearError: () => setError(null) }
}

export function ErrorLine({ message }: { message: string | null }) {
  return message ? <Notice kind="error">{message}</Notice> : null
}
