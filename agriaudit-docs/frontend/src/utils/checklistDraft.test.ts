import { beforeEach, describe, expect, it } from 'vitest';
import { clearChecklistDraft, loadChecklistDraft, saveChecklistDraft } from './checklistDraft';

const entry = { compliance: 'NON_COMPLIANT' as const, evidenceNotes: 'Falta registro', auditorComment: '' };

describe('borrador del checklist', () => {
  beforeEach(() => sessionStorage.clear());

  it('guarda y recupera los cambios sin guardar de una auditoría', () => {
    saveChecklistDraft(7, { 12: entry });
    expect(loadChecklistDraft(7)).toEqual({ 12: entry });
  });

  it('mantiene separados los borradores de cada auditoría', () => {
    saveChecklistDraft(7, { 12: entry });
    expect(loadChecklistDraft(8)).toEqual({});
  });

  it('un borrador vacío o descartado borra lo guardado', () => {
    saveChecklistDraft(7, { 12: entry });
    saveChecklistDraft(7, {});
    expect(sessionStorage.getItem('agriaudit.checklistDraft.7')).toBeNull();

    saveChecklistDraft(7, { 12: entry });
    clearChecklistDraft(7);
    expect(loadChecklistDraft(7)).toEqual({});
  });

  it('ignora un contenido corrupto sin romper la pantalla', () => {
    sessionStorage.setItem('agriaudit.checklistDraft.7', '{no es json');
    expect(loadChecklistDraft(7)).toEqual({});
  });
});
