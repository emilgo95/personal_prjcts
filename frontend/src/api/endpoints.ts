import { api } from './client';
import type {
  Attachment,
  AuditDetail,
  AuditItemResult,
  AuditRequest,
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

export const CatalogApi = {
  standards: () => api.get<StandardSummary[]>('/standards').then((r) => r.data),
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
  list: () => api.get<AuditSummary[]>('/audits').then((r) => r.data),
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
