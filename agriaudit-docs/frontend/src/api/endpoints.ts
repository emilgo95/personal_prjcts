import { api } from './client';
import type {
  Attachment,
  AuditDetail,
  AuditItemResult,
  AuditRequest,
  AuditPage,
  ImportPreview,
  IntegrityReport,
  TracePage,
  TraceQuery,
  TraceSeal,
  AuditSummary,
  CorrectiveAction,
  CorrectiveActionRequest,
  Dashboard,
  DocumentOption,
  EvidenceAnalysis,
  Farm,
  FarmRequest,
  Features,
  Finding,
  FindingRequest,
  GeneratedDocument,
  Regulation,
  ResultUpdate,
  ScoreResult,
  StandardDetail,
  StandardSummary,
  TokenResponse,
  UserDto,
  PrivacySettings,
  EvidenceType,
  EvidenceRecord,
  EvidenceRecordRequest,
  EvidenceRecordDetail,
  EvidenceRecordChange,
  EvidenceMatrix,
  EvidenceMatrixEntry,
  EvidenceCoverage,
  EvidenceMap,
  SubmissionCheck,
  FindingList,
  FindingView,
  EvidenceHealth,
  BlockedUser,
  PersonalDataItem,
} from './types';

export const AuthApi = {
  login: (email: string, password: string) =>
    api.post<TokenResponse>('/auth/login', { email, password }).then((r) => r.data),
  me: () => api.get<UserDto>('/auth/me').then((r) => r.data),
  /**
   * Cambia la contraseña propia y abre una sesión nueva: devuelve tokens nuevos (las demás sesiones quedan cerradas).
   * Lo que devuelve son tokens de sesión, como el login, no la contraseña.
   */
  renewLogin: (currentPassword: string, newPassword: string) =>
    api.post<TokenResponse>('/auth/password', { currentPassword, newPassword }).then((r) => r.data),
};

