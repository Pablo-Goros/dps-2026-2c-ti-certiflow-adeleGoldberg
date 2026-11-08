// What the interface says about the inspection assistant: where a non-conformity came from, the
// body of an edited suggestion, and why a consultation could not be answered.

import { ApiError } from './api'
import type { CriterionResult, Severity, Suggestion, SuggestionOrigin } from './types'

/** Who proposed an observed non-conformity, as the inspector reads it. */
export function originLabel(origin: SuggestionOrigin | null | undefined): string {
  if (!origin) return 'Del inspector'
  return origin.edited ? 'Sugerida por IA, editada' : 'Sugerida por IA'
}

export interface EditForm {
  criterionId: string
  result: CriterionResult
  severity: Severity
  description: string
  note: string
}

export interface EditRequest extends EditForm {
  verdict: 'EDITED'
}

/** The form an edit starts from: the suggestion as the assistant wrote it, with an empty note. */
export function editFormOf(suggestion: Suggestion): EditForm {
  return {
    criterionId: suggestion.criterionId,
    result: suggestion.result,
    severity: suggestion.severity,
    description: suggestion.description,
    note: '',
  }
}

/**
 * The body of an `EDITED` decision, or the reason it cannot be sent yet. An edit is still a
 * non-conformity, so it cannot approve the criterion: that is a discard.
 */
export function editRequest(form: EditForm): { request: EditRequest } | { problem: string } {
  if (form.result === 'APPROVED') return { problem: 'Una edición sigue siendo una no conformidad; para aprobar, descartá la sugerencia.' }
  if (!form.criterionId) return { problem: 'Elegí el criterio.' }
  if (!form.description.trim()) return { problem: 'Escribí la descripción.' }
  if (!form.note.trim()) return { problem: 'Explicá por qué la editás.' }
  return {
    request: {
      verdict: 'EDITED',
      criterionId: form.criterionId,
      result: form.result,
      severity: form.severity,
      description: form.description.trim(),
      note: form.note.trim(),
    },
  }
}

const unavailableReasons: Record<string, string> = {
  UNREACHABLE: 'El asistente no respondió a tiempo o está saturado. Probá de nuevo en un momento.',
  REFUSED: 'El modelo se negó a responder esta consulta.',
  INCOMPLETE: 'La inspección o la respuesta superan el largo que admite el modelo.',
  MALFORMED: 'El modelo respondió algo que no se pudo interpretar.',
  MISCONFIGURED: 'El asistente no está bien configurado en el servidor (proveedor, modelo o clave).',
}

/** Why a consultation failed, in words; nothing was recorded when it did. */
export function unavailableMessage(error: unknown): string | null {
  if (!(error instanceof ApiError) || error.code !== 'ASSISTANT_UNAVAILABLE') return null
  const reason = error.details.map((detail) => unavailableReasons[detail]).find(Boolean)
  return `${reason ?? 'El asistente no está disponible.'} No se registró nada.`
}
