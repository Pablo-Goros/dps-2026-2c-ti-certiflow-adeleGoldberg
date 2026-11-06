import { useEffect, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, query } from '../lib/api'
import { dayHeading, dayOf, formatTime } from '../lib/format'
import { useLoad } from '../lib/hooks'
import { blockerLabel, label } from '../lib/labels'
import type { AuditEntry } from '../lib/types'
import { Icon, type IconName } from '../components/Icon'
import { Tree } from '../components/Tree'
import { Badge, Card, Empty, Field, Loading, LoadError, Page, PersonName, type Tone } from '../components/ui'

const TYPES = ['ASSET', 'PARTY', 'SCHEMA', 'INSPECTION', 'FINDING', 'CORRECTIVE_ACTION', 'CERTIFICATE']

const elementIcon: Record<string, IconName> = {
  ASSET: 'box',
  PARTY: 'users',
  SCHEMA: 'file',
  INSPECTION: 'clipboard',
  FINDING: 'alert',
  CORRECTIVE_ACTION: 'wrench',
  CERTIFICATE: 'award',
}

/** Where each kind of element has its own screen. Parties and corrective actions have none. */
const elementPage: Record<string, string> = {
  ASSET: '/activos',
  SCHEMA: '/esquemas',
  INSPECTION: '/inspecciones',
  FINDING: '/hallazgos',
  CERTIFICATE: '/certificados',
}

/** Icon and color of a movement, so the kind of thing that happened shows before it is read. */
function styleOf(action: string): { icon: IconName; tone: Tone } {
  if (/BLOCKED|REFUSED/.test(action)) return { icon: 'ban', tone: 'bad' }
  if (/EXPIRED|SUSPENDED|VOIDED|WITHDRAWN|DISCARDED/.test(action)) return { icon: 'pause', tone: 'warn' }
  if (/ISSUED|RENEWED|REACTIVATED|PUBLISHED|VERIFIED|CLOSED/.test(action)) return { icon: 'checkCircle', tone: 'ok' }
  if (/REGISTERED|CREATED|ASSIGNED|STARTED|PLANNED|OPENED|REPORTED/.test(action)) return { icon: 'plus', tone: 'seal' }
  return { icon: 'edit', tone: 'info' }
}

/** "answer.TEMP" is the answer of criterion TEMP: the prefix says what was touched, the rest where. */
const fieldPrefixes: [string, string][] = [['answer.', 'Respuesta'], ['note.', 'Nota'], ['observation.', 'Observación'], ['section.', 'Sección']]

function fieldLabel(field: string): string {
  const prefix = fieldPrefixes.find(([start]) => field.startsWith(start))
  return prefix ? `${prefix[1]} · ${field.slice(prefix[0].length)}` : label(field)
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : null
}

function Actor({ actor }: { actor: unknown }) {
  const record = asRecord(actor)
  if (record?.type === 'User' && typeof record.name === 'string') return <PersonName name={record.name} />
  if (record?.type === 'System' || actor === 'system') return <Badge icon="bolt">Sistema</Badge>
  return <Tree value={actor} />
}

function Element({ entry, filtered }: { entry: AuditEntry; filtered: boolean }) {
  const { type, id } = entry.element
  const page = elementPage[type]
  const short = id.length > 14 ? `${id.slice(0, 8)}…` : id
  return (
    <span className="element">
      <Icon name={elementIcon[type] ?? 'file'} size={14} />
      {page ? <Link to={`${page}/${id}`}>{label(type)}</Link> : <span>{label(type)}</span>}
      <code title={id}>{short}</code>
      {!filtered && (
        <Link
          className="icon-link"
          to={`/auditoria${query({ type, id })}`}
          title="Ver solo el historial de este elemento"
          aria-label={`Ver solo el historial de ${label(type)} ${id}`}
        >
          <Icon name="history" size={14} />
        </Link>
      )}
    </span>
  )
}

function Moved({ from, to }: { from: ReactNode; to: ReactNode }) {
  return (
    <span className="moved">
      {from}
      <Icon name="arrowRight" size={14} />
      <strong>{to}</strong>
    </span>
  )
}

