/** Fecha de calendario (sin hora) de una cadena ISO `yyyy-MM-dd`, en hora local. */
function parseDay(iso: string): Date {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(y, m - 1, d);
}

function startOfDay(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

/** `2026-07-13` → `13/07/2026`; vacío → `—`. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = parseDay(iso);
  return d.toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

/** Instante ISO → `13/07/2026, 18:44`. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('es-ES', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Días naturales desde `today` hasta la fecha: negativo si ya pasó, 0 si es hoy. */
export function daysUntil(iso: string, today: Date = new Date()): number {
  const ms = parseDay(iso).getTime() - startOfDay(today).getTime();
  return Math.round(ms / 86_400_000);
}

export type DeadlineTone = 'overdue' | 'soon' | 'ok' | 'done';

export interface DeadlineInfo {
  tone: DeadlineTone;
  label: string;
  days: number;
}

/** Días a partir de los cuales una fecha límite deja de considerarse «a punto de vencer». */
export const DUE_SOON_DAYS = 7;

function plural(n: number, one: string, many: string): string {
  return n === 1 ? one : many;
}

/**
 * Estado de una fecha límite: vencida, a punto de vencer (≤ 7 días) o con margen. Si la NC ya está cerrada
 * o verificada (`closed`) solo se muestra la fecha, sin alarma.
 */
export function deadlineInfo(dueDate: string, closed = false, today: Date = new Date()): DeadlineInfo {
  const days = daysUntil(dueDate, today);
  if (closed) return { tone: 'done', label: `Límite ${formatDate(dueDate)}`, days };
  if (days < 0) {
    const n = -days;
    return { tone: 'overdue', label: `Vencida hace ${n} ${plural(n, 'día', 'días')}`, days };
  }
  if (days === 0) return { tone: 'soon', label: 'Vence hoy', days };
  if (days === 1) return { tone: 'soon', label: 'Vence mañana', days };
  if (days <= DUE_SOON_DAYS) return { tone: 'soon', label: `Vence en ${days} días`, days };
  return { tone: 'ok', label: `Vence en ${days} días`, days };
}
