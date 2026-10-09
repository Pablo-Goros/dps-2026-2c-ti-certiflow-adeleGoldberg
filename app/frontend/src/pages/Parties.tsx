import { useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import { label } from '../lib/labels'
import type { Party, PartyKind } from '../lib/types'
import { useParties } from '../components/Party'
import { Card, Empty, ErrorLine, Field, Page, useAction } from '../components/ui'

export function Parties() {
  const { parties, loaded, reload, chooseActor, actorId } = useParties()
  const [name, setName] = useState('')
  const [kind, setKind] = useState<PartyKind>('PERSON')
  const { run, busy, error } = useAction()

  async function submit(event: FormEvent) {
    event.preventDefault()
    let created: Party | undefined
    const ok = await run(async () => {
      created = await api.post<Party>('/parties', { name: name.trim(), kind })
    }, 'Persona registrada')
    if (ok && created) {
      setName('')
      reload()
      // Registering the first person is the moment a visitor needs an identity, so offer it.
      if (!actorId) chooseActor(created.id)
    }
  }

  return (
    <Page title="Personas y organizaciones" subtitle="Quiénes responsables, inspeccionan o ejecutan correcciones.">
      <Card title="Registrar">
        <form className="form" onSubmit={submit}>
          <Field label="Nombre">
            <input value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} />
          </Field>
          <Field label="Tipo">
            <select value={kind} onChange={(e) => setKind(e.target.value as PartyKind)}>
              <option value="PERSON">Persona</option>
              <option value="ORGANIZATION">Organización</option>
            </select>
          </Field>
          <div className="form-actions">
            <button className="primary" disabled={busy || !name.trim()}>
              Registrar
            </button>
          </div>
        </form>
        <ErrorLine message={error} />
      </Card>
      <Card title={`Registradas (${parties.length})`}>
        {!loaded ? null : parties.length === 0 ? (
          <Empty title="Todavía no hay nadie" hint="Registrá al menos una persona para poder asignar responsables e inspectores." />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Tipo</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {parties.map((party) => (
                  <tr key={party.id}>
                    <td>{party.name}</td>
                    <td>{label(party.kind)}</td>
                    <td className="num">
                      {party.id === actorId ? (
                        <span className="muted">Estás actuando como esta persona</span>
                      ) : (
                        <button className="small" onClick={() => chooseActor(party.id)}>
                          Actuar como
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </Page>
  )
}
