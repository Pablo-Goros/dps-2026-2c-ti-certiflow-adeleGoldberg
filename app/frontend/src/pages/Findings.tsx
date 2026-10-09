import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { api, query } from '../lib/api'
import { formatDate } from '../lib/format'
import { useLoad } from '../lib/hooks'
import type { Asset, Finding } from '../lib/types'
import { useParties } from '../components/Party'
import {
  ActionBadge, Badge, Card, Empty, Field, Loading, LoadError, Page, ResultBadge, SeverityBadge,
} from '../components/ui'

export function Findings() {
  const [params] = useSearchParams()
  const inspectionId = params.get('inspectionId') ?? ''
  const [openOnly, setOpenOnly] = useState(false)
  const { nameOf } = useParties()
  const assets = useLoad(() => api.get<Asset[]>('/assets'), [])
  const findings = useLoad(
    () => api.get<Finding[]>('/findings' + query({ inspectionId, openActions: openOnly ? true : undefined })),
    [inspectionId, openOnly],
  )
  const assetName = (id: string) => assets.data?.find((a) => a.id === id)?.name ?? id

  return (
    <Page
      title="Hallazgos"
      subtitle="Lo que una inspección cerrada encontró y las acciones correctivas para resolverlo."
    >
      <Card>
        <div className="filters" style={{ marginBottom: 14 }}>
          <Field group label="Mostrar">
            <label className="checks">
              <input type="checkbox" checked={openOnly} onChange={(e) => setOpenOnly(e.target.checked)} disabled={!!inspectionId} />
              Sólo con acciones abiertas
            </label>
          </Field>
          {inspectionId && <Link className="button" to="/hallazgos">Ver todos</Link>}
        </div>
        {findings.error && <LoadError message={findings.error} retry={findings.reload} />}
        {findings.loading && !findings.data && <Loading />}
        {findings.data &&
          (findings.data.length === 0 ? (
            <Empty title="No hay hallazgos" hint="Aparecen cuando se cierra una inspección con criterios observados o rechazados." />
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr><th>Criterio</th><th>Activo</th><th>Resultado</th><th>Responsable</th><th>Acción correctiva</th><th>Plazo</th></tr>
                </thead>
                <tbody>
                  {findings.data.map((finding) => {
                    const action = finding.correctiveActions.at(-1)
                    return (
                      <tr key={finding.id}>
                        <td><Link to={`/hallazgos/${finding.id}`}>{finding.criterionId}</Link></td>
                        <td>{assetName(finding.assetId)}</td>
                        <td><ResultBadge result={finding.result} /> <SeverityBadge severity={finding.severity} /></td>
                        <td>{nameOf(finding.responsibleId)}</td>
                        <td>
                          {finding.obligationVoided ? <Badge>Sin obligación</Badge> : action ? <ActionBadge status={action.status} /> : '—'}
                        </td>
                        <td className="nowrap">
                          {action && !finding.obligationVoided ? (
                            <>
                              {formatDate(action.deadline)}
                              {action.deadlineBreached && <> <Badge tone="bad">Vencido</Badge></>}
                            </>
                          ) : '—'}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          ))}
      </Card>
    </Page>
  )
}
