import { describe, expect, it } from 'vitest';
import { daysUntil, deadlineInfo, formatDate } from './dates';

const today = new Date(2026, 8, 19, 15, 30); // 19/09/2026, a media tarde

describe('fechas', () => {
  it('formatea una fecha ISO en castellano', () => {
    expect(formatDate('2026-07-13')).toBe('13/07/2026');
    expect(formatDate(null)).toBe('—');
  });

  it('cuenta días naturales sin depender de la hora', () => {
    expect(daysUntil('2026-09-19', today)).toBe(0);
    expect(daysUntil('2026-09-22', today)).toBe(3);
    expect(daysUntil('2026-09-15', today)).toBe(-4);
  });
});

describe('plazo de una no conformidad', () => {
  it('marca como vencida la que ha pasado su fecha', () => {
    const d = deadlineInfo('2026-09-15', false, today);
    expect(d.tone).toBe('overdue');
    expect(d.label).toBe('Vencida hace 4 días');
  });

  it('usa singular y textos especiales para hoy y mañana', () => {
    expect(deadlineInfo('2026-09-18', false, today).label).toBe('Vencida hace 1 día');
    expect(deadlineInfo('2026-09-19', false, today).label).toBe('Vence hoy');
    expect(deadlineInfo('2026-09-20', false, today).label).toBe('Vence mañana');
  });

  it('avisa a 2, 7 y 14 días del vencimiento', () => {
    expect(deadlineInfo('2026-09-19', false, today).tone).toBe('urgent');
    expect(deadlineInfo('2026-09-21', false, today)).toMatchObject({ tone: 'urgent', label: 'Vence en 2 días' });
    expect(deadlineInfo('2026-09-22', false, today).tone).toBe('soon');
    expect(deadlineInfo('2026-09-26', false, today)).toMatchObject({ tone: 'soon', label: 'Vence en 7 días' });
    expect(deadlineInfo('2026-09-27', false, today).tone).toBe('watch');
    expect(deadlineInfo('2026-10-03', false, today).tone).toBe('watch');
    expect(deadlineInfo('2026-10-04', false, today).tone).toBe('ok');
  });

  it('no alarma si la no conformidad ya está cerrada', () => {
    const d = deadlineInfo('2026-07-13', true, today);
    expect(d.tone).toBe('done');
    expect(d.label).toBe('Límite 13/07/2026');
  });

  it('indica si se cerró en plazo o con retraso', () => {
    expect(deadlineInfo('2026-09-10', true, today, '2026-09-10T18:00:00')).toMatchObject({
      tone: 'onTime',
      label: 'Cerrada en plazo',
    });
    expect(deadlineInfo('2026-09-10', true, today, '2026-09-11T09:00:00')).toMatchObject({
      tone: 'late',
      label: 'Cerrada con 1 día de retraso',
    });
    expect(deadlineInfo('2026-09-10', true, today, '2026-09-15T09:00:00').label).toBe(
      'Cerrada con 5 días de retraso',
    );
  });
});
