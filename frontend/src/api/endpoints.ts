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
} from './types';

export const AuthApi = {
  login: (email: string, password: string) =>
    api.post<TokenResponse>('/auth/login', { email, password }).then((r) => r.data),
  me: () => api.get<UserDto>('/auth/me').then((r) => r.data),
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

export const FarmApi = {
  list: () => api.get<Farm[]>('/farms').then((r) => r.data),
  get: (id: number) => api.get<Farm>(`/farms/${id}`).then((r) => r.data),
  create: (body: FarmRequest) => api.post<Farm>('/farms', body).then((r) => r.data),
  update: (id: number, body: FarmRequest) => api.put<Farm>(`/farms/${id}`, body).then((r) => r.data),
  remove: (id: number) => api.delete(`/farms/${id}`).then(() => undefined),
};

export const AuditApi = {
  list: (page = 0, size = 25) =>
    api.get<AuditPage>('/audits', { params: { page, size } }).then((r) => r.data),
  get: (id: number) => api.get<AuditDetail>(`/audits/${id}`).then((r) => r.data),
  create: (body: AuditRequest) => api.post<AuditDetail>('/audits', body).then((r) => r.data),
  update: (id: number, body: AuditRequest) => api.put<AuditDetail>(`/audits/${id}`, body).then((r) => r.data),
  transition: (id: number, status: string) =>
    api.post<AuditDetail>(`/audits/${id}/transition`, { status }).then((r) => r.data),
  results: (id: number) => api.get<AuditItemResult[]>(`/audits/${id}/results`).then((r) => r.data),
  updateResults: (id: number, results: ResultUpdate[]) =>
    api.put<AuditItemResult[]>(`/audits/${id}/results`, { results }).then((r) => r.data),
  score: (id: number) => api.get<ScoreResult>(`/audits/${id}/score`).then((r) => r.data),
};

export const FindingApi = {
  list: (auditId: number) => api.get<Finding[]>(`/audits/${auditId}/findings`).then((r) => r.data),
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
  uploadForControlPoint: (auditId: number, controlPointId: number, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api
      .post<Attachment>(`/audits/${auditId}/results/${controlPointId}/attachments`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data);
  },
  uploadForFinding: (auditId: number, findingId: number, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api
      .post<Attachment>(`/audits/${auditId}/findings/${findingId}/attachments`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data);
  },
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
