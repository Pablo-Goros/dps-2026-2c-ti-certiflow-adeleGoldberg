// Everything the person reads that the API sends as a code. Unknown codes fall back to a readable form.

const dictionary: Record<string, string> = {
  // asset types
  LABORATORY: 'Laboratorio',
  FACTORY: 'Fábrica',
  FACILITY: 'Instalación',
  EQUIPMENT: 'Equipo',
  // parties
  PERSON: 'Persona',
  ORGANIZATION: 'Organización',
  // subsystems and characteristics (the names come from the domain in English)
  'electrical installation': 'Instalación eléctrica',
  'pressure system': 'Sistema de presión',
  'building safety': 'Seguridad edilicia',
  room: 'Sala',
  biosafetyLevel: 'Nivel de bioseguridad',
  productionLine: 'Línea de producción',
  purpose: 'Uso',
  brand: 'Marca',
  model: 'Modelo',
  serialNumber: 'N.º de serie',
  // results and severities
  APPROVED: 'Aprobado',
  OBSERVED: 'Observado',
  REJECTED: 'Rechazado',
  LOW: 'Baja',
  MEDIUM: 'Media',
  HIGH: 'Alta',
  CRITICAL: 'Crítica',
  // inspections
  ASSIGNED: 'Asignada',
  IN_PROGRESS: 'En curso',
  CLOSED: 'Cerrada',
  // certificates
  VALID: 'Vigente',
  SUSPENDED: 'Suspendido',
  EXPIRED: 'Vencido',
  GLOBAL: 'Global',
  PARTIAL: 'Parcial',
  REGULAR: 'Regular',
  CONDITIONAL: 'Condicional',
  // corrective actions
  PENDING_PLANNING: 'Falta planificar',
  PLANNED: 'Planificada',
  EXECUTION_REPORTED: 'Ejecución informada',
  VOIDED: 'Anulada',
  // evidence
  PHOTOGRAPH: 'Fotografía',
  DOCUMENT: 'Documento',
  // outbox
  PENDING: 'Pendiente',
  DONE: 'Entregado',
  DEAD: 'Agotado',
  // fields of blockers
  count: 'Cantidad',
  criterionId: 'Criterio',
  laterInspectionId: 'Inspección posterior',
  certificateId: 'Certificado',
  // audit element types
  ASSET: 'Activo',
  PARTY: 'Persona u organización',
  SCHEMA: 'Esquema',
  INSPECTION: 'Inspección',
  FINDING: 'Hallazgo',
  CORRECTIVE_ACTION: 'Acción correctiva',
  CERTIFICATE: 'Certificado',
}

export function label(code: string | null | undefined): string {
  if (!code) return '—'
  return dictionary[code] ?? humanize(code)
}

/** SOME_CODE or someCode -> "Some code". */
export function humanize(code: string): string {
  const spaced = code
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .toLowerCase()
    .trim()
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

const blockerText: Record<string, string> = {
  BlockingSeverity: 'Un criterio tiene una severidad que impide certificar',
  ConditionalNotAllowed: 'Hay no conformidades pendientes y la política no permite certificados condicionales',
  InspectionNotClosed: 'La inspección todavía no está cerrada',
  UnverifiedRejection: 'Hay criterios rechazados sin corrección verificada',
  OverdueOpenAction: 'Hay acciones correctivas abiertas y vencidas',
  UnplannedAction: 'Hay acciones correctivas sin planificar',
  SupersededInspection: 'El activo fue inspeccionado de nuevo después; esta inspección ya no describe su estado',
  AssetAlreadyCertified: 'El activo ya tiene un certificado vigente',
}

export function blockerLabel(type: string): string {
  return blockerText[type] ?? humanize(type)
}
