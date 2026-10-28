export const complianceLabels: Record<string, string> = {
  COMPLIANT: 'Conforme',
  NON_COMPLIANT: 'No conforme',
  NOT_APPLICABLE: 'No aplica',
  NOT_ASSESSED: 'Sin evaluar',
};

export const complianceColors: Record<string, 'success' | 'error' | 'default' | 'warning'> = {
  COMPLIANT: 'success',
  NON_COMPLIANT: 'error',
  NOT_APPLICABLE: 'default',
  NOT_ASSESSED: 'warning',
};

export const levelLabels: Record<string, string> = {
  MAJOR_MUST: 'Obligación mayor',
  MINOR_MUST: 'Obligación menor',
  RECOMMENDATION: 'Recomendación',
  CORE: 'Requisito esencial',
  IMPROVEMENT_L1: 'Mejora nivel 1',
  IMPROVEMENT_L2: 'Mejora nivel 2',
};

export const classificationLabels: Record<string, string> = {
  CRITICAL: 'Crítica',
  MAJOR: 'Mayor',
  MINOR: 'Menor',
  OBSERVATION: 'Observación',
};

export const classificationColors: Record<string, 'error' | 'warning' | 'info' | 'default'> = {
  CRITICAL: 'error',
  MAJOR: 'warning',
  MINOR: 'info',
  OBSERVATION: 'default',
};

export const findingStatusColors: Record<string, 'default' | 'info' | 'primary' | 'success'> = {
  OPEN: 'default',
  IN_PROGRESS: 'info',
  RESOLVED: 'primary',
  VERIFIED: 'success',
  CLOSED: 'success',
};

export const findingStatusLabels: Record<string, string> = {
  OPEN: 'Abierta',
  IN_PROGRESS: 'En curso',
  RESOLVED: 'Resuelta',
  VERIFIED: 'Verificada',
  CLOSED: 'Cerrada',
};

export const caStatusLabels: Record<string, string> = {
  PENDING: 'Pendiente',
  IN_PROGRESS: 'En curso',
  COMPLETED: 'Completada',
  VERIFIED: 'Verificada',
  REJECTED: 'Rechazada',
};

export const auditTypeLabels: Record<string, string> = {
  SELF_ASSESSMENT: 'Autoevaluación',
  INTERNAL: 'Auditoría interna',
  CB_INITIAL: 'Certificación inicial',
  SURVEILLANCE: 'Seguimiento',
  RECERTIFICATION: 'Recertificación',
};

export const auditStatusLabels: Record<string, string> = {
  DRAFT: 'Borrador',
  IN_PROGRESS: 'En curso',
  CLOSING: 'Cierre',
  SUBMITTED: 'Enviada',
  CLOSED: 'Cerrada',
};

export const auditStatusColors: Record<string, 'default' | 'info' | 'warning' | 'primary' | 'success'> = {
  DRAFT: 'default',
  IN_PROGRESS: 'info',
  CLOSING: 'warning',
  SUBMITTED: 'primary',
  CLOSED: 'success',
};

export const roleLabels: Record<string, string> = {
  ADMIN: 'Administrador',
  AUDITOR: 'Auditor',
  QUALITY: 'Calidad',
  VIEWER: 'Lectura',
};

export const overallResultLabels: Record<string, string> = {
  PASS: 'APTO',
  FAIL: 'NO APTO',
  PENDING: 'PENDIENTE',
  REPORT_ONLY: 'INFORME',
};

export const overallResultColors: Record<string, 'success' | 'error' | 'warning' | 'info'> = {
  PASS: 'success',
  FAIL: 'error',
  PENDING: 'warning',
  REPORT_ONLY: 'info',
};

const standardShortLabels: Record<string, string> = {
  GLOBALGAP_IFA_V6: 'GLOBALG.A.P. IFA v6',
  GRASP_V2: 'GRASP v2',
  SMETA_6_1: 'SMETA 6.1',
};

/** Nombre corto de un estándar para tablas y listas; si no lo conocemos, el nombre completo o el código. */
export function standardShort(code: string | null | undefined, fallback?: string | null): string {
  if (code && standardShortLabels[code]) return standardShortLabels[code];
  return fallback ?? code ?? '—';
}

/** Porcentaje con coma decimal y espacio fino antes del símbolo (no parte línea): `90,0 %`. */
export function formatPct(value: number): string {
  return `${value.toLocaleString('es-ES', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} %`;
}

export function label(map: Record<string, string>, key: string | null | undefined): string {
  if (!key) return '—';
  return map[key] ?? key;
}

export const traceActionLabels: Record<string, string> = {
  CREATE: 'Alta',
  UPDATE: 'Modificación',
  DELETE: 'Baja',
  TRANSITION: 'Cambio de estado',
  UPLOAD: 'Subida',
  GENERATE: 'Generación',
};

export const traceEntityLabels: Record<string, string> = {
  AUDIT: 'Auditoría',
  AUDIT_RESULT: 'Punto del checklist',
  FINDING: 'No conformidad',
  CORRECTIVE_ACTION: 'Acción correctiva',
  ATTACHMENT: 'Adjunto',
  DOCUMENT: 'Documento',
  USER: 'Usuario',
  FARM: 'Explotación',
};
