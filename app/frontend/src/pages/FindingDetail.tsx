import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../lib/api'
import { formatDate, formatInstant, today } from '../lib/format'
import { useLoad } from '../lib/hooks'
import type { CorrectiveAction, Finding } from '../lib/types'
import { PartySelect, useParties } from '../components/Party'
import {
  ActionBadge, Badge, Card, ErrorLine, Facts, Field, Loading, LoadError, Notice, Page, ResultBadge, SeverityBadge, useAction,
} from '../components/ui'

export function FindingDetail() {
  const { id = '' } = useParams()
  const { nameOf } = useParties()
  const finding = useLoad(() => api.get<Finding>(`/findings/${id}`), [id])

  if (finding.error) return <Page title="Hallazgo"><LoadError message={finding.error} retry={finding.reload} /></Page>
  if (!finding.data) return <Page title="Hallazgo"><Loading /></Page>
  const f = finding.data

  return (
    <Page
      title={`Hallazgo en ${f.criterionId}`}
      subtitle={<><ResultBadge result={f.result} /> <SeverityBadge severity={f.severity} /></>}
      actions={<Link className="button" to={`/inspecciones/${f.inspectionId}`}>Ver inspección</Link>}
    >
      <Card title="Qué se encontró">
        {f.reasons.length > 0 && <ul className="tree">{f.reasons.map((r, i) => <li key={i}>{r}</li>)}</ul>}
        <hr className="divider" />
        <Facts
          items={[
            ['Responsable', nameOf(f.responsibleId)],
            ['Inspector', nameOf(f.inspectorId)],
            ['Detectado', formatInstant(f.createdAt)],
            ['Evidencia presentada', f.presentedEvidence.length ? f.presentedEvidence.join(', ') : 'Ninguna'],
          ]}
        />
      </Card>
      {f.obligationVoided && <Notice kind="info">La obligación de corregir este hallazgo quedó sin efecto (por ejemplo, porque se rectificó la inspección).</Notice>}
      {f.pendingNonConformity && <Notice kind="warn">Es una no conformidad pendiente: mientras no se verifique, puede condicionar la certificación.</Notice>}
      {f.correctiveActions.map((action, index) => (
        <ActionCard key={action.id} finding={f} action={action} number={index + 1} total={f.correctiveActions.length} onChange={finding.reload} />
      ))}
    </Page>
  )
}

function ActionCard({ finding, action, number, total, onChange }: {
  finding: Finding
  action: CorrectiveAction
  number: number
  total: number
  onChange: () => void
}) {
  const { nameOf } = useParties()
  return (
    <Card title={total > 1 ? `Acción correctiva ${number}` : 'Acción correctiva'} actions={<ActionBadge status={action.status} />}>
      <Facts
        items={[
          ['Planificarla antes del', formatDate(action.planningDueDate)],
          ['Plazo final', <>{formatDate(action.deadline)} {action.deadlineBreached && <Badge tone="bad">Vencido</Badge>}</>],
          ['Impide certificar', action.blocksCertification ? 'Sí' : 'No'],
          ...(action.closedAt ? [['Cerrada', formatInstant(action.closedAt)] as [string, string]] : []),
        ]}
      />

      {action.plan && (
        <div style={{ marginTop: 16 }}>
          <h3>Plan</h3>
          <p style={{ margin: '4px 0' }}>{action.plan.work}</p>
          <p className="muted small">
            Ejecuta {nameOf(action.plan.executor)} · para el {formatDate(action.plan.dueDate)}
          </p>
        </div>
      )}
      {action.executions.map((execution, i) => (
        <div key={i} style={{ marginTop: 12 }}>
          <h3>Ejecución informada</h3>
          <p style={{ margin: '4px 0' }}>{execution.statement}</p>
          <p className="muted small">
            {nameOf(execution.reportedBy)} · {formatInstant(execution.reportedAt)}
            {execution.evidenceReferences.length > 0 && <> · evidencia: <span className="mono">{execution.evidenceReferences.join(', ')}</span></>}
          </p>
        </div>
      ))}
      {action.verifications.map((verification, i) => (
        <div key={i} style={{ marginTop: 12 }}>
          <h3>Verificación <Badge tone={verification.satisfactory ? 'ok' : 'bad'}>{verification.satisfactory ? 'Satisfactoria' : 'No satisfactoria'}</Badge></h3>
          <p style={{ margin: '4px 0' }}>{verification.reason}</p>
          <p className="muted small">{nameOf(verification.verifiedBy)} · {formatInstant(verification.verifiedAt)}</p>
        </div>
      ))}

      {action.status === 'PENDING_PLANNING' && <PlanForm finding={finding} onDone={onChange} />}
      {action.status === 'PLANNED' && <ExecutionForm finding={finding} action={action} onDone={onChange} />}
      {action.status === 'EXECUTION_REPORTED' && <VerificationForm finding={finding} onDone={onChange} />}
    </Card>
  )
}

