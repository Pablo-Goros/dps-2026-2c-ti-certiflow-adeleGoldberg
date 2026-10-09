import { blockerLabel, humanize, label } from '../lib/labels'
import type { Blocker } from '../lib/types'

/** Why something cannot be certified: each blocker in plain words, with its own data underneath. */
export function Blockers({ blockers }: { blockers: Blocker[] }) {
  return (
    <ul>
      {blockers.map((blocker, index) => {
        const { type, ...rest } = blocker
        const extras = Object.entries(rest).filter(([, value]) => value !== null && typeof value !== 'object')
        return (
          <li key={index}>
            {blockerLabel(type)}
            {extras.length > 0 && (
              <span className="muted small">
                {' '}
                ({extras.map(([key, value]) => `${humanize(key)}: ${typeof value === 'string' ? label(value) : String(value)}`).join(', ')})
              </span>
            )}
          </li>
        )
      })}
    </ul>
  )
}
