const dateTime = new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium', timeStyle: 'short' })
const monthNames = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']

/** A calendar date such as "2026-10-20" (no time zone involved, so it never shifts a day). */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
  if (!match) return value
  return `${Number(match[3])} ${monthNames[Number(match[2]) - 1]} ${match[1]}`
}

/** An instant such as "2026-10-20T15:30:00Z", shown in the viewer's time zone. */
export function formatInstant(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : dateTime.format(date)
}

const timeOnly = new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false })

/** Just the time of an instant ("14:32"), in the viewer's time zone. */
export function formatTime(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : timeOnly.format(date)
}

/** The calendar day of an instant in the viewer's time zone (yyyy-mm-dd), to group records by day. */
export function dayOf(value: string | number): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** "Hoy", "Ayer" or the date, for the heading of a day. */
export function dayHeading(day: string): string {
  if (day === today()) return 'Hoy'
  if (day === today(-1)) return 'Ayer'
  return formatDate(day)
}

/** Today's date as yyyy-mm-dd in the viewer's time zone, for date inputs. */
export function today(offsetDays = 0): string {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

/** Converts the value of a datetime-local input into the instant the API expects. */
export function localDateTimeToInstant(value: string): string | undefined {
  if (!value) return undefined
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

export function isFuture(instant: string): boolean {
  return new Date(instant).getTime() > Date.now()
}