function Step({ title, who, children }: { title: string; who: string; children: React.ReactNode }) {
  return (
    <div style={{ marginTop: 18, paddingTop: 16, borderTop: '1px solid var(--line)' }}>
      <h3>{title}</h3>
      <p className="muted small">Lo hace {who}. Elegí esa persona en «Actuando como».</p>
      {children}
    </div>
  )
}

function PlanForm({ finding, onDone }: { finding: Finding; onDone: () => void }) {
  const { nameOf } = useParties()
  const [work, setWork] = useState('')
  const [executorId, setExecutorId] = useState('')
  const [dueDate, setDueDate] = useState(today(14))
  const { run, busy, error } = useAction()
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (await run(() => api.post(`/findings/${finding.id}/plan`, { work: work.trim(), executorId, dueDate }), 'Plan guardado')) onDone()
  }
  return (
    <Step title="Planificar la corrección" who={`el responsable (${nameOf(finding.responsibleId)})`}>
      <form className="form" onSubmit={submit}>
        <Field label="Trabajo a realizar" wide><textarea value={work} onChange={(e) => setWork(e.target.value)} required /></Field>
        <Field label="Quién lo ejecuta"><PartySelect value={executorId} onChange={setExecutorId} /></Field>
        <Field label="Fecha comprometida"><input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} required /></Field>
        <div className="form-actions"><button className="primary" disabled={busy || !work.trim() || !executorId}>Guardar plan</button></div>
      </form>
      <ErrorLine message={error} />
    </Step>
  )
}

function ExecutionForm({ finding, action, onDone }: { finding: Finding; action: CorrectiveAction; onDone: () => void }) {
  const { nameOf } = useParties()
  const [statement, setStatement] = useState('')
  const [references, setReferences] = useState('')
  const { run, busy, error } = useAction()
  async function submit(event: FormEvent) {
    event.preventDefault()
    const evidenceReferences = references.split('\n').map((r) => r.trim()).filter(Boolean)
    if (await run(() => api.post(`/findings/${finding.id}/execution`, { statement: statement.trim(), evidenceReferences }), 'Ejecución informada')) onDone()
  }
  return (
    <Step title="Informar la ejecución" who={`el ejecutor (${nameOf(action.plan?.executor)})`}>
      <form className="form" onSubmit={submit}>
        <Field label="Qué se hizo" wide><textarea value={statement} onChange={(e) => setStatement(e.target.value)} required /></Field>
        <Field label="Evidencia" hint="Una referencia por línea (archivo o enlace). Es obligatoria." wide>
          <textarea value={references} onChange={(e) => setReferences(e.target.value)} required />
        </Field>
        <div className="form-actions"><button className="primary" disabled={busy || !statement.trim() || !references.trim()}>Informar ejecución</button></div>
      </form>
      <ErrorLine message={error} />
    </Step>
  )
}

function VerificationForm({ finding, onDone }: { finding: Finding; onDone: () => void }) {
  const { nameOf } = useParties()
  const [satisfactory, setSatisfactory] = useState(true)
  const [reason, setReason] = useState('')
  const { run, busy, error } = useAction()
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (await run(() => api.post(`/findings/${finding.id}/verification`, { satisfactory, reason: reason.trim() }), 'Verificación registrada')) onDone()
  }
  return (
    <Step title="Verificar la corrección" who={`el inspector (${nameOf(finding.inspectorId)})`}>
      <form className="form" onSubmit={submit}>
        <Field group label="Resultado">
          <div className="segmented" role="group" aria-label="Resultado">
            <button type="button" aria-pressed={satisfactory} onClick={() => setSatisfactory(true)}>Satisfactoria</button>
            <button type="button" aria-pressed={!satisfactory} onClick={() => setSatisfactory(false)}>No satisfactoria</button>
          </div>
        </Field>
        <Field label="Motivo" wide><textarea value={reason} onChange={(e) => setReason(e.target.value)} required /></Field>
        <div className="form-actions"><button className="primary" disabled={busy || !reason.trim()}>Registrar verificación</button></div>
      </form>
      <ErrorLine message={error} />
    </Step>
  )
}
