export interface UserDto {
  id: number;
  email: string;
  fullName: string | null;
  role: 'ADMIN' | 'AUDITOR' | 'VIEWER';
  organizationId: number;
  organizationName: string;
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
}

export interface StandardDetail extends Omit<StandardSummary, 'chapterCount' | 'controlPointCount'> {
  scoringRule: string | null;
  chapters: Chapter[];
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

export interface Dashboard {
  farmsCount: number;
  auditsTotal: number;
  auditsByStatus: Record<string, number>;
  openFindings: number;
  overdueFindings: number;
  documentsGenerated: number;
  recentAudits: AuditSummary[];
}
