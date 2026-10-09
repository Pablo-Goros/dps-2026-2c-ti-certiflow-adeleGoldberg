import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, currentActor, setActor } from '../lib/api'
import type { Party } from '../lib/types'

interface PartyDirectory {
  parties: Party[]
  loaded: boolean
  reload: () => void
  nameOf: (id: string | null | undefined) => string
  actorId: string | null
  chooseActor: (id: string | null) => void
}

const Directory = createContext<PartyDirectory | null>(null)

/** Everyone registered, plus who the person is acting as (sent with every request). */
export function PartyProvider({ children }: { children: ReactNode }) {
  const [parties, setParties] = useState<Party[]>([])
  const [loaded, setLoaded] = useState(false)
  const [tick, setTick] = useState(0)
  const [actorId, setActorId] = useState<string | null>(currentActor())

  useEffect(() => {
    let alive = true
    api
      .get<Party[]>('/parties')
      .then((list) => {
        if (!alive) return
        setParties(list)
        // A remembered actor that no longer exists (new database) would make every call fail.
        if (actorId && !list.some((p) => p.id === actorId)) {
          setActor(null)
          setActorId(null)
        }
      })
      .catch(() => undefined)
      .finally(() => alive && setLoaded(true))
    return () => {
      alive = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tick])

  const value = useMemo<PartyDirectory>(
    () => ({
      parties,
      loaded,
      reload: () => setTick((n) => n + 1),
      nameOf: (id) => parties.find((p) => p.id === id)?.name ?? (id ? id : '—'),
      actorId,
      chooseActor: (id) => {
        setActor(id)
        setActorId(id)
      },
    }),
    [parties, loaded, actorId],
  )
  return <Directory.Provider value={value}>{children}</Directory.Provider>
}

export function useParties(): PartyDirectory {
  const value = useContext(Directory)
  if (!value) throw new Error('PartyProvider is missing')
  return value
}

export function PartySelect({ value, onChange, required = true, placeholder = 'Elegí…' }: {
  value: string
  onChange: (id: string) => void
  required?: boolean
  placeholder?: string
}) {
  const { parties } = useParties()
  return (
    <select value={value} required={required} onChange={(e) => onChange(e.target.value)}>
      <option value="">{placeholder}</option>
      {parties.map((party) => (
        <option key={party.id} value={party.id}>
          {party.name}
        </option>
      ))}
    </select>
  )
}
