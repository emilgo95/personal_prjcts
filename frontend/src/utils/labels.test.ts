import { describe, expect, it } from 'vitest';
import {
  complianceLabels,
  label,
  levelLabels,
  overallResultLabels,
} from './labels';

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
