import { formatDate, formatInstant } from '../lib/format'
import { humanize, label } from '../lib/labels'

/** Shows any JSON value the API sends without a dedicated screen (policy, audit detail): keys humanized, dates formatted. */
export function Tree({ value }: { value: unknown }) {
  if (value === null || value === undefined) return <span className="muted">—</span>
  if (Array.isArray(value)) {
    if (value.length === 0) return <span className="muted">Ninguno</span>
    return (
      <ul className="tree">
        {value.map((item, index) => (
          <li key={index}><Tree value={item} /></li>
        ))}
      </ul>
    )
  }
  if (typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>)
    if (entries.length === 0) return <span className="muted">—</span>
    return (
      <dl className="kv">
        {entries.map(([key, child]) => (
          <div key={key} style={{ display: 'contents' }}>
            <dt>{key === 'type' ? 'Tipo' : label(key) === humanize(key) ? humanize(key) : label(key)}</dt>
            <dd><Tree value={child} /></dd>
          </div>
        ))}
      </dl>
    )
  }
  if (typeof value === 'boolean') return <>{value ? 'Sí' : 'No'}</>
  if (typeof value === 'string') {
    if (/^\d{4}-\d{2}-\d{2}T/.test(value)) return <>{formatInstant(value)}</>
    if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return <>{formatDate(value)}</>
    return <>{label(value) === humanize(value) ? value : label(value)}</>
  }
  return <>{String(value)}</>
}
