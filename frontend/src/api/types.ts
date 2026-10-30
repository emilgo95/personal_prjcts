export interface UserDto {
  id: number;
  /** null si los datos del usuario están bloqueados: su correo ya no se muestra. */
  email: string | null;
  fullName: string | null;
  role: 'ADMIN' | 'AUDITOR' | 'QUALITY' | 'VIEWER';
  organizationId: number;
  organizationName: string;
  active: boolean;
  blocked: boolean;
  /** Tiene una contraseña temporal (puesta por un administrador): debe cambiarla antes de usar la aplicación. */
  mustChangePassword: boolean;
}

export interface PrivacySettings {
  blockedRetentionMonths: number;
  organizationValue: number | null;
  defaultMonths: number;
  minMonths: number;
  maxMonths: number;
  blockedCount: number;
  nextDestruction: string | null;
}

/** Tipo de evidencia reutilizable (análisis de agua, evaluación de riesgos…). */
export interface EvidenceType {
  code: string;
  name: string;
  description: string | null;
  personalDataNotice: string | null;
}

export type EvidenceStatus = 'CURRENT' | 'DUE_SOON' | 'EXPIRED';
export type EvidenceHealthStatus = EvidenceStatus | 'UNFAVORABLE' | 'MISSING';
export type EvidenceOutcome = 'FAVORABLE' | 'UNFAVORABLE' | 'UNRATED';

export interface EvidenceRecord {
  id: number;
  farmId: number;
  farmName: string | null;
  typeCode: string;
  typeName: string;
  title: string;
  issuedOn: string;
  validUntil: string | null;
  outcome: EvidenceOutcome;
  notes: string | null;
  containsPersonalData: boolean;
  status: EvidenceStatus;
  documents: number;
  usedIn: number;
  createdAt: string;
  updatedAt: string;
}

export interface EvidenceRecordRequest {
  farmId: number;
  typeCode: string;
  title: string;
  issuedOn: string;
  validUntil: string | null;
  outcome: EvidenceOutcome;
  notes: string | null;
  containsPersonalData: boolean;
}

export interface EvidenceUse {
  linkId: number;
  auditId: number;
  auditReference: string;
  auditStatus: string;
  controlPointId: number;
  controlRef: string | null;
  standardCode: string | null;
}

export interface EvidenceRecordDetail {
  record: EvidenceRecord;
  attachments: Attachment[];
  uses: EvidenceUse[];
}

export interface EvidenceRecordChange {
  record: EvidenceRecord;
  affectedAudits: number;
}

export interface EvidenceMatrixEntry {
  id: number;
  typeCode: string;
  typeName: string;
  standardCode: string;
  standardName: string | null;
  controlRef: string;
  controlTitle: string | null;
  status: 'DRAFT' | 'VALIDATED';
}

export interface EvidenceMatrix {
  types: EvidenceType[];
  entries: EvidenceMatrixEntry[];
}

export interface EvidenceCoverageRecord {
  id: number;
  title: string;
  issuedOn: string;
  validUntil: string | null;
  status: EvidenceStatus;
  outcome: EvidenceOutcome;
  documents: number;
  linkId: number | null;
  linkable: boolean;
  notLinkableReason: string | null;
}

export interface EvidenceTypeCoverage {
  typeCode: string;
  typeName: string;
  matrixStatus: 'DRAFT' | 'VALIDATED';
  health: EvidenceHealthStatus;
  records: EvidenceCoverageRecord[];
}

export interface EvidenceCoverageItem {
  controlPointId: number;
  controlRef: string;
  controlTitle: string;
  types: EvidenceTypeCoverage[];
  covered: boolean;
  alert: boolean;
}

/**
 * Estado de un punto en el mapa de evidencias: COVERED (vinculada y vigente), DUE_SOON (vinculada, por renovar),
 * ALERT (vinculada pero vencida o desfavorable), SUGGESTED (registrada, por confirmar), ATTACHMENT (solo adjuntos
 * del checklist) o MISSING (falta evidencia).
 */
export type EvidenceMapState = 'COVERED' | 'DUE_SOON' | 'ALERT' | 'SUGGESTED' | 'ATTACHMENT' | 'MISSING';

