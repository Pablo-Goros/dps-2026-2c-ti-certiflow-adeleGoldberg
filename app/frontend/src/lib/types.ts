// Shapes of the REST API (see docs/API.md). Dates arrive as ISO-8601 text.

export type PartyKind = 'PERSON' | 'ORGANIZATION'
export type AssetType = 'LABORATORY' | 'FACTORY' | 'FACILITY' | 'EQUIPMENT'
export type CriterionResult = 'APPROVED' | 'OBSERVED' | 'REJECTED'
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type InspectionStatus = 'ASSIGNED' | 'IN_PROGRESS' | 'CLOSED'
export type CertificateStatus = 'VALID' | 'SUSPENDED' | 'EXPIRED'
export type ActionStatus = 'PENDING_PLANNING' | 'PLANNED' | 'EXECUTION_REPORTED' | 'CLOSED' | 'VOIDED'
export type EvidenceType = 'PHOTOGRAPH' | 'DOCUMENT'

export interface Party {
  id: string
  name: string
  kind: PartyKind
}

export interface AssetTypeInfo {
  name: AssetType
  characteristics: string[]
  subsystems: string[]
}

export interface Asset {
  id: string
  name: string
  assetType: AssetType
  characteristics: Record<string, string>
  responsibleId: string
  responsibleName: string
  location: string
  subsystems: string[]
  jurisdiction: string
}

export interface Outcome {
  code: string
  result: CriterionResult
  severity: Severity | null
  description: string
}

export interface Band {
  lower: number | null
  lowerInclusive: boolean
  upper: number | null
  upperInclusive: boolean
  outcome: Outcome
}

export interface Rule {
  type: 'YES_NO' | 'OPTIONS' | 'NUMERIC_RANGE'
  yes?: Outcome | null
  no?: Outcome | null
  options?: Record<string, Outcome> | null
  unit?: string | null
  minimum?: number | null
  maximum?: number | null
  bands?: Band[] | null
}

export interface EvidenceRequirement {
  type: EvidenceType
  label: string
  mandatory: boolean
  minimumCount: number
}

export interface Criterion {
  id: string
  rule: Rule
  subsystem: string | null
  evidence: EvidenceRequirement[]
}

export interface Section {
  name: string
  order: number
  criteria: Criterion[]
}

export interface SchemaVersion {
  id: string
  number: number
  publishedAt: string
  effectiveFrom: string
  sections: Section[]
}

export interface Schema {
  id: string
  name: string
  assetTypes: AssetType[]
  draft: { sections: Section[] } | null
  versions: SchemaVersion[]
  effectiveVersion: number | null
}

export interface Answer {
  type: 'YES_NO' | 'OPTION' | 'MEASUREMENT'
  affirmative?: boolean | null
  option?: string | null
  value?: number | null
  unit?: string | null
}

export interface EvidenceRecord {
  id: string
  requirementLabel: string
  type: EvidenceType
  reference: string
  attachedAt: string
}

export interface Evaluation {
  result: CriterionResult
  severity: Severity | null
  reasons: string[]
  fromRectification: boolean
}

export interface CriterionLine {
  criterionId: string
  section: string
  subsystem: string | null
  applicable: boolean
  answer: Answer | null
  evidence: EvidenceRecord[]
  evaluation: Evaluation | null
}

export interface Note {
  id: string
  criterionId: string | null
  text: string
  authorId: string
  recordedAt: string
}

export interface InspectionRow {
  id: string
  assetId: string
  inspectorId: string
  expectedDate: string
  status: InspectionStatus
}

export interface Inspection extends InspectionRow {
  schemaVersion: string | null
  startedAt: string | null
  closedAt: string | null
  subsystems: string[]
  criteria: CriterionLine[]
  notes: Note[]
}

export interface Plan {
  work: string
  executor: string
  dueDate: string
}

export interface Execution {
  statement: string
  evidenceReferences: string[]
  reportedBy: string
  reportedAt: string
}

export interface Verification {
  satisfactory: boolean
  reason: string
  verifiedBy: string
  verifiedAt: string
}

export interface CorrectiveAction {
  id: string
  status: ActionStatus
  planningDueDate: string
  deadline: string
  plan: Plan | null
  executions: Execution[]
  verifications: Verification[]
  closedAt: string | null
  deadlineBreached: boolean
  blocksCertification: boolean
}

export interface Finding {
  id: string
  inspectionId: string
  criterionId: string
  assetId: string
  inspectorId: string
  responsibleId: string
  result: CriterionResult
  severity: Severity | null
  reasons: string[]
  presentedEvidence: string[]
  createdAt: string
  obligationVoided: boolean
  pendingNonConformity: boolean
  correctiveActions: CorrectiveAction[]
}

export interface Certificate {
  id: string
  assetId: string
  backingInspectionId: string
  schemaVersion: string
  scope: 'GLOBAL' | 'PARTIAL'
  subsystem: string | null
  status: CertificateStatus
  mode: 'REGULAR' | 'CONDITIONAL'
  issuedAt: string
  expiresAt: string
}

/** A blocker as the API renders it: the class name in `type` plus its own fields. */
export interface Blocker {
  type: string
  [field: string]: unknown
}

export interface Assessment {
  blockers: Blocker[]
  mode?: string | null
  [field: string]: unknown
}

export interface OutboxEntry {
  seq: number
  eventType: string
  status: 'PENDING' | 'DONE' | 'DEAD'
  attempts: number
  lastError: string | null
  createdAtMillis: number
}

export interface JobsResult {
  expiredCertificates: number
  expiredActions: number
  deliveredEvents: number
}

export interface AuditEntry {
  element: { type: string; id: string }
  action: string
  occurredAt: string
  actor: unknown
  reason: string | null
  detail: unknown
}
