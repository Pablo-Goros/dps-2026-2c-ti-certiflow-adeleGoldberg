import { useCallback, useEffect, useRef, useState } from 'react'
import { describeError } from './api'
import { useParties } from '../components/Party'

export interface Loaded<T> {
  data: T | undefined
  error: string | null
  loading: boolean
  reload: () => void
}

/** Loads something when the page opens (and whenever `deps` change); `reload` asks again. */
export function useLoad<T>(load: () => Promise<T>, deps: unknown[]): Loaded<T> {
  const { actorId } = useParties()
  const [data, setData] = useState<T>()
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [tick, setTick] = useState(0)
  const latest = useRef(0)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const loader = useCallback(load, deps)

  useEffect(() => {
    const mine = ++latest.current
    setLoading(true)
    loader()
      .then((result) => {
        if (mine === latest.current) {
          setData(result)
          setError(null)
        }
      })
      .catch((failure) => {
        if (mine === latest.current) setError(describeError(failure))
      })
      .finally(() => {
        if (mine === latest.current) setLoading(false)
      })
    return () => { ++latest.current }
  }, [loader, tick, actorId])

  return { data, error, loading, reload: () => setTick((n) => n + 1) }
}