/** Descarga un fichero protegido (con el token) y lo guarda desde el navegador. */
async function saveBlob(url: string, params: object, fileName: string) {
  const res = await api.get<Blob>(url, { params, responseType: 'blob' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(res.data);
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(link.href);
}

export const CatalogApi = {
  /** Con {@code all} incluye los ocultos y archivados (para gestionarlos). */
  standards: (all = false) => api.get<StandardSummary[]>('/standards', { params: { all } }).then((r) => r.data),
  previewImport: (form: FormData) => api
      .post<ImportPreview>('/standards/import/preview', form, { headers: { 'Content-Type': 'multipart/form-data' } })
      .then((r) => r.data),
  importStandard: (form: FormData) => api
      .post<StandardSummary>('/standards/import', form, { headers: { 'Content-Type': 'multipart/form-data' } })
      .then((r) => r.data),
  downloadTemplate: () => saveBlob('/standards/import/template', {}, 'plantilla-listado.xlsx'),
  setArchived: (code: string, value: boolean) =>
    api.put(`/standards/${code}/archived`, null, { params: { value } }).then(() => undefined),
  setHidden: (code: string, value: boolean) =>
    api.put(`/standards/${code}/hidden`, null, { params: { value } }).then(() => undefined),
  standard: (code: string) => api.get<StandardDetail>(`/standards/${code}`).then((r) => r.data),
  regulations: () => api.get<Regulation[]>('/regulations').then((r) => r.data),
};

export const EvidenceApi = {
  types: () => api.get<EvidenceType[]>('/evidence/types').then((r) => r.data),
  records: (params: { farmId?: number; typeCode?: string } = {}) =>
    api.get<EvidenceRecord[]>('/evidence/records', { params }).then((r) => r.data),
  record: (id: number) => api.get<EvidenceRecordDetail>(`/evidence/records/${id}`).then((r) => r.data),
  create: (body: EvidenceRecordRequest) => api.post<EvidenceRecordChange>('/evidence/records', body).then((r) => r.data),
  update: (id: number, body: EvidenceRecordRequest) =>
    api.put<EvidenceRecordChange>(`/evidence/records/${id}`, body).then((r) => r.data),
  remove: (id: number) => api.delete(`/evidence/records/${id}`).then(() => undefined),
  upload: (id: number, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api
      .post<Attachment>(`/evidence/records/${id}/attachments`, form, { headers: { 'Content-Type': 'multipart/form-data' } })
      .then((r) => r.data);
  },
  health: () => api.get<EvidenceHealth>('/evidence/health').then((r) => r.data),
  matrix: () => api.get<EvidenceMatrix>('/evidence/matrix').then((r) => r.data),
  addEquivalence: (body: { typeCode: string; standardCode: string; controlRef: string }) =>
    api.post<EvidenceMatrixEntry>('/evidence/matrix', body).then((r) => r.data),
  validateEquivalence: (id: number, validated: boolean) =>
    api.put<EvidenceMatrixEntry>(`/evidence/matrix/${id}/validation`, { validated }).then((r) => r.data),
  removeEquivalence: (id: number) => api.delete(`/evidence/matrix/${id}`).then(() => undefined),
  coverage: (auditId: number) => api.get<EvidenceCoverage>(`/audits/${auditId}/evidence-coverage`).then((r) => r.data),
  /** Mapa de la auditoría: qué justifica cada punto y en qué puntos de otras normas vale la misma evidencia. */
  map: (auditId: number) => api.get<EvidenceMap>(`/audits/${auditId}/evidence-map`).then((r) => r.data),
  link: (auditId: number, controlPointId: number, recordId: number) =>
    api.post<EvidenceCoverage>(`/audits/${auditId}/evidence-links`, { controlPointId, recordId }).then((r) => r.data),
  unlink: (auditId: number, linkId: number) =>
    api.delete<EvidenceCoverage>(`/audits/${auditId}/evidence-links/${linkId}`).then((r) => r.data),
};

export const FarmApi = {
  list: () => api.get<Farm[]>('/farms').then((r) => r.data),
  get: (id: number) => api.get<Farm>(`/farms/${id}`).then((r) => r.data),
  create: (body: FarmRequest) => api.post<Farm>('/farms', body).then((r) => r.data),
  update: (id: number, body: FarmRequest) => api.put<Farm>(`/farms/${id}`, body).then((r) => r.data),
  remove: (id: number) => api.delete(`/farms/${id}`).then(() => undefined),
};

export const AuditApi = {
  /** Con `q`, solo las auditorías que contienen todas sus palabras (busca en el servidor, en todas las páginas). */
  list: (page = 0, size = 25, q?: string) =>
    api.get<AuditPage>('/audits', { params: { page, size, q: q || undefined } }).then((r) => r.data),
  get: (id: number) => api.get<AuditDetail>(`/audits/${id}`).then((r) => r.data),
  create: (body: AuditRequest) => api.post<AuditDetail>('/audits', body).then((r) => r.data),
  update: (id: number, body: AuditRequest) => api.put<AuditDetail>(`/audits/${id}`, body).then((r) => r.data),
  /** Al enviar, `lateJustifications` (id de la NC → motivo) justifica las NC vencidas. */
  transition: (id: number, status: string, lateJustifications?: Record<number, string>) =>
    api.post<AuditDetail>(`/audits/${id}/transition`, { status, lateJustifications }).then((r) => r.data),
  /** Lo que impide (NC por revisar) o condiciona (NC vencidas) el envío. */
  submissionCheck: (id: number) =>
    api.get<SubmissionCheck>(`/audits/${id}/submission-check`).then((r) => r.data),
  results: (id: number) => api.get<AuditItemResult[]>(`/audits/${id}/results`).then((r) => r.data),
  updateResults: (id: number, results: ResultUpdate[]) =>
    api.put<AuditItemResult[]>(`/audits/${id}/results`, { results }).then((r) => r.data),
  score: (id: number) => api.get<ScoreResult>(`/audits/${id}/score`).then((r) => r.data),
};

export const FindingApi = {
  list: (auditId: number) => api.get<Finding[]>(`/audits/${auditId}/findings`).then((r) => r.data),
  /** Todas las NC de la organización en una vista (mismas reglas que las cifras del panel). */
  listAll: (view: FindingView) => api.get<FindingList>('/findings', { params: { view } }).then((r) => r.data),
  create: (auditId: number, body: FindingRequest) =>
    api.post<Finding>(`/audits/${auditId}/findings`, body).then((r) => r.data),
  update: (auditId: number, findingId: number, body: FindingRequest) =>
    api.put<Finding>(`/audits/${auditId}/findings/${findingId}`, body).then((r) => r.data),
  remove: (auditId: number, findingId: number) =>
    api.delete(`/audits/${auditId}/findings/${findingId}`).then(() => undefined),
  addAction: (auditId: number, findingId: number, body: CorrectiveActionRequest) =>
    api
      .post<CorrectiveAction>(`/audits/${auditId}/findings/${findingId}/corrective-actions`, body)
      .then((r) => r.data),
  updateAction: (auditId: number, findingId: number, caId: number, body: CorrectiveActionRequest) =>
    api
      .put<CorrectiveAction>(`/audits/${auditId}/findings/${findingId}/corrective-actions/${caId}`, body)
      .then((r) => r.data),
  deleteAction: (auditId: number, findingId: number, caId: number) =>
    api.delete(`/audits/${auditId}/findings/${findingId}/corrective-actions/${caId}`).then(() => undefined),
  verifyAction: (auditId: number, findingId: number, caId: number) =>
    api
      .post<CorrectiveAction>(`/audits/${auditId}/findings/${findingId}/corrective-actions/${caId}/verify`)
      .then((r) => r.data),
  close: (auditId: number, findingId: number) =>
    api.post<Finding>(`/audits/${auditId}/findings/${findingId}/close`).then((r) => r.data),
};

const clean = (q: TraceQuery) => Object.fromEntries(Object.entries(q).filter(([, v]) => v !== '' && v != null));

export const TraceApi = {
  search: (q: TraceQuery) => api.get<TracePage>('/trace', { params: clean(q) }).then((r) => r.data),
  forAudit: (auditId: number, page = 0, size = 50) =>
    api.get<TracePage>(`/audits/${auditId}/trace`, { params: { page, size } }).then((r) => r.data),
  forFinding: (auditId: number, findingId: number) =>
    api.get<TracePage>(`/audits/${auditId}/findings/${findingId}/trace`, { params: { size: 100 } }).then((r) => r.data),
  verify: () => api.get<IntegrityReport>('/trace/verify').then((r) => r.data),
  seals: () => api.get<TraceSeal[]>('/trace/seals').then((r) => r.data),
  sealNow: () => api.post<TraceSeal & { message?: string }>('/trace/seals').then((r) => r.data),
  downloadCsv: (q: TraceQuery) => saveBlob('/trace/export', clean({ ...q, page: undefined, size: undefined }), 'trazabilidad.csv'),
  downloadSeals: () => saveBlob('/trace/seals/export', {}, 'sellos-trazabilidad.txt'),
};

export const AttachmentApi = {
  uploadForControlPoint: (auditId: number, controlPointId: number, file: File, personalData = false) => {
    const form = new FormData();
    form.append('file', file);
    if (personalData) form.append('containsPersonalData', 'true');
    return api
      .post<Attachment>(`/audits/${auditId}/results/${controlPointId}/attachments`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data);
  },
  uploadForFinding: (auditId: number, findingId: number, file: File, personalData = false) => {
    const form = new FormData();
    form.append('file', file);
    if (personalData) form.append('containsPersonalData', 'true');
    return api
      .post<Attachment>(`/audits/${auditId}/findings/${findingId}/attachments`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data);
  },
  markPersonalData: (id: number, containsPersonalData: boolean) =>
    api.put<Attachment>(`/attachments/${id}/personal-data`, { containsPersonalData }).then((r) => r.data),
  remove: (id: number) => api.delete(`/attachments/${id}`).then(() => undefined),
  download: (id: number) =>
    api.get(`/attachments/${id}/download`, { responseType: 'blob' }).then((r) => r.data as Blob),
};

export const DocumentApi = {
  options: (auditId: number) =>
    api.get<DocumentOption[]>(`/audits/${auditId}/document-options`).then((r) => r.data),
  listForAudit: (auditId: number) =>
    api.get<GeneratedDocument[]>(`/audits/${auditId}/documents`).then((r) => r.data),
  listAll: () => api.get<GeneratedDocument[]>('/documents').then((r) => r.data),
  generate: (auditId: number, documentType: string, format: string) =>
    api
      .post<GeneratedDocument>(`/audits/${auditId}/documents/generate`, { documentType, format })
      .then((r) => r.data),
  download: (id: number) =>
    api.get(`/documents/${id}/download`, { responseType: 'blob' }).then((r) => r.data as Blob),
};

export const DashboardApi = {
  load: () => api.get<Dashboard>('/dashboard').then((r) => r.data),
};

export const FeaturesApi = {
  get: () => api.get<Features>('/features').then((r) => r.data),
};

export const AnalysisApi = {
  analyzeEvidence: (auditId: number, attachmentId: number, controlPointId?: number) =>
    api
      .post<EvidenceAnalysis>(
        `/audits/${auditId}/evidence/${attachmentId}/analyze`,
        undefined,
        { params: controlPointId ? { controlPointId } : undefined },
      )
      .then((r) => r.data),
};

export const UserApi = {
  list: () => api.get<UserDto[]>('/users').then((r) => r.data),
  create: (body: { email: string; fullName?: string; role: string; password: string }) =>
    api.post<UserDto>('/users', body).then((r) => r.data),
  update: (
    id: number,
    body: { fullName?: string; role: string; active?: boolean; password?: string },
  ) => api.put<UserDto>(`/users/${id}`, body).then((r) => r.data),
  block: (id: number) => api.post<UserDto>(`/users/${id}/block`).then((r) => r.data),
  /** Genera una contraseña temporal para otro usuario; solo se recibe en esta respuesta. */
  resetPassword: (id: number) =>
    api.post<{ temporaryPassword: string }>(`/users/${id}/reset-password`).then((r) => r.data),
  dataExport: (id: number) =>
    api.get(`/users/${id}/data-export`, { responseType: 'blob' }).then((r) => r.data as Blob),
};

export const PrivacyApi = {
  get: () => api.get<PrivacySettings>('/organization/privacy').then((r) => r.data),
  setRetention: (blockedRetentionMonths: number | null) =>
    api.put<PrivacySettings>('/organization/privacy', { blockedRetentionMonths }).then((r) => r.data),
  blocked: () => api.get<BlockedUser[]>('/organization/privacy/blocked').then((r) => r.data),
  inventory: () => api.get<PersonalDataItem[]>('/organization/privacy/inventory').then((r) => r.data),
  exportAll: () =>
    api.get('/organization/export', { responseType: 'blob' }).then((r) => r.data as Blob),
};

export function downloadBlob(blob: Blob, fileName: string) {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}
