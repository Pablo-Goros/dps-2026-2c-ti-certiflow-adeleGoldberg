import { useCallback, useEffect, useRef, useState } from 'react'
import { describeError } from './api'

export interface Loaded<T> {
  data: T | undefined
  error: string | null
  loading: boolean
  reload: () => void
}

/** Loads something when the page opens (and whenever `deps` change); `reload` asks again. */
export function useLoad<T>(load: () => Promise<T>, deps: unknown[]): Loaded<T> {
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
  }, [loader, tick])

  return { data, error, loading, reload: () => setTick((n) => n + 1) }
}