export interface EvidenceMapEvidence {
  recordId: number;
  title: string;
  typeName: string;
  status: EvidenceStatus;
  outcome: EvidenceOutcome;
  validUntil: string | null;
  /** true si está vinculada al punto; false si es una candidata por confirmar. */
  linked: boolean;
}

export interface EvidenceMapRow {
  controlPointId: number;
  controlRef: string;
  controlTitle: string;
  chapterCode: string | null;
  level: string | null;
  compliance: string | null;
  evidenceTypes: string[];
  evidence: EvidenceMapEvidence[];
  attachments: number;
  /** Por código de otra norma, los puntos de esa norma donde vale la misma evidencia. */
  equivalences: Record<string, string[]>;
  state: EvidenceMapState;
}

export interface EvidenceMap {
  auditId: number;
  farmId: number;
  standardCode: string;
  standardName: string;
  locked: boolean;
  otherStandards: Array<{ code: string; name: string }>;
  rows: EvidenceMapRow[];
  /** Puntos por estado, más POINTS (puntos de la norma) y ATTACHMENTS (adjuntos del checklist). */
  summary: Record<string, number>;
}

export interface EvidenceCoverage {
  auditId: number;
  farmId: number;
  locked: boolean;
  items: EvidenceCoverageItem[];
}

export interface EvidenceHealthCell {
  status: EvidenceHealthStatus;
  recordId: number | null;
  validUntil: string | null;
}

export interface EvidenceFarmHealth {
  farmId: number;
  farmName: string;
  cells: Record<string, EvidenceHealthCell>;
}

export interface EvidenceHealth {
  types: EvidenceType[];
  farms: EvidenceFarmHealth[];
  summary: Record<EvidenceHealthStatus, number>;
  renewalWindowDays: number;
}

export interface BlockedUser {
  userId: number;
  role: string | null;
  blockedAt: string;
  destroyAfter: string;
  blockedBy: string | null;
}

export interface PersonalDataItem {
  category: string;
  data: string;
  whose: string;
  fields: string[];
  visibleTo: string;
  retention: string;
  notes: string | null;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInMinutes: number;
  user: UserDto;
}

export interface RegulationRef {
  code: string;
  title: string;
  jurisdiction: string | null;
  sourceUrl: string | null;
}

export interface Regulation {
  id: number;
  code: string;
  title: string;
  jurisdiction: string | null;
  scopeArea: string | null;
  summary: string | null;
  keyObligations: string | null;
  sourceUrl: string | null;
}

export interface ControlPoint {
  id: number;
  ref: string;
  title: string;
  description: string | null;
  level: string;
  guidance: string | null;
  evidenceExamples: string | null;
  applicableScopes: string | null;
  chapterCode: string;
  chapterTitle: string;
  regulations: RegulationRef[];
}

export interface Chapter {
  id: number;
  code: string;
  title: string;
  description: string | null;
  sortOrder: number;
  controlPoints: ControlPoint[];
}

export interface StandardSummary {
  id: number;
  code: string;
  version: string | null;
  name: string;
  ownerBody: string | null;
  category: string;
  description: string | null;
  officialUrl: string | null;
  chapterCount: number;
  controlPointCount: number;
  /** Estándar propio de la organización (importado); si no, es de demostración. */
  custom: boolean;
  hidden: boolean;
  archived: boolean;
}

export interface ImportIssue {
  row: number;
  column: string;
  message: string;
}

export interface ImportPreview {
  valid: boolean;
  storedCode: string;
  chapterCount: number;
  pointCount: number;
  levelCounts: Record<string, number>;
  issues: ImportIssue[];
  totalIssues: number;
  sample: { row: number; chapterCode: string; ref: string; title: string; level: string }[];
}

export interface StandardDetail
  extends Omit<StandardSummary, 'chapterCount' | 'controlPointCount' | 'custom' | 'hidden' | 'archived'> {
  scoringRule: string | null;
  chapters: Chapter[];
  closureRule: ClosureRule | null;
}

/** Plazo de cierre de NC por gravedad, en días desde la auditoría; null = sin plazo automático. */
export interface ClosureRule {
  criticalDays: number | null;
  majorDays: number | null;
  minorDays: number | null;
  observationDays: number | null;
}

