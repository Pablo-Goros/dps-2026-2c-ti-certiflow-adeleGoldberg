// The core explains a refusal in English with identifiers, because that text is data for logs and
// audit. What the person reads is decided here: known refusals in Spanish, and people by their name.

type NameOf = (id: string) => string

let nameOf: NameOf = (id) => id

/** Lets the error texts name people; the party directory registers it once it has loaded them. */
export function setNameResolver(resolver: NameOf | null): void {
  nameOf = resolver ?? ((id) => id)
}

const operations: Record<string, string> = {
  'attach evidence': 'adjuntar evidencia',
  close: 'cerrarla',
  'consult the assistant about': 'consultar al asistente',
  'correct a note': 'corregir una nota',
  'decide a suggestion about': 'decidir una sugerencia',
  'record a note': 'agregar una nota',
  'record an answer': 'registrar una respuesta',
  'record an observed non-conformity': 'registrar una no conformidad observada',
  'remove a note': 'quitar una nota',
  'remove an answer': 'quitar una respuesta',
  'remove evidence': 'quitar evidencia',
  start: 'iniciarla',
  'withdraw an observed non-conformity': 'retirar una no conformidad observada',
}

const NOT_THE_INSPECTOR = /^only the assigned inspector (\S+) may (.+?) inspection \S+, not (\S+)$/
const UUID = /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi

/** What to show for a refusal of the core: in Spanish when it is a known one, and with names instead of ids. */
export function humanizeDomainMessage(message: string): string {
  const notTheInspector = NOT_THE_INSPECTOR.exec(message.trim())
  if (notTheInspector) {
    const [, inspector, operation, actor] = notTheInspector
    return `Solo ${nameOf(inspector)}, que tiene asignada esta inspección, puede ${operations[operation] ?? operation}. `
      + `Estás actuando como ${nameOf(actor)}: elegí a ${nameOf(inspector)} en «Actuando como».`
  }
  return message.replace(UUID, (id) => nameOf(id))
}
