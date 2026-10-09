import { useState } from 'react'
import { api, query } from '../lib/api'
import { formatInstant } from '../lib/format'
import { useLoad } from '../lib/hooks'
import { label } from '../lib/labels'
import type { JobsResult, OutboxEntry } from '../lib/types'
import { Badge, Card, Empty, ErrorLine, Loading, LoadError, Notice, Page, useAction, type Tone } from '../components/ui'

const tone: Record<OutboxEntry['status'], Tone> = { PENDING: 'warn', DONE: 'ok', DEAD: 'bad' }

export function Processes() {
  const [status, setStatus] = useState<'' | OutboxEntry['status']>('')
  const outbox = useLoad(() => api.get<OutboxEntry[]>('/admin/outbox' + query({ status })), [status])
  const jobs = useAction()
  const retry = useAction()
  const [result, setResult] = useState<JobsResult | null>(null)
  const [retried, setRetried] = useState<{ revived: number; delivered: number } | null>(null)
  const dead = outbox.data?.filter((e) => e.status === 'DEAD').length ?? 0

  return (
    <Page
      title="Procesos"
      subtitle="Tareas que el sistema hace solo cada tanto. Acá podés ejecutarlas ahora, sin esperar."
    >
      <Card title="Barridos de mantenimiento">
        <p className="muted">
          Vencen los certificados y las acciones correctivas que pasaron su fecha, y entregan los eventos que quedaron pendientes.
        </p>
        <button
          className="primary"
          disabled={jobs.busy}
          onClick={async () => {
            const ok = await jobs.run(async () => {
              setResult(await api.post<JobsResult>('/admin/jobs/run'))
              outbox.reload()
            })
            if (!ok) setResult(null)
          }}
        >
          Ejecutar ahora
        </button>
        {result && (
          <div style={{ marginTop: 14 }}>
            <Notice kind="ok">
              Certificados vencidos: <strong>{result.expiredCertificates}</strong> · acciones vencidas: <strong>{result.expiredActions}</strong> · eventos entregados: <strong>{result.deliveredEvents}</strong>
            </Notice>
          </div>
        )}
        <ErrorLine message={jobs.error} />
      </Card>

      <Card
        title="Eventos del sistema"
        actions={
          <select aria-label="Estado" value={status} onChange={(e) => setStatus(e.target.value as typeof status)} style={{ width: 'auto' }}>
            <option value="">Todos</option>
            {(['PENDING', 'DONE', 'DEAD'] as const).map((s) => <option key={s} value={s}>{label(s)}</option>)}
          </select>
        }
      >
        <p className="muted">
          Cada cambio importante genera un evento que se guarda junto con el cambio y se entrega después. Si la entrega falla se
          reintenta; tras varios fracasos queda como «Agotado» hasta que alguien lo reactive.
        </p>
        {dead > 0 && (
          <div style={{ marginBottom: 14 }}>
            <Notice kind="warn">
              Hay {dead} {dead === 1 ? 'evento agotado' : 'eventos agotados'}.{' '}
              <button
                className="link"
                disabled={retry.busy}
                onClick={async () => {
                  const ok = await retry.run(async () => {
                    setRetried(await api.post('/admin/outbox/retry-dead'))
                    outbox.reload()
                  })
                  if (!ok) setRetried(null)
                }}
              >
                Reintentar ahora
              </button>
            </Notice>
          </div>
        )}
        {retried && <Notice kind="ok">Se reactivaron {retried.revived} y se entregaron {retried.delivered}.</Notice>}
        <ErrorLine message={retry.error} />
        {outbox.error && <LoadError message={outbox.error} retry={outbox.reload} />}
        {outbox.loading && !outbox.data && <Loading />}
        {outbox.data &&
          (outbox.data.length === 0 ? (
            <Empty title="No hay eventos" hint="Se generan al operar con inspecciones, hallazgos y certificados." />
          ) : (
            <div className="table-wrap">
              <table>
                <thead><tr><th className="num">N.º</th><th>Evento</th><th>Estado</th><th className="num">Intentos</th><th>Creado</th><th>Último error</th></tr></thead>
                <tbody>
                  {[...outbox.data].reverse().slice(0, 100).map((entry) => (
                    <tr key={entry.seq}>
                      <td className="num">{entry.seq}</td>
                      <td>{entry.eventType.split('.').at(-1)}</td>
                      <td><Badge tone={tone[entry.status]}>{label(entry.status)}</Badge></td>
                      <td className="num">{entry.attempts}</td>
                      <td className="nowrap">{formatInstant(entry.createdAtMillis)}</td>
                      <td className="small">{entry.lastError ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
      </Card>
    </Page>
  )
}
