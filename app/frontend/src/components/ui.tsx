import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { useInRouterContext, useLocation } from 'react-router-dom'
import { describeError } from '../lib/api'
import { label } from '../lib/labels'
import type { ActionStatus, CertificateStatus, CriterionResult, InspectionStatus, Severity } from '../lib/types'
import { Icon, sectionIcon, type IconName } from './Icon'

/** The icon of the section the page belongs to, taken from the first part of the address. */
function SectionTile() {
  const inRouter = useInRouterContext()
  return inRouter ? <SectionTileInRouter /> : null
}

function SectionTileInRouter() {
  const section = useLocation().pathname.split('/')[1] || 'panel'
  const icon = sectionIcon[section]
  return icon ? (
    <span className="page-icon">
      <Icon name={icon} size={22} />
    </span>
  ) : null
}

export function Page({ title, subtitle, actions, children }: {
  title: string
  subtitle?: ReactNode
  actions?: ReactNode
  children: ReactNode
}) {
  return (
    <div className="page">
      <header className="page-head">
        <div className="page-title">
          <SectionTile />
          <div>
            <h1>{title}</h1>
            {subtitle && <p className="subtitle">{subtitle}</p>}
          </div>
        </div>
        {actions && <div className="page-actions">{actions}</div>}
      </header>
      {children}
    </div>
  )
}

/** Initials in a colored circle, so a person is recognizable at a glance in lists and the audit trail. */
export function Avatar({ name, size = 28 }: { name: string; size?: number }) {
  const words = name.trim().split(/\s+/).filter(Boolean)
  const initials = ((words[0]?.[0] ?? '?') + (words.length > 1 ? words[words.length - 1][0] : '')).toUpperCase()
  let hash = 0
  for (const char of name) hash = (hash * 31 + char.charCodeAt(0)) % 360
  return (
    <span
      className="avatar"
      aria-hidden="true"
      style={{ width: size, height: size, fontSize: Math.round(size * 0.4), background: `hsl(${hash} 38% 90%)`, color: `hsl(${hash} 45% 28%)` }}
    >
      {initials}
    </span>
  )
}

/** A person or organization with its avatar. */
export function PersonName({ name }: { name: string }) {
  return (
    <span className="person">
      <Avatar name={name} size={24} />
      <span>{name}</span>
    </span>
  )
}

/**
 * A section of a page. With a title it can be collapsed and expanded by the person: its content is
 * hidden, not unmounted, so a half-written form keeps what was typed.
 */
export function Card({ title, actions, children, className = '', collapsible = Boolean(title), defaultOpen = true }: {
  title?: ReactNode
  actions?: ReactNode
  children: ReactNode
  className?: string
  collapsible?: boolean
  defaultOpen?: boolean
}) {
  const [open, setOpen] = useState(defaultOpen)
  const toggle = () => setOpen((value) => !value)
  const what = typeof title === 'string' ? `«${title}»` : 'la sección'
  return (
    <section className={`card ${className} ${open ? '' : 'collapsed'}`}>
      {(title || actions) && (
        <div className="card-head">
          <div className="card-title-group">
            {collapsible && (
              <button type="button" className="card-toggle" aria-expanded={open}
                aria-label={`${open ? 'Contraer' : 'Desplegar'} ${what}`} onClick={toggle}>
                <Icon name="arrowRight" size={16} className="card-chevron" />
              </button>
            )}
            {title && <h2 className={collapsible ? 'card-title clickable' : 'card-title'}
              onClick={collapsible ? toggle : undefined}>{title}</h2>}
          </div>
          {actions && <div className="card-actions">{actions}</div>}
        </div>
      )}
      <div className="card-body" hidden={!open}>{children}</div>
    </section>
  )
}

export type Tone = 'neutral' | 'ok' | 'warn' | 'bad' | 'info' | 'seal'

export function Badge({ tone = 'neutral', icon, children }: { tone?: Tone; icon?: IconName; children: ReactNode }) {
  return (
    <span className={`badge badge-${tone}`}>
      {icon && <Icon name={icon} size={13} />}
      {children}
    </span>
  )
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

const resultIcon: Record<CriterionResult, IconName> = { APPROVED: 'checkCircle', OBSERVED: 'alert', REJECTED: 'xCircle' }
const inspectionIcon: Record<InspectionStatus, IconName> = { ASSIGNED: 'calendar', IN_PROGRESS: 'edit', CLOSED: 'check' }
const certificateIcon: Record<CertificateStatus, IconName> = { VALID: 'shieldCheck', SUSPENDED: 'pause', EXPIRED: 'clock' }
const actionIcon: Record<ActionStatus, IconName> = {
  PENDING_PLANNING: 'clock',
  PLANNED: 'calendar',
  EXECUTION_REPORTED: 'wrench',
  CLOSED: 'check',
  VOIDED: 'ban',
}

export const ResultBadge = ({ result }: { result: CriterionResult }) => (
  <Badge tone={resultTone[result]} icon={resultIcon[result]}>{label(result)}</Badge>
)
export const SeverityBadge = ({ severity }: { severity: Severity | null }) =>
  severity ? <Badge tone={severityTone[severity]} icon="flag">Severidad {label(severity).toLowerCase()}</Badge> : null
export const InspectionBadge = ({ status }: { status: InspectionStatus }) => (
  <Badge tone={inspectionTone[status]} icon={inspectionIcon[status]}>{label(status)}</Badge>
)
export const CertificateBadge = ({ status }: { status: CertificateStatus }) => (
  <Badge tone={certificateTone[status]} icon={certificateIcon[status]}>{label(status)}</Badge>
)
export const ActionBadge = ({ status }: { status: ActionStatus }) => (
  <Badge tone={actionTone[status]} icon={actionIcon[status]}>{label(status)}</Badge>
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
  const icon: IconName = { info: 'info', error: 'alert', ok: 'checkCircle', warn: 'alert' }[kind] as IconName
  return (
    <div className={`notice notice-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>
      <Icon name={icon} size={18} className="notice-icon" />
      <div className="notice-body">{children}</div>
    </div>
  )
}

export function Loading({ what = 'Cargando…' }: { what?: string }) {
  return (
    <p className="muted loading" role="status">
      <span className="spinner" aria-hidden="true" />
      {what}
    </p>
  )
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
      <span className="empty-icon">
        <Icon name="search" size={22} />
      </span>
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
