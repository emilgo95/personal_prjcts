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

/**
 * Colores del estado de la auditoría: solo tonos fríos y neutros (gris, azul, índigo, violeta, pizarra). El rojo, el
 * naranja y el verde quedan reservados al progreso, para que un mismo color no signifique dos cosas en la misma pantalla.
 */
export const auditStatusStyle: Record<string, { bg: string; fg: string; solid: string }> = {
  DRAFT: { bg: '#eceff1', fg: '#455a64', solid: '#90a4ae' },
  IN_PROGRESS: { bg: '#e3f2fd', fg: '#0d47a1', solid: '#1e88e5' },
  CLOSING: { bg: '#e8eaf6', fg: '#283593', solid: '#5c6bc0' },
  SUBMITTED: { bg: '#f3e5f5', fg: '#6a1b9a', solid: '#ab47bc' },
  CLOSED: { bg: '#455a64', fg: '#ffffff', solid: '#455a64' },
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
  EVIDENCE_RECORD: 'Evidencia reutilizable',
  EVIDENCE_LINK: 'Vínculo de evidencia',
  EVIDENCE_MATRIX: 'Matriz de equivalencias',
};

/** Estado de una evidencia reutilizable: vigencia (y, en el panel de salud, también «desfavorable» y «falta»). */
export const evidenceStatusLabels: Record<string, string> = {
  CURRENT: 'Vigente',
  DUE_SOON: 'Por renovar',
  EXPIRED: 'Vencida',
  UNFAVORABLE: 'Desfavorable',
  MISSING: 'Falta',
};

export const evidenceStatusColors: Record<string, 'success' | 'warning' | 'error' | 'default'> = {
  CURRENT: 'success',
  DUE_SOON: 'warning',
  EXPIRED: 'error',
  UNFAVORABLE: 'error',
  MISSING: 'default',
};

/** Estado de un punto en el mapa de evidencias de una auditoría. */
export const evidenceMapStateLabels: Record<string, string> = {
  COVERED: 'Cubierto',
  DUE_SOON: 'Por renovar',
  ALERT: 'Revisar',
  SUGGESTED: 'Por confirmar',
  ATTACHMENT: 'Solo adjuntos',
  MISSING: 'Falta evidencia',
};

export const evidenceMapStateColors: Record<string, 'success' | 'warning' | 'error' | 'info' | 'default'> = {
  COVERED: 'success',
  DUE_SOON: 'warning',
  ALERT: 'error',
  SUGGESTED: 'info',
  ATTACHMENT: 'default',
  MISSING: 'default',
};

export const evidenceOutcomeLabels: Record<string, string> = {
  FAVORABLE: 'Favorable',
  UNFAVORABLE: 'Desfavorable',
  UNRATED: 'Sin valorar',
};