export interface Farm {
  id: number;
  legalName: string;
  tradeName: string | null;
  registrationNumber: string | null;
  address: string | null;
  city: string | null;
  region: string | null;
  country: string | null;
  latitude: number | null;
  longitude: number | null;
  totalHectares: number | null;
  crops: string | null;
  productionAreas: string | null;
  contactName: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  notes: string | null;
}

export type FarmRequest = Omit<Farm, 'id'>;

export interface FieldChange {
  field: string;
  label: string;
  oldValue: string | null;
  newValue: string | null;
}

export interface TraceEntry {
  id: number;
  seq: number;
  occurredAt: string;
  createdAt: string;
  userId: number | null;
  username: string;
  userFullName: string | null;
  userRole: string;
  entityType: string;
  entityId: number | null;
  entityRef: string | null;
  parentType: string | null;
  parentId: number | null;
  auditId: number | null;
  action: string;
  summary: string;
  changes: FieldChange[];
  prevHash: string;
  hash: string;
  signature: string;
}

export interface TracePage {
  content: TraceEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface IntegrityReport {
  ok: boolean;
  entriesChecked: number;
  sealsChecked: number;
  firstBrokenSeq: number | null;
  message: string;
  verifiedAt: string;
}

export interface TraceSeal {
  id: number;
  sealedAt: string;
  lastSeq: number;
  lastHash: string;
  signature: string;
}

export interface TraceQuery {
  user?: string;
  action?: string;
  entityType?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface AuditPage {
  content: AuditSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditSummary {
  id: number;
  reference: string;
  farmId: number;
  farmName: string;
  standardCode: string;
  standardName: string;
  auditType: string;
  status: string;
  plannedStartDate: string | null;
  actualDate: string | null;
  leadAuditor: string | null;
  createdAt: string;
  findingsCount: number;
  assessedCount: number;
  totalControlPoints: number;
  progressPct: number;
}

export interface AuditDetail {
  id: number;
  reference: string;
  standardCode: string;
  standardName: string;
  standardVersion: string | null;
  auditType: string;
  status: string;
  scopeDescription: string | null;
  plannedStartDate: string | null;
  plannedEndDate: string | null;
  actualDate: string | null;
  leadAuditor: string | null;
  auditTeam: string | null;
  siteAddress: string | null;
  productsInScope: string | null;
  createdAt: string;
  updatedAt: string;
  farm: Farm;
  totalControlPoints: number;
  assessedCount: number;
  progressPct: number;
  findingsCount: number;
  allowedTransitions: string[];
}

export interface AuditRequest {
  farmId: number;
  standardCode: string;
  auditType: string;
  scopeDescription?: string | null;
  plannedStartDate?: string | null;
  plannedEndDate?: string | null;
  actualDate?: string | null;
  leadAuditor?: string | null;
  auditTeam?: string | null;
  siteAddress?: string | null;
  productsInScope?: string | null;
}

export type ComplianceValue = 'COMPLIANT' | 'NON_COMPLIANT' | 'NOT_APPLICABLE' | 'NOT_ASSESSED';

export interface Attachment {
  id: number;
  entityType: 'AUDIT' | 'AUDIT_ITEM_RESULT' | 'FINDING';
  entityId: number;
  fileName: string;
  contentType: string | null;
  sizeBytes: number | null;
  uploadedBy: string | null;
  uploadedAt: string;
  image: boolean;
  /** Solo la reciben administración, calidad y quien lo subió; para el resto es null. */
  containsPersonalData: boolean | null;
}

export interface AuditItemResult {
  resultId: number | null;
  controlPointId: number;
  ref: string;
  title: string;
  description: string | null;
  guidance: string | null;
  level: string;
  applicableScopes: string | null;
  chapterCode: string;
  chapterTitle: string;
  compliance: ComplianceValue;
  evidenceNotes: string | null;
  auditorComment: string | null;
  assessedAt: string | null;
  assessedBy: string | null;
  attachments: Attachment[];
}

export interface ResultUpdate {
  controlPointId: number;
  compliance: ComplianceValue;
  evidenceNotes?: string | null;
  auditorComment?: string | null;
}

export interface CorrectiveAction {
  id: number;
  findingId: number;
  rootCause: string | null;
  correction: string | null;
  preventiveAction: string | null;
  responsiblePerson: string | null;
  targetDate: string | null;
  completedDate: string | null;
  verificationNotes: string | null;
  verifiedBy: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface Finding {
  id: number;
  auditId: number;
  controlPointId: number | null;
  controlPointRef: string | null;
  controlPointTitle: string | null;
  reference: string | null;
  classification: string;
  title: string | null;
  description: string | null;
  area: string | null;
  dueDate: string | null;
  status: string;
  raisedAt: string;
  correctiveActions: CorrectiveAction[];
  attachments: Attachment[];
  /** Momento en que quedó cerrada o verificada (reloj de cierre). */
  closedAt: string | null;
}

export interface FindingRequest {
  controlPointId?: number | null;
  classification: string;
  title?: string | null;
  description?: string | null;
  area?: string | null;
  dueDate?: string | null;
  status?: string | null;
}

export interface CorrectiveActionRequest {
  rootCause?: string | null;
  correction?: string | null;
  preventiveAction?: string | null;
  responsiblePerson?: string | null;
  targetDate?: string | null;
  completedDate?: string | null;
  verificationNotes?: string | null;
  verifiedBy?: string | null;
  status?: string | null;
}

export interface LevelScore {
  level: string;
  total: number;
  compliant: number;
  nonCompliant: number;
  notApplicable: number;
  notAssessed: number;
  compliancePct: number | null;
}

export interface GroupScore {
  code: string;
  title: string;
  total: number;
  compliant: number;
  nonCompliant: number;
  notApplicable: number;
  notAssessed: number;
  compliancePct: number;
}

export interface ScoreResult {
  standardCode: string;
  standardName: string;
  scoringType: string;
  reportOnly: boolean;
  overallResult: string;
  majorMustMinPct: number | null;
  minorMustMinPct: number | null;
  levelBreakdown: LevelScore[];
  chapterBreakdown: GroupScore[];
  pillarBreakdown: GroupScore[];
  totalControlPoints: number;
  assessedCount: number;
  progressPct: number;
  notes: string[];
}

export interface DocumentOption {
  documentType: string;
  label: string;
  formats: string[];
}

export interface GeneratedDocument {
  id: number;
  auditId: number;
  auditReference: string | null;
  documentType: string;
  documentTypeLabel: string;
  standardCode: string | null;
  standardName: string | null;
  format: string;
  fileName: string;
  sizeBytes: number | null;
  sha256: string | null;
  generatedBy: string | null;
  generatedAt: string;
}

export interface Features {
  evidenceOcr: boolean;
}

export interface SuggestedFinding {
  title: string;
  description: string;
  classification: string;
}

export interface EvidenceAnalysis {
  attachmentId: number;
  fileName: string;
  engine: string;
  pages: number;
  truncated: boolean;
  meanConfidence: number;
  extractedText: string;
  suggestedEvidenceNote: string;
  suggestedCompliance: ComplianceValue | null;
  complianceRationale: string | null;
  suggestedFindings: SuggestedFinding[];
  warnings: string[];
}

export interface Deadline {
  findingId: number;
  auditId: number;
  auditReference: string | null;
  farmName: string | null;
  findingReference: string | null;
  title: string | null;
  classification: string;
  status: string;
  dueDate: string;
  daysLeft: number;
}

export interface Dashboard {
  farmsCount: number;
  auditsTotal: number;
  auditsByStatus: Record<string, number>;
  openFindings: number;
  overdueFindings: number;
  /** Vencen en 7 días o menos (incluye hoy). */
  dueSoonFindings: number;
  /** Vencen en 2 días o menos (incluye hoy). */
  dueIn2Findings: number;
  /** Vencen en 14 días o menos (incluye hoy). */
  dueIn14Findings: number;
  /** NC cerradas en los últimos 12 meses que tenían fecha límite, y cuántas a tiempo. */
  closedWithDeadlineFindings: number;
  closedOnTimeFindings: number;
  documentsGenerated: number;
  upcomingDeadlines: Deadline[];
  recentAudits: AuditSummary[];
}