/** What changed, in words where the shape is known and in the generic tree where it is not. */
function Detail({ detail }: { detail: unknown }) {
  const record = asRecord(detail)
  if (!record) return null
  switch (record.type) {
    case 'ElementCreated':
      return <p className="event-text">{String(record.description ?? '')}</p>
    case 'StateChanged':
      return (
        <p className="event-text">
          <Moved from={label(String(record.previousState))} to={label(String(record.newState))} />
        </p>
      )
    case 'DataChanged': {
      const changes = Array.isArray(record.changes) ? record.changes : []
      return (
        <ul className="changes">
          {changes.map((change, index) => {
            const item = asRecord(change) ?? {}
            return (
              <li key={index}>
                <span className="muted">{fieldLabel(String(item.field ?? ''))}</span>
                <Moved from={<del><Tree value={item.previousValue} /></del>} to={<Tree value={item.currentValue} />} />
              </li>
            )
          })}
        </ul>
      )
    }
    case 'DecisionRecorded':
      return (
        <p className="event-text">
          {String(record.decision ?? '')}
          <span className="muted"> — {String(record.outcome ?? '')}</span>
        </p>
      )
    case 'CertificateStateChanged': {
      const { type, previousState, newState, ...rest } = record
      void type
      return (
        <>
          <p className="event-text"><Moved from={label(String(previousState))} to={label(String(newState))} /></p>
          <details className="more"><summary>Ver detalle</summary><Tree value={rest} /></details>
        </>
      )
    }
    case 'CertificationDecision': {
      const blockers = Array.isArray(record.blockers) ? record.blockers : []
      return (
        <>
          {blockers.length === 0 ? (
            <p className="event-text">Se emitió el certificado.</p>
          ) : (
            <ul className="blockers">
              {blockers.map((blocker, index) => <li key={index}>{blockerLabel(String(asRecord(blocker)?.type ?? ''))}</li>)}
            </ul>
          )}
          <details className="more"><summary>Ver detalle</summary><Tree value={detail} /></details>
        </>
      )
    }
    default:
      return <details className="more"><summary>Ver detalle</summary><Tree value={detail} /></details>
  }
}

export function Audit() {
  const [params, setParams] = useSearchParams()
  const type = params.get('type') ?? ''
  const id = params.get('id') ?? ''
  const [draftId, setDraftId] = useState(id)
  const filtered = !!type && !!id
  // Following a link to another element's history must also update the field that shows it.
  useEffect(() => setDraftId(id), [id])
  const entries = useLoad(() => api.get<AuditEntry[]>('/audit' + (filtered ? query({ type, id }) : '')), [type, id])
  // Newest first reads better than the append order.
  const sorted = entries.data ? [...entries.data].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt)) : undefined
  const days = new Map<string, AuditEntry[]>()
  sorted?.forEach((entry) => {
    const day = dayOf(entry.occurredAt)
    days.set(day, [...(days.get(day) ?? []), entry])
  })

  return (
    <Page title="Auditoría" subtitle="Registro permanente de quién hizo qué y cuándo. No se puede modificar.">
      <Card>
        <form
          className="filters toolbar"
          onSubmit={(e) => {
            e.preventDefault()
            setParams(type && draftId ? { type, id: draftId.trim() } : {})
          }}
        >
          <Field label="Elemento">
            <select value={type} onChange={(e) => setParams(e.target.value && draftId ? { type: e.target.value, id: draftId } : e.target.value ? { type: e.target.value } : {})}>
              <option value="">Todo el sistema</option>
              {TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}
            </select>
          </Field>
          <Field label="Identificador">
            <input value={draftId} onChange={(e) => setDraftId(e.target.value)} disabled={!type} />
          </Field>
          <button className="primary" disabled={!type || !draftId.trim()}><Icon name="search" size={16} />Filtrar</button>
          {(type || id) && <button type="button" onClick={() => { setDraftId(''); setParams({}) }}><Icon name="x" size={16} />Limpiar</button>}
          {sorted && sorted.length > 0 && (
            <span className="muted small toolbar-count">{sorted.length} {sorted.length === 1 ? 'movimiento' : 'movimientos'}</span>
          )}
        </form>
        {entries.error && <LoadError message={entries.error} retry={entries.reload} />}
        {entries.loading && !sorted && <Loading />}
        {sorted &&
          (sorted.length === 0 ? (
            <Empty title="Sin registros" hint="Todavía no hay movimientos para mostrar." />
          ) : (
            <div className="timeline">
              {[...days.entries()].map(([day, items]) => (
                <section className="day" key={day}>
                  <h2 className="day-heading">{dayHeading(day)}</h2>
                  <ol className="events">
                    {items.map((entry, index) => {
                      const style = styleOf(entry.action)
                      return (
                        <li className="event" key={index}>
                          <span className={`event-dot tone-${style.tone}`}><Icon name={style.icon} size={16} /></span>
                          <div className="event-body">
                            <div className="event-head">
                              <strong>{label(entry.action)}</strong>
                              <time className="muted small" dateTime={entry.occurredAt}>{formatTime(entry.occurredAt)}</time>
                            </div>
                            <div className="event-meta">
                              <Actor actor={entry.actor} />
                              <Element entry={entry} filtered={filtered} />
                            </div>
                            {entry.reason && <p className="event-reason">«{entry.reason}»</p>}
                            <Detail detail={entry.detail} />
                          </div>
                        </li>
                      )
                    })}
                  </ol>
                </section>
              ))}
            </div>
          ))}
      </Card>
    </Page>
  )
}
