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

export function label(map: Record<string, string>, key: string | null | undefined): string {
  if (!key) return '—';
  return map[key] ?? key;
}
