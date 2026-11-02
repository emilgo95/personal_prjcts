import { describe, expect, it } from 'vitest';
import {
  complianceLabels,
  formatPct,
  label,
  levelLabels,
  overallResultLabels,
  standardShort,
} from './labels';

describe('nombres cortos y porcentajes', () => {
  it('abrevia los estándares conocidos y cae al nombre completo si no', () => {
    expect(standardShort('GLOBALGAP_IFA_V6')).toBe('GLOBALG.A.P. IFA v6');
    expect(standardShort('SMETA_6_1', 'Sedex Members Ethical Trade Audit')).toBe('SMETA 6.1');
    expect(standardShort('OTRO', 'Otro estándar')).toBe('Otro estándar');
    expect(standardShort(null)).toBe('—');
  });

  it('formatea porcentajes con coma decimal', () => {
    expect(formatPct(90)).toBe('90,0 %');
    expect(formatPct(66.666)).toBe('66,7 %');
  });
});

describe('label helper', () => {
  it('traduce claves conocidas', () => {
    expect(label(complianceLabels, 'COMPLIANT')).toBe('Conforme');
    expect(label(levelLabels, 'MAJOR_MUST')).toBe('Obligación mayor');
    expect(label(overallResultLabels, 'PASS')).toBe('APTO');
  });

  it('devuelve un guion para valores vacíos', () => {
    expect(label(complianceLabels, null)).toBe('—');
    expect(label(complianceLabels, undefined)).toBe('—');
  });

  it('devuelve la clave original si no hay traducción', () => {
    expect(label(complianceLabels, 'DESCONOCIDO')).toBe('DESCONOCIDO');
  });
});
