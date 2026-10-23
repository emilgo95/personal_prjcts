import type { ComplianceValue } from '../api/types';

export interface DraftEntry {
  compliance: ComplianceValue;
  evidenceNotes: string;
  auditorComment: string;
}

export type ChecklistDraft = Record<number, DraftEntry>;

const KEY_PREFIX = 'agriaudit.checklistDraft.';

const keyOf = (auditId: number) => KEY_PREFIX + auditId;

/**
 * Borrador del checklist de una auditoría (solo los puntos editados y sin guardar). Vive en
 * `sessionStorage`: sobrevive a un cambio de pantalla o a recargar la página, pero no al cerrar la pestaña
 * del navegador ni se comparte entre pestañas.
 */
export function loadChecklistDraft(auditId: number): ChecklistDraft {
  try {
    const raw = sessionStorage.getItem(keyOf(auditId));
    return raw ? (JSON.parse(raw) as ChecklistDraft) : {};
  } catch {
    return {};
  }
}

export function saveChecklistDraft(auditId: number, draft: ChecklistDraft): void {
  try {
    if (Object.keys(draft).length === 0) {
      sessionStorage.removeItem(keyOf(auditId));
    } else {
      sessionStorage.setItem(keyOf(auditId), JSON.stringify(draft));
    }
  } catch {
    // almacenamiento no disponible o lleno: el borrador sigue en memoria
  }
}

export function clearChecklistDraft(auditId: number): void {
  saveChecklistDraft(auditId, {});
}
